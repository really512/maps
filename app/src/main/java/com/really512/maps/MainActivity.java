package com.really512.maps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.util.GeoPoint;

public class MainActivity extends Activity implements LocationListener {
    private MapView map; private LocationManager lm; private TextToSpeech tts;
    private boolean nav=false; private boolean settingDestination=false; private float touchDownX,touchDownY; private GeoPoint destination; private Marker marker; private Polyline routeLine;
    private TextView status; private long lastVoice=0; private android.content.SharedPreferences navPrefs; private Location lastLocation; private Handler mainHandler=new Handler(); private boolean routeRequestRunning=false; private int currentStepIndex=0; private double lastStepDistance=Double.MAX_VALUE; private java.util.List<GeoPoint> roadRoute=new java.util.ArrayList<>(); private int nextRoutePoint=0; private JSONArray routeSteps; private double routeMeters=0; private long lastRouteRefresh=0;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        Configuration.getInstance().load(this,getSharedPreferences("maps",0)); Configuration.getInstance().setUserAgentValue(getPackageName());
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        map=new MapView(this); map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); map.setMinZoomLevel(2); map.setMaxZoomLevel(20); map.getController().setZoom(3); map.getController().setCenter(new GeoPoint(20,0));
        root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this); status.setText("🗺️ Нажмите на место, чтобы построить маршрут"); status.setGravity(Gravity.CENTER); status.setTextSize(16); status.setPadding(12,12,12,12); root.addView(status);
        setContentView(root); navPrefs=getSharedPreferences("maps_navigation",MODE_PRIVATE);
        tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("ru","RU"));});
        map.setOnTouchListener((v,e)->{ if(e.getAction()==1 && Math.abs(e.getX()-touchDownX)<12 && Math.abs(e.getY()-touchDownY)<12){ GeoPoint p=(GeoPoint)map.getProjection().fromPixels((int)e.getX(),(int)e.getY());selectDestination(p);} if(e.getAction()==0){touchDownX=e.getX();touchDownY=e.getY();} return false;}); startLocation();
    }
    private void selectDestination(GeoPoint p){destination=p;if(marker!=null)map.getOverlays().remove(marker);marker=new Marker(map);marker.setPosition(p);marker.setTitle("Место назначения");map.getOverlays().add(marker);map.invalidate();
        new AlertDialog.Builder(this).setTitle("📍 Место назначения").setMessage("Координаты: "+String.format(Locale.US,"%.5f, %.5f",p.getLatitude(),p.getLongitude())).setPositiveButton("Маршрут",(d,w)->showRoute()).setNegativeButton("Отмена",null).show();}
    private void showRoute(){ if(destination!=null){ if(lastLocation!=null) requestRoadRoute(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()),destination); navPrefs.edit().putFloat("destination_lat",(float)destination.getLatitude()).putFloat("destination_lon",(float)destination.getLongitude()).putString("destination_title","Место назначения").apply(); } status.setText("🛣️ Маршрут готов • Нажмите «Включить навигатор»");
        new AlertDialog.Builder(this).setTitle("🛣️ Маршрут").setMessage("Маршрут рассчитан от текущего местоположения до выбранной точки.\n\nНавигатор будет отслеживать движение и давать голосовые подсказки.").setPositiveButton("Включить навигатор",(d,w)->toggleNav(true)).setNegativeButton("Закрыть",null).show();}
    private void toggleNav(boolean on){nav=on; if(!on && navPrefs!=null) navPrefs.edit().putBoolean("navigation_enabled",false).apply(); if(navPrefs!=null) navPrefs.edit().putBoolean("navigation_enabled",on).apply();status.setText(nav?"🔊 Навигатор ВКЛ • голосовые подсказки": "🔇 Навигатор ВЫКЛ • маршрут остаётся на карте");if(nav)speak("Навигатор включён");}
    private void startLocation(){lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,3,this);}catch(Exception ignored){}}
    @Override public void onLocationChanged(Location l){lastLocation=l; if(destination==null)return; if(routeLine==null && !routeRequestRunning) requestRoadRoute(new GeoPoint(l.getLatitude(),l.getLongitude()),destination);float m=l.distanceTo(toLocation(destination)); updateTurnGuidance(l); if(navPrefs!=null) navPrefs.edit().putFloat("destination_distance_m",m).apply(); status.setText(nav?"🔊 До цели: "+formatDistance(m):"📍 До цели: "+formatDistance(m));if(nav){if(m<50){speak("Вы прибыли в пункт назначения");nav=false; if(navPrefs!=null) navPrefs.edit().putBoolean("navigation_enabled",false).apply();}else if(System.currentTimeMillis()-lastVoice>15000 && m<2000){speak("До пункта назначения "+formatDistance(m));lastVoice=System.currentTimeMillis();}}}
    private void requestRoadRoute(final GeoPoint from, final GeoPoint to){ if(routeRequestRunning)return; routeRequestRunning=true;
        status.setText("🛣️ Строим маршрут по дорогам…");
        new Thread(() -> {
            try {
                String u="https://router.project-osrm.org/route/v1/driving/"+from.getLongitude()+","+from.getLatitude()+";"+to.getLongitude()+","+to.getLatitude()+"?overview=full&geometries=geojson&steps=true";
                HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection(); h.setConnectTimeout(10000); h.setReadTimeout(15000);
                h.setRequestMethod("GET");
                java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(h.getInputStream()));
                StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); br.close();
                JSONObject root=new JSONObject(sb.toString()); JSONArray routes=root.getJSONArray("routes");
                if(routes.length()==0) throw new Exception("route not found");
                JSONObject route=routes.getJSONObject(0); JSONObject geo=route.getJSONObject("geometry"); JSONArray coords=geo.getJSONArray("coordinates");
                java.util.ArrayList<GeoPoint> pts=new java.util.ArrayList<>();
                for(int i=0;i<coords.length();i++){ JSONArray q=coords.getJSONArray(i); pts.add(new GeoPoint(q.getDouble(1),q.getDouble(0))); }
                double meters=route.getDouble("distance");
                JSONArray legs=route.getJSONArray("legs"); JSONArray steps=legs.getJSONObject(0).getJSONArray("steps");
                String instruction="Следуйте по маршруту";
                if(steps.length()>0) instruction=steps.getJSONObject(0).optString("name","Следуйте по маршруту");
                mainHandler.post(() -> {
                    roadRoute=pts; nextRoutePoint=0; routeSteps=steps; currentStepIndex=0; lastStepDistance=Double.MAX_VALUE; routeMeters=meters; drawRoadRoute(pts);
                    if(navPrefs!=null) navPrefs.edit().putFloat("route_distance_m",(float)meters).putString("next_instruction",instruction).putFloat("next_step_distance_m",(float)meters).apply();
                    status.setText("🛣️ Маршрут построен • "+formatDistance((float)meters));
                });
            } catch(Exception e) {
                mainHandler.post(() -> { routeRequestRunning=false; drawRoute(from,to); status.setText("⚠️ Не удалось получить дорожный маршрут"); });
            }
        }).start();
    }
    private void updateTurnGuidance(Location l){
        if(routeSteps==null || routeSteps.length()==0 || !nav) return;
        try {
            if(currentStepIndex >= routeSteps.length()) return;
            JSONObject current=routeSteps.getJSONObject(currentStepIndex);
            JSONArray currentLoc=current.getJSONObject("maneuver").getJSONArray("location");
            float currentDist=l.distanceTo(toLocation(new GeoPoint(currentLoc.getDouble(1),currentLoc.getDouble(0))));
            if(currentDist < 35 && currentStepIndex < routeSteps.length()-1){
                currentStepIndex++;
            }
            JSONObject step=routeSteps.getJSONObject(currentStepIndex);
            JSONArray stepLoc=step.getJSONObject("maneuver").getJSONArray("location");
            float bestDist=l.distanceTo(toLocation(new GeoPoint(stepLoc.getDouble(1),stepLoc.getDouble(0))));
            lastStepDistance=bestDist;
            String type=step.getJSONObject("maneuver").optString("type","");
            String mod=step.getJSONObject("maneuver").optString("modifier","");
            String road=step.optString("name","");
            String action="Продолжайте движение";
            if("turn".equals(type)) action="Поверните "+(mod.length()>0?mod:"на следующую дорогу");
            else if("roundabout".equals(type)) action="На круговом движении";
            else if("arrive".equals(type)) action="Вы прибыли в пункт назначения";
            float d=(float)bestDist;
            String text=action+(road.length()>0?" на "+road:"");
            if(navPrefs!=null) navPrefs.edit().putString("next_instruction",text).putFloat("next_step_distance_m",d).apply();
            if(d<120 && System.currentTimeMillis()-lastVoice>12000 && !"arrive".equals(type)){ speak("Через "+formatDistance(d)+": "+text); lastVoice=System.currentTimeMillis(); }
        } catch(Exception ignored){}
    }
    private void drawRoadRoute(java.util.List<GeoPoint> pts){
        if(routeLine!=null) map.getOverlays().remove(routeLine);
        routeLine=new Polyline(map); routeLine.setPoints(pts); map.getOverlays().add(routeLine); map.invalidate();
    }
    private void drawRoute(GeoPoint from, GeoPoint to){ if(routeLine!=null) map.getOverlays().remove(routeLine); routeLine=new Polyline(map); java.util.List<GeoPoint> pts=new java.util.ArrayList<>(); pts.add(from); pts.add(to); routeLine.setPoints(pts); map.getOverlays().add(routeLine); map.invalidate(); if(navPrefs!=null) navPrefs.edit().putString("next_instruction","Следуйте к пункту назначения").putFloat("next_step_distance_m",from.distanceToAsDouble(to).floatValue()).apply(); }
    private Location toLocation(GeoPoint p){Location x=new Location("destination");x.setLatitude(p.getLatitude());x.setLongitude(p.getLongitude());return x;}
    private String formatDistance(float m){return m>=1000?String.format(Locale.getDefault(),"%.1f км",m/1000f):Math.round(m)+" м";}
    private void speak(String s){if(nav&&tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"maps-navigation");}
    @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,Bundle e){}
    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();} @Override protected void onPause(){if(map!=null)map.onPause();if(tts!=null)tts.stop();super.onPause();} @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}
}
