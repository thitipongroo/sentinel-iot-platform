package com.sentinel.iot.service.notification;

public interface NotificationProvider {

    void send(String message);

    boolean isEnabled();

    String providerName();
}
