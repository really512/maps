package com.really512.maps;

import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;

public class MapsCarScreen extends Screen {
    public MapsCarScreen(CarContext context) { super(context); }
    @Override public Template onGetTemplate() {
        Action stop = new Action.Builder().setTitle("Выйти").setOnClickListener(this::finish).build();
        Action voice = new Action.Builder().setTitle("Навигатор").setOnClickListener(() ->
                getCarContext().getCarService(androidx.car.app.navigation.NavigationManager.class)
                        .navigationStarted()).build();
        return new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(voice).addAction(stop).build())
                .build();
    }
}
