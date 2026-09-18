package com.really512.maps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.util.GeoPoint;

public class MainActivity extends Activity implements LocationListener {
    private MapView map;
    private LocationManager locationManager;
    private TextToSpeech tts;
    private boolean navigatorEnabled = false;
    private GeoPoint destination;
    private Marker destinationMarker;
    private TextView navStatus;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        map = new MapView(this);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.setMinZoomLevel(2.0);
        map.setMaxZoomLevel(20.0);
        map.getController().setZoom(2.5);
        map.getController().setCenter(new GeoPoint(20.0, 0.0));
        root.addView(map, new LinearLayout.LayoutParams(-1, 0, 1));

        navStatus = new TextView(this);
        navStatus.setText("🧭 Выберите точку на карте");
        navStatus.setTextSize(16);
        navStatus.setGravity(Gravity.CENTER);
        navStatus.setPadding(12, 8, 12, 8);
        root.addView(navStatus, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        map.setOnTouchListener((v, event) -> {
            if (event.getAction() == 1) {
                GeoPoint p = (GeoPoint) map.getProjection().fromPixels((int)event.getX(), (int)event.getY());
                selectDestination(p);
            }
            return false;
        });

        tts = new TextToSpeech(this, status -> { if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru", "RU")); });
        startLocation();
    }

    private void selectDestination(GeoPoint point) {
        destination = point;
        if (destinationMarker != null) map.getOverlays().remove(destinationMarker);
        destinationMarker = new Marker(map);
        destinationMarker.setPosition(point);
        destinationMarker.setTitle("Точка назначения");
        map.getOverlays().add(destinationMarker);
        map.invalidate();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("📍 Точка назначения");
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        Button route = new Button(this);
        route.setText("Маршрут");
        route.setOnClickListener(v -> showNavigatorButton());
        box.addView(title);
        box.addView(route);
        new android.app.AlertDialog.Builder(this).setView(box).setPositiveButton("Закрыть", null).show();
    }

    private void showNavigatorButton() {
        Button toggle = new Button(this);
        toggle.setText("Включить навигатор");
        toggle.setOnClickListener(v -> {
            navigatorEnabled = !navigatorEnabled;
            toggle.setText(navigatorEnabled ? "Выключить навигатор" : "Включить навигатор");
            navStatus.setText(navigatorEnabled ? "🔊 Навигатор включён" : "🔇 Навигатор выключен");
            if (navigatorEnabled) speak("Навигатор включён");
        });
        new android.app.AlertDialog.Builder(this).setTitle("🧭 Маршрут готов").setMessage("Маршрут построен до выбранной точки.").setView(toggle).setPositiveButton("Готово", null).show();
    }

    private void startLocation() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 10);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
    }

    @Override public void onLocationChanged(Location location) {
        if (navigatorEnabled && destination != null) {
            Location d = new Location("destination");
            d.setLatitude(destination.getLatitude()); d.setLongitude(destination.getLongitude());
            float meters = location.distanceTo(d);
            if (meters < 50) speak("Вы прибыли в пункт назначения");
            else if (meters < 500) speak("До точки назначения примерно " + Math.round(meters) + " метров");
        }
    }

    private void speak(String text) { if (navigatorEnabled && tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "maps-nav"); }
    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    @Override protected void onResume() { super.onResume(); if (map != null) map.onResume(); }
    @Override protected void onPause() { if (map != null) map.onPause(); if (tts != null) tts.stop(); super.onPause(); }
    @Override protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }
}
