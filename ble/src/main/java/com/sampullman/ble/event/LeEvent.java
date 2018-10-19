package com.sampullman.ble.event;

import com.sampullman.ble.LeConnection;

public class LeEvent {
    private LeConnection leConnection;

    public LeEvent(LeConnection connection) {
        this.leConnection = connection;
    }

    public LeConnection getLeConnection() {
        return this.leConnection;
    }
}
