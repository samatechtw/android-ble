package com.sampullman.ble.event;

import com.sampullman.ble.LeConnection;

public class LeReadEvent extends LeEvent {
    private final String uuid;
    private final byte[] data;

    public LeReadEvent(LeConnection connection, String uuid, byte[] data) {
        super(connection);
        this.uuid = uuid;
        this.data = data;
    }

    public String getUUID() {
        return uuid;
    }

    public byte[] getData() {
        return data;
    }

}
