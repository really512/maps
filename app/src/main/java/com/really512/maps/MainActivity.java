package com.really512.maps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.*;
import java.util.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.util.GeoPoint;

public class MainActivity extends Activity implements LocationListener {
    private MapView map; private LocationManager lm; private TextToSpeech tts;
    private boolean nav=false; private GeoPoint destination; private Marker marker; private Polyline routeLine;
    private TextView status; private long lastVoice=0; private android.content.SharedPreferences navPrefs; private Location lastLocation;

    @Override public void onCreate(Bundle b){super.onCreate(b);
        Configuration.getInstance().load(this,getSharedPreferences("maps",0)); Configuration.getInstance().setUserAgentValue(getPackageName());
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        map=new MapView(this); map.setTileSource(TileSourceFactory.MAPNIK); map.setMultiTouchControls(true); map.setMinZoomLevel(2); map.setMaxZoomLevel(20); map.getController().setZoom(3); map.getController().setCenter(new GeoPoint(20,0));
        root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        status=new TextView(this); status.setText("🗺️ Нажмите на место, чтобы построить маршрут"); status.setGravity(Gravity.CENTER); status.setTextSize(16); status.setPadding(12,12,12,12); root.addView(status);
        setContentView(root); navPrefs=getSharedPreferences("maps_navigation",MODE_PRIVATE);
        tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("ru","RU"));});
        map.setOnTouchListener((v,e)->{if(e.getAction()==1){GeoPoint p=(GeoPoint)map.getProjection().fromPixels((int)e.getX(),(int)e.getY());selectDestination(p);}return false;}); startLocation();
    }
    private void selectDestination(GeoPoint p){destination=p;if(marker!=null)map.getOverlays().remove(marker);marker=new Marker(map);marker.setPosition(p);marker.setTitle("Место назначения");map.getOverlays().add(marker);map.invalidate();
        new AlertDialog.Builder(this).setTitle("📍 Место назначения").setMessage("Координаты: "+String.format(Locale.US,"%.5f, %.5f",p.getLatitude(),p.getLongitude())).setPositiveButton("Маршрут",(d,w)->showRoute()).setNegativeButton("Отмена",null).show();}
    private void showRoute(){ if(destination!=null){ if(lastLocation!=null) drawRoute(new GeoPoint(lastLocation.getLatitude(),lastLocation.getLongitude()),destination); navPrefs.edit().putFloat("destination_lat",(float)destination.getLatitude()).putFloat("destination_lon",(float)destination.getLongitude()).putString("destination_title","Место назначения").apply(); } status.setText("🛣️ Маршрут готов • Нажмите «Включить навигатор»");
        new AlertDialog.Builder(this).setTitle("🛣️ Маршрут").setMessage("Маршрут рассчитан от текущего местоположения до выбранной точки.\n\nНавигатор будет отслеживать движение и давать голосовые подсказки.").setPositiveButton("Включить навигатор",(d,w)->toggleNav(true)).setNegativeButton("Закрыть",null).show();}
    private void toggleNav(boolean on){nav=on; if(navPrefs!=null) navPrefs.edit().putBoolean("navigation_enabled",on).apply();status.setText(nav?"🔊 Навигатор ВКЛ • голосовые подсказки": "🔇 Навигатор ВЫКЛ • маршрут остаётся на карте");if(nav)speak("Навигатор включён");}
    private void startLocation(){lm=(LocationManager)getSystemService(LOCATION_SERVICE);if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},10);return;}try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,3,this);}catch(Exception ignored){}}
    @Override public void onLocationChanged(Location l){lastLocation=l; if(destination==null)return; if(routeLine==null) drawRoute(new GeoPoint(l.getLatitude(),l.getLongitude()),destination);float m=l.distanceTo(toLocation(destination)); if(navPrefs!=null) navPrefs.edit().putFloat("destination_distance_m",m).apply(); status.setText(nav?"🔊 До цели: "+formatDistance(m):"📍 До цели: "+formatDistance(m));if(nav){if(m<50){speak("Вы прибыли в пункт назначения");nav=false;}else if(System.currentTimeMillis()-lastVoice>15000 && m<2000){speak("До пункта назначения "+formatDistance(m));lastVoice=System.currentTimeMillis();}}}
    private void drawRoute(GeoPoint from, GeoPoint to){ if(routeLine!=null) map.getOverlays().remove(routeLine); routeLine=new Polyline(map); java.util.List<GeoPoint> pts=new java.util.ArrayList<>(); pts.add(from); pts.add(to); routeLine.setPoints(pts); map.getOverlays().add(routeLine); map.invalidate(); if(navPrefs!=null) navPrefs.edit().putString("next_instruction","Следуйте к пункту назначения").putFloat("next_step_distance_m",from.distanceToAsDouble(to).floatValue()).apply(); }
    private Location toLocation(GeoPoint p){Location x=new Location("destination");x.setLatitude(p.getLatitude());x.setLongitude(p.getLongitude());return x;}
    private String formatDistance(float m){return m>=1000?String.format(Locale.getDefault(),"%.1f км",m/1000f):Math.round(m)+" м";}
    private void speak(String s){if(nav&&tts!=null)tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"maps-navigation");}
    @Override public void onProviderEnabled(String p){} @Override public void onProviderDisabled(String p){} @Override public void onStatusChanged(String p,int s,Bundle e){}
    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();} @Override protected void onPause(){if(map!=null)map.onPause();if(tts!=null)tts.stop();super.onPause();} @Override protected void onDestroy(){if(tts!=null)tts.shutdown();super.onDestroy();}
}
