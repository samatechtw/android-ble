package com.sampullman.ble.event;

import com.sampullman.ble.LeConnection;

public class LeNotificationEvent extends LeReadEvent {

    public LeNotificationEvent(LeConnection connection, String uuid, byte[] data) {
        super(connection, uuid, data);
    }
}
