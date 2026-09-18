package com.really512.maps;

import android.content.pm.ApplicationInfo;
import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.SessionInfo;
import androidx.car.app.validation.HostValidator;

public class MapsCarAppService extends CarAppService {
    @NonNull @Override public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }
    @NonNull @Override public Session onCreateSession(@NonNull SessionInfo sessionInfo) {
        return new MapsCarSession();
    }
}
