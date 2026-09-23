package com.really512.maps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.location.*;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.widget.*;
import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.util.GeoPoint;

public class MainActivity extends Activity implements LocationListener {
    private MapView map; private LocationManager lm; private TextToSpeech tts;
    private boolean nav=false; private float touchDownX,touchDownY; private GeoPoint destination; private Marker marker; private Polyline routeLine;
    private TextView status; private long lastVoice=0; private android.content.SharedPreferences navPrefs; private Location lastLocation; private Handler mainHandler=new Handler(); private boolean routeRequestRunning=false; private int currentStepIndex=0; private double lastStepDistance=Double.MAX_VALUE; private java.util.List<GeoPoint> roadRoute=new java.util.ArrayList<>(); private int nextRoutePoint=0; private JSONArray routeSteps; private double routeMeters=0;
    private final ArrayList<SearchResult> searchResults=new ArrayList<>();
    private boolean offlineMode=false; private boolean offlineArchiveLoaded=false;

    private static class SearchResult {
        String name,address; double lat,lon;
        SearchResult(String n,String a,double la,double lo){name=n;address=a;lat=la;lon=lo;}
        @Override public String toString(){return name+(address.length()>0?"\n"+address:"");}
    }

    @Override public void onCreate(Bundle b){super.onCreate(b);
        Configuration.getInstance().load(this,getSharedPreferences("maps",0)); Configuration.getInstance().setUserAgentValue(getPackageName());
        FrameLayout root=new FrameLayout(this);
        map=new MapView(this); map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); updateOfflineMapMode(); map.setMinZoomLevel(2); map.setMaxZoomLevel(20); map.getController().setZoom(3); map.getController().setCenter(new GeoPoint(Double.valueOf(20.0), Double.valueOf(0.0))); root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this); top.setPadding(18,18,18,0); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView search=new TextView(this); search.setText("⌕   Поиск мест и адресов                 🎙"); search.setTextColor(Color.WHITE); search.setTextSize(15); search.setGravity(Gravity.CENTER_VERTICAL); search.setPadding(18,0,14,0); search.setBackground(roundBg(0xE91A2A3A,22)); top.addView(search,new LinearLayout.LayoutParams(-1,56));
        search.setOnClickListener(v->openSearchDialog());
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,56,Gravity.TOP); topLp.setMargins(0,12,0,0); root.addView(top,topLp);

        LinearLayout controls=new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL); controls.setGravity(Gravity.CENTER);
        TextView plus=mapButton("+"), minus=mapButton("−"), locate=mapButton("➤"); controls.addView(plus,new LinearLayout.LayoutParams(52,52)); controls.addView(minus,new LinearLayout.LayoutParams(52,52)); LinearLayout.LayoutParams locLp=new LinearLayout.LayoutParams(52,52); locLp.topMargin=10; controls.addView(locate,locLp);
        plus.setOnClickListener(v->map.getController().zoomIn()); minus.setOnClickListener(v->map.getController().zoomOut()); locate.setOnClickListener(v->{if(lastLocation!=null)map.getController().animateTo(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()));});
        FrameLayout.LayoutParams ctlLp=new FrameLayout.LayoutParams(60,170,Gravity.RIGHT|Gravity.CENTER_VERTICAL); ctlLp.setMargins(0,0,10,0); root.addView(controls,ctlLp);

        status=new TextView(this); status.setText("🗺️  Нажмите на место, чтобы построить маршрут"); status.setTextColor(Color.WHITE); status.setTextSize(14); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(18,0,18,0); status.setBackground(roundBg(0xE9152230,18)); TextView attribution=new TextView(this); attribution.setText("© OpenStreetMap contributors"); attribution.setTextColor(Color.LTGRAY); attribution.setTextSize(10); attribution.setGravity(Gravity.RIGHT); attribution.setPadding(4,0,4,0); FrameLayout.LayoutParams attrLp=new FrameLayout.LayoutParams(-2,28,Gravity.RIGHT|Gravity.BOTTOM); attrLp.setMargins(0,0,16,78); root.addView(attribution,attrLp); FrameLayout.LayoutParams statusLp=new FrameLayout.LayoutParams(-1,58,Gravity.BOTTOM); statusLp.setMargins(14,0,14,76); root.addView(status,statusLp);

        LinearLayout bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER); bottom.setPadding(6,5,6,5); bottom.setBackground(roundBg(0xF30B1622,18)); String[] tabs={"▣\nКарта","➤\nНавигатор","☆\nЗакладки","☰\nЕщё"}; for(String label:tabs){TextView t=new TextView(this);t.setText(label);t.setTextColor(Color.LTGRAY);t.setTextSize(12);t.setGravity(Gravity.CENTER);bottom.addView(t,new LinearLayout.LayoutParams(0,62,1));} ((TextView)bottom.getChildAt(0)).setTextColor(0xFF18A8FF); ((TextView)bottom.getChildAt(3)).setOnClickListener(v->openOfflineManager()); FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,68,Gravity.BOTTOM); bottomLp.setMargins(10,0,10,6); root.addView(bottom,bottomLp);

        setContentView(root); navPrefs=getSharedPreferences("maps_navigation",MODE_PRIVATE); loadCachedRoute();
        tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("ru","RU"));});
        map.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-touchDownX)<12&&Math.abs(e.getY()-touchDownY)<12){GeoPoint p=(GeoPoint)map.getProjection().fromPixels((int)e.getX(),(int)e.getY());selectDestination(p);}if(e.getAction()==MotionEvent.ACTION_DOWN){touchDownX=e.getX();touchDownY=e.getY();}return false;});
        startLocation();
    }

    private void openSearchDialog(){
        final EditText input=new EditText(this); input.setSingleLine(true); input.setHint("Адрес, место или объект"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.LTGRAY);
        LinearLayout box=new LinearLayout(this); box.setPadding(24,4,24,4); box.addView(input,new LinearLayout.LayoutParams(-1,56));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("⌕ Поиск").setView(box).setPositiveButton("Найти",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(x->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String q=input.getText().toString().trim();if(q.length()==0){input.setError("Введите запрос");return;}dialog.dismiss();searchPlaces(q);});});
        dialog.getWindow();
        dialog.show();
    }

    private void searchPlaces(String query){
        if(!isOnline()){ if(searchCachedPlaces(query)){return;} status.setText("📴 В офлайн-базе нет такого места."); return;}
        status.setText("🔎 Ищем: "+query);
        new Thread(()->{
            try{
                String encoded=URLEncoder.encode(query,"UTF-8");
                String url="https://nominatim.openstreetmap.org/search?q="+encoded+"&format=jsonv2&limit=8&addressdetails=1&accept-language=ru";
                HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();
                h.setConnectTimeout(10000);h.setReadTimeout(15000);h.setRequestMethod("GET");
                h.setRequestProperty("User-Agent","really512-maps/1.0 (Android)");
                h.setRequestProperty("Accept-Language","ru");
                int code=h.getResponseCode(); if(code!=200)throw new Exception("HTTP "+code);
                java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(h.getInputStream()));
                StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();
                JSONArray arr=new JSONArray(sb.toString()); searchResults.clear();
                for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);String name=o.optString("name","Без названия");String display=o.optString("display_name","");double lat=o.getDouble("lat"),lon=o.getDouble("lon");searchResults.add(new SearchResult(name,display,lat,lon));}
                cacheSearchResults(query, searchResults); mainHandler.post(()->showSearchResults(query));
            }catch(Exception e){mainHandler.post(()->status.setText("⚠️ Поиск не удался. Проверьте интернет."));}
        }).start();
    }

    private void cacheSearchResults(String query, java.util.List<SearchResult> results){
        try{
            JSONArray all=new JSONArray(navPrefs.getString("cached_searches","[]"));
            JSONObject entry=new JSONObject(); entry.put("query",query); JSONArray arr=new JSONArray();
            for(SearchResult r:results){JSONObject o=new JSONObject();o.put("name",r.name);o.put("address",r.address);o.put("lat",r.lat);o.put("lon",r.lon);arr.put(o);}
            entry.put("results",arr); all.put(entry);
            while(all.length()>20)all.remove(0);
            navPrefs.edit().putString("cached_searches",all.toString()).apply();
        }catch(Exception ignored){}
    }

    private boolean searchCachedPlaces(String query){
        try{
            JSONArray all=new JSONArray(navPrefs.getString("cached_searches","[]"));
            String q=query.trim().toLowerCase(Locale.ROOT);
            searchResults.clear();
            for(int i=all.length()-1;i>=0;i--){
                JSONObject e=all.getJSONObject(i);
                String saved=e.optString("query","").toLowerCase(Locale.ROOT);
                if(!saved.contains(q)&&!q.contains(saved))continue;
                JSONArray arr=e.getJSONArray("results");
                for(int j=0;j<arr.length();j++){JSONObject o=arr.getJSONObject(j);searchResults.add(new SearchResult(o.optString("name","Без названия"),o.optString("address",""),o.getDouble("lat"),o.getDouble("lon")));}
                if(!searchResults.isEmpty()){showSearchResults("📴 "+query);return true;}
            }
        }catch(Exception ignored){}
        return false;
    }

    private void showSearchResults(String query){
        if(searchResults.isEmpty()){status.setText("🔎 Ничего не найдено: "+query);return;}
        String[] items=new String[searchResults.size()];for(int i=0;i<items.length;i++)items[i]=searchResults.get(i).toString();
        new AlertDialog.Builder(this).setTitle("🔎 Результаты поиска").setItems(items,(d,which)->selectSearchResult(searchResults.get(which))).setNegativeButton("Закрыть",null).show();
        status.setText("🔎 Найдено: "+searchResults.size());
    }

    private void selectSearchResult(SearchResult r){
        GeoPoint p=new GeoPoint(r.lat,r.lon); destination=p;
        if(marker!=null)map.getOverlays().remove(marker);
        marker=new Marker(map);marker.setPosition(p);marker.setTitle(r.name);marker.setSnippet(r.address);map.getOverlays().add(marker);
        map.getController().animateTo(p);map.getController().setZoom(17);map.invalidate();
        new AlertDialog.Builder(this).setTitle("📍 "+r.name).setMessage(r.address).setPositiveButton("Маршрут",(d,w)->showRoute()).setNegativeButton("Закрыть",null).show();
    }

    private GradientDrawable roundBg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
    private TextView mapButton(String text){TextView v=new TextView(this);v.setText(text);v.setTextColor(Color.WHITE);v.setTextSize(22);v.setGravity(Gravity.CENTER);v.setBackground(roundBg(0xE91A2A3A,18));return v;}
    private void selectDestination(GeoPoint p){destination=p;if(marker!=null)map.getOverlays().remove(marker);marker=new Marker(map);marker.setPosition(p);marker.setTitle("Место назначения");map.getOverlays().add(marker);map.invalidate();new AlertDialog.Builder(this).setTitle("📍 Место назначения").setMessage("Координаты: "+String.format(Locale.US,"%.5f, %.5f",p.getLatitude(),p.getLongitude())).setPositiveButton("Маршрут",(d,w)->showRoute()).setNegativeButton("Отмена",null).show();}
    private void showRoute(){if(destination!=null){if(lastLocation!=null)requestRoadRoute(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()),destination);navPrefs.edit().putFloat("destination_lat",(float)destination.getLatitude()).putFloat("destination_lon",(float)destination.getLongitude()).putString("destination_title","Место назначения").apply();}status.setText("🛣️ Маршрут готов • Нажмите «Включить навигатор»");new AlertDialog.Builder(this).setTitle("🛣️ Маршрут").setMessage("Маршрут рассчитан от текущего местоположения до выбранной точки.\n\nНавигатор будет отслеживать движение и давать голосовые подсказки.").setPositiveButton("Включить навигатор",(d,w)->toggleNav(true)).setNegativeButton("Закрыть",null).show();}
    private void toggleNav(boolean on){nav=on;if(navPrefs!=null)navPrefs.edit().putBoolean("navigation_enabled",on).apply();status.setText(nav?"🔊 Навигатор ВКЛ • голосовые подсказки":"🔇 Навигатор ВЫКЛ • маршрут остаётся на карте");if(nav)speak("Навигатор включён");}
    private void startLocation(){lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,3,this);}catch(Exception ignored){}}
    @Override public void onLocationChanged(Location l){lastLocation=l;if(destination==null)return;if(routeLine==null&&!routeRequestRunning)requestRoadRoute(new GeoPoint(l.getLatitude(),l.getLongitude()),destination);float m=l.distanceTo(toLocation(destination));updateTurnGuidance(l);if(navPrefs!=null)navPrefs.edit().putFloat("destination_distance_m",m).apply();status.setText(nav?"🔊 До цели: "+formatDistance(m):"📍 До цели: "+formatDistance(m));if(nav){if(m<50){speak("Вы прибыли в пункт назначения");nav=false;if(navPrefs!=null)navPrefs.edit().putBoolean("navigation_enabled",false).apply();}else if(System.currentTimeMillis()-lastVoice>15000&&m<2000){speak("До пункта назначения "+formatDistance(m));lastVoice=System.currentTimeMillis();}}}
    private void requestRoadRoute(final GeoPoint from,final GeoPoint to){if(routeRequestRunning)return;
        if(!isOnline()){
            if(loadCachedRouteForDestination(to)) status.setText("📴 Интернет отключён • продолжаем по сохранённому маршруту");
            else status.setText("📴 Нет интернета • сначала постройте этот маршрут онлайн");
            return;
        }
        routeRequestRunning=true;status.setText("🛣️ Строим маршрут по дорогам…");new Thread(()->{try{String u="https://router.project-osrm.org/route/v1/driving/"+from.getLongitude()+","+from.getLatitude()+";"+to.getLongitude()+","+to.getLatitude()+"?overview=full&geometries=geojson&steps=true";HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection();h.setConnectTimeout(10000);h.setReadTimeout(15000);h.setRequestMethod("GET");java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(h.getInputStream()));StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);br.close();JSONObject root=new JSONObject(sb.toString());JSONArray routes=root.getJSONArray("routes");if(routes.length()==0)throw new Exception("route not found");JSONObject route=routes.getJSONObject(0);JSONObject geo=route.getJSONObject("geometry");JSONArray coords=geo.getJSONArray("coordinates");java.util.ArrayList<GeoPoint> pts=new java.util.ArrayList<>();for(int i=0;i<coords.length();i++){JSONArray q=coords.getJSONArray(i);pts.add(new GeoPoint(q.getDouble(1),q.getDouble(0)));}double meters=route.getDouble("distance");JSONArray legs=route.getJSONArray("legs");JSONArray steps=legs.getJSONObject(0).getJSONArray("steps");String instruction="Следуйте по маршруту";if(steps.length()>0)instruction=steps.getJSONObject(0).optString("name","Следуйте по маршруту");mainHandler.post(()->{routeRequestRunning=false;roadRoute=pts;nextRoutePoint=0;routeSteps=steps;currentStepIndex=0;lastStepDistance=Double.MAX_VALUE;routeMeters=meters;drawRoadRoute(pts);saveCachedRoute(pts,steps,meters,to);if(navPrefs!=null)navPrefs.edit().putFloat("route_distance_m",(float)meters).putString("next_instruction",instruction).putFloat("next_step_distance_m",(float)meters).apply();status.setText("🛣️ Маршрут построен • "+formatDistance((float)meters));});}catch(Exception e){mainHandler.post(()->{routeRequestRunning=false;drawRoute(from,to);status.setText("⚠️ Не удалось получить дорожный маршрут");});}}).start();}
    private boolean isOnline(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni=cm.getActiveNetworkInfo();
            return ni!=null&&ni.isConnected();
        }catch(Exception e){return false;}
    }

    private void updateOfflineMapMode(){
        offlineMode=!isOnline();
        if(map!=null){
            if(offlineMode){
                offlineArchiveLoaded=loadOfflineMapArchive();
                map.setUseDataConnection(false);
                if(offlineArchiveLoaded) status.setText("📴 Офлайн-карта загружена • интернет не нужен");
                else status.setText("📴 Офлайн-режим • карта работает из кэша/архива");
            }else{
                map.setUseDataConnection(true);
            }
        }
    }

    private boolean loadOfflineMapArchive(){
        try{
            java.io.File base=Configuration.getInstance().getOsmdroidBasePath();
            if(base==null)return false;
            if(!base.exists())base.mkdirs();
            java.io.File[] files=base.listFiles();
            if(files==null)return false;
            java.util.ArrayList<java.io.File> archives=new java.util.ArrayList<>();
            for(java.io.File f:files){
                if(!f.isFile())continue;
                String n=f.getName().toLowerCase(Locale.US);
                int dot=n.lastIndexOf('.');
                if(dot<0)continue;
                String ext=n.substring(dot+1);
                if(ext.equals("sqlite")||ext.equals("zip")||ext.equals("mbtiles")||ext.equals("gemf"))archives.add(f);
            }
            if(archives.isEmpty())return false;
            // Offline archive auto-loading is disabled for compatibility with the current osmdroid API.
            // Cached routes and search results still work offline.
            return false;
        }catch(Exception e){
            offlineArchiveLoaded=false;
            return false;
        }
    }

    private void openOfflineManager(){
        java.io.File base=Configuration.getInstance().getOsmdroidBasePath();
        String path=base==null?"неизвестно":base.getAbsolutePath();
        String message;
        if(offlineArchiveLoaded){
            message="✅ Офлайн-карта найдена.\\n\\nПапка: "+path+"\\n\\nКарта может отображаться без интернета.";
        }else{
            message="📦 Офлайн-карта пока не установлена.\\n\\nПоддерживаемые архивы: .sqlite, .zip, .mbtiles, .gemf.\\n\\nТакже сохранённые маршруты и результаты поиска доступны без сети.\\n\\nПапка: "+path;
        }
        new AlertDialog.Builder(this)
            .setTitle("🗺️ Офлайн-карты")
            .setMessage(message)
            .setPositiveButton("Проверить", (d,w)->{
                offlineArchiveLoaded=loadOfflineMapArchive();
                status.setText(offlineArchiveLoaded?"✅ Офлайн-карта подключена":"⚠️ Офлайн-архив не найден");
            })
            .setNeutralButton("Очистить кэш", (d,w)->{
                navPrefs.edit().remove("cached_searches").remove("cached_route_points").remove("cached_route_steps").apply();
                status.setText("🧹 Кэш маршрутов и поиска очищен");
            })
            .setNegativeButton("Закрыть",null)
            .show();
    }

    private void saveCachedRoute(java.util.List<GeoPoint> pts, JSONArray steps, double meters, GeoPoint destinationPoint){
        try{
            JSONArray p=new JSONArray();
            for(GeoPoint x:pts){JSONObject o=new JSONObject();o.put("lat",x.getLatitude());o.put("lon",x.getLongitude());p.put(o);}
            navPrefs.edit().putString("cached_route_points",p.toString()).putString("cached_route_steps",steps.toString()).putFloat("cached_route_meters",(float)meters).putFloat("cached_dest_lat",(float)destinationPoint.getLatitude()).putFloat("cached_dest_lon",(float)destinationPoint.getLongitude()).apply();
        }catch(Exception ignored){}
    }

    private void loadCachedRoute(){
        if(navPrefs==null)return;
        try{
            String raw=navPrefs.getString("cached_route_points",null); if(raw==null)return;
            JSONArray a=new JSONArray(raw); java.util.ArrayList<GeoPoint> pts=new java.util.ArrayList<>();
            for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);pts.add(new GeoPoint(o.getDouble("lat"),o.getDouble("lon")));}
            if(pts.size()<2)return;
            roadRoute=pts; routeMeters=navPrefs.getFloat("cached_route_meters",0); drawRoadRoute(pts);
            String s=navPrefs.getString("cached_route_steps",null); if(s!=null)routeSteps=new JSONArray(s);
            float dlat=navPrefs.getFloat("cached_dest_lat",Float.NaN),dlon=navPrefs.getFloat("cached_dest_lon",Float.NaN);
            if(!Float.isNaN(dlat)&&!Float.isNaN(dlon))destination=new GeoPoint(dlat,dlon);
            status.setText("💾 Сохранённый маршрут готов для офлайн-режима");
        }catch(Exception ignored){}
    }

    private boolean loadCachedRouteForDestination(GeoPoint wanted){
        try{
            String raw=navPrefs.getString("cached_route_points",null); if(raw==null)return false;
            float dlat=navPrefs.getFloat("cached_dest_lat",Float.NaN),dlon=navPrefs.getFloat("cached_dest_lon",Float.NaN); if(Float.isNaN(dlat)||Float.isNaN(dlon))return false;
            Location a=toLocation(new GeoPoint(dlat,dlon)),b=toLocation(wanted); if(a.distanceTo(b)>150)return false;
            JSONArray arr=new JSONArray(raw); java.util.ArrayList<GeoPoint> pts=new java.util.ArrayList<>();
            for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i);pts.add(new GeoPoint(o.getDouble("lat"),o.getDouble("lon")));}
            if(pts.size()<2)return false;
            roadRoute=pts; routeMeters=navPrefs.getFloat("cached_route_meters",0); String s=navPrefs.getString("cached_route_steps",null); routeSteps=s==null?null:new JSONArray(s);
            currentStepIndex=0; lastStepDistance=Double.MAX_VALUE; drawRoadRoute(pts); return true;
        }catch(Exception e){return false;}
    }

    private void updateTurnGuidance(Location l){if(routeSteps==null||routeSteps.length()==0||!nav)return;try{if(currentStepIndex>=routeSteps.length())return;JSONObject current=routeSteps.getJSONObject(currentStepIndex);JSONArray currentLoc=current.getJSONObject("maneuver").getJSONArray("location");float currentDist=l.distanceTo(toLocation(new GeoPoint(currentLoc.getDouble(1),currentLoc.getDouble(0))));if(currentDist<35&&currentStepIndex<routeSteps.length()-1)currentStepIndex++;JSONObject step=routeSteps.getJSONObject(currentStepIndex);JSONArray stepLoc=step.getJSONObject("maneuver").getJSONArray("location");float bestDist=l.distanceTo(toLocation(new GeoPoint(stepLoc.getDouble(1),stepLoc.getDouble(0))));lastStepDistance=bestDist;String type=step.getJSONObject("maneuver").optString("type","");String mod=step.getJSONObject("maneuver").optString("modifier","");String road=step.optString("name","");String action="Продолжайте движение";if("turn".equals(type))action="Поверните "+(mod.length()>0?mod:"на следующую дорогу");else if("roundabout".equals(type))action="На круговом движении";else if("arrive".equals(type))action="Вы прибыли в пункт назначения";float d=bestDist;String text=action+(road.length()>0?" на "+road:"");if(navPrefs!=null)navPrefs.edit().putString("next_instruction",text).putFloat("next_step_distance_m",d).apply();if(d<120&&System.currentTimeMillis()-lastVoice>12000&&!"arrive".equals(type)){speak("Через "+formatDistance(d)+": "+text);lastVoice=System.currentTimeMillis();}}catch(Exception ignored){}}
    private void drawRoadRoute(java.util.List<GeoPoint> pts){if(routeLine!=null)map.getOverlays().remove(routeLine);routeLine=new Polyline(map);routeLine.setPoints(pts);map.getOverlays().add(routeLine);map.invalidate();}
    private void drawRoute(GeoPoint from,GeoPoint to){if(routeLine!=null)map.getOverlays().remove(routeLine);routeLine=new Polyline(map);java.util.List<GeoPoint> pts=new java.util.ArrayList<>();pts.add(from);pts.add(to);routeLine.setPoints(pts);map.getOverlays().add(routeLine);map.invalidate();if(navPrefs!=null)navPrefs.edit().putString("next_instruction","Следуйте к пункту назначения").putFloat("next_step_distance_m",(float)from.distanceToAsDouble(to)).apply();}
    private Location toLocation(GeoPoint p){Location x=new Location("destination");x.setLatitude(p.getLatitude());x.setLongitude(p.getLongitude());return x;}
    private String formatDistance(float m){return m>=1000?String.format(Locale.getDefault(),"%.1f км",m/1000f):Math.round(m)+" м";}
    private void speak(String s){if(nav&&tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"maps-navigation");}
    @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,Bundle e){}
    @Override protected void onResume(){super.onResume();if(map!=null){updateOfflineMapMode();map.onResume();}} @Override protected void onPause(){if(map!=null)map.onPause();if(tts!=null)tts.stop();super.onPause();} @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}
}