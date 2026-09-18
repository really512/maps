package com.really512.maps;

import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.car.app.CarContext;

public class MapsCarSession extends Session {
    @Override public Screen onCreateScreen(Intent intent) {
        return new MapsCarScreen(getCarContext());
    }
}
