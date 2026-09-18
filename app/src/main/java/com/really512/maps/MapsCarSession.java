package com.really512.maps;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

public class MapsCarSession extends Session {
    @NonNull @Override public Screen onCreateScreen(@NonNull Intent intent) {
        return new MapsCarScreen(getCarContext());
    }
}
