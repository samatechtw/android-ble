package com.sampullman.ble.event;

import com.sampullman.ble.LeConnection;

public class LeConnectionEvent extends LeEvent {
    private boolean connected;
    private boolean error;

    public LeConnectionEvent(LeConnection connection, boolean connected) {
        this(connection, connected, false);
    }

    public LeConnectionEvent(LeConnection connection, boolean connected, boolean error) {
        super(connection);
        this.connected = connected;
        this.error = error;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isError() { return error; }
}
