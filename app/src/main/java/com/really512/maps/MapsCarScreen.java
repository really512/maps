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
    }

    @NonNull @Override public Template onGetTemplate() {
        SharedPreferences p = getCarContext().getSharedPreferences(PREFS, CarContext.MODE_PRIVATE);
        navigating = p.getBoolean("navigation_enabled", false)
                && p.contains("destination_lat") && p.contains("destination_lon");

        NavigationTemplate.Builder builder = new NavigationTemplate.Builder();

        Action toggle = new Action.Builder()
                .setTitle(navigating ? "Стоп" : "Навигатор")
                .setOnClickListener(() -> {
                    boolean next = !navigating;
                    p.edit().putBoolean("navigation_enabled", next).apply();
                    navigating = next;
                    invalidate();
                }).build();

        builder.setActionStrip(new ActionStrip.Builder().addAction(toggle).build());

        if (navigating) {
            String instruction = p.getString("next_instruction", "Следуйте по маршруту");
            float stepMeters = p.getFloat("next_step_distance_m", 0f);
            float destinationMeters = p.getFloat("destination_distance_m", stepMeters);

            Distance stepDistance = Distance.create(Math.max(0d, stepMeters), Distance.UNIT_METERS);
            Distance destinationDistance = Distance.create(Math.max(0d, destinationMeters), Distance.UNIT_METERS);

            TravelEstimate estimate = new TravelEstimate.Builder(
                    destinationDistance, Duration.create(0, Duration.UNIT_MILLIS)).build();

            Step step = new Step.Builder(instruction).build();
            builder.setNavigationInfo(new RoutingInfo.Builder()
                    .setCurrentStep(step, stepDistance)
                    .build());
            builder.setDestinationTravelEstimate(estimate);
        }

        return builder.build();
    }
}
