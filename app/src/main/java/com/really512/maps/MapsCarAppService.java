package com.really512.maps;

import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

public class MapsCarAppService extends CarAppService {
    @Override public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }
    @Override public Session onCreateSession() {
        return new MapsCarSession();
    }
}
