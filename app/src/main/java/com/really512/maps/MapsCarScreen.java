package com.really512.maps;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Distance;
import androidx.car.app.model.Duration;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.car.app.navigation.model.RoutingInfo;
import androidx.car.app.navigation.model.Step;
import androidx.car.app.navigation.model.TravelEstimate;

public class MapsCarScreen extends Screen {
    private static final String PREFS = "maps_navigation";
    private boolean navigating;

    public MapsCarScreen(CarContext context) {
        super(context);
        SharedPreferences p = context.getSharedPreferences(PREFS, CarContext.MODE_PRIVATE);
        navigating = p.getBoolean("navigation_enabled", false)
                && p.contains("destination_lat") && p.contains("destination_lon");
    }

    @NonNull @Override public Template onGetTemplate() {
        SharedPreferences p = getCarContext().getSharedPreferences(PREFS, CarContext.MODE_PRIVATE);
        NavigationTemplate.Builder builder = new NavigationTemplate.Builder();

        Action toggle = new Action.Builder()
                .setTitle(navigating ? "Стоп" : "Навигатор")
                .setOnClickListener(() -> {
                    navigating = !navigating;
                    p.edit().putBoolean("navigation_enabled", navigating).apply();
                    invalidate();
                }).build();
        builder.setActionStrip(new ActionStrip.Builder().addAction(toggle).build());

        if (navigating && p.contains("destination_distance_m")) {
            String title = p.getString("destination_title", "Место назначения");
            float meters = p.getFloat("destination_distance_m", 0f);
            Distance distance = Distance.create(Math.max(0d, meters), Distance.UNIT_METERS);
            TravelEstimate estimate = new TravelEstimate.Builder(
                    distance, Duration.create(0, Duration.UNIT_MINUTES)).build();
            builder.setDestinationTravelEstimate(estimate);
            Step step = new Step.Builder("Следуйте по маршруту к " + title).build();
            builder.setNavigationInfo(new RoutingInfo.Builder()
                    .setCurrentStep(step, distance).build());
        }
        return builder.build();
    }
}
