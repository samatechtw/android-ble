package com.sampullman.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.os.Handler;
import android.os.Looper;

import java.util.UUID;

import timber.log.Timber;

public class LeConnection {
    private static final int MAX_RETRIES = 4;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    private final BluetoothDevice device;
    public BluetoothGatt gatt;
    private int discoveryTries = 0, connectionTries = 0;
    private boolean servicesDiscovered = false;
    public final BluetoothLeService leService;
    private int aclConnections = 0;
    private int connectionState = STATE_DISCONNECTED;

    public LeConnection(BluetoothLeService leService, BluetoothDevice device) {
        this.device = device;
        this.leService = leService;
    }

    public boolean isConnected() {
        return connectionState == STATE_CONNECTED;
    }

    public boolean isConnecting() {
        return connectionState == STATE_CONNECTING;
    }

    public boolean shouldRetry() {
        return connectionTries <= MAX_RETRIES;
    }

    public void setConnected(boolean connected) {
        // Attempts to discover services after successful connection.
        if(connected && gatt != null) {
            connected();
        } else {
            disconnected();
        }
    }

    public void connected() {
        this.connectionState = STATE_CONNECTED;
        discoveryTries = 3;
        boolean starting = gatt.discoverServices(); // FIXME gatt is null when discoverServices is called during RSSI
        Timber.i("Attempting to start service discovery: %b", starting);
    }

    public void disconnected() {
        setAclConnections(0);
        servicesDiscovered = false;
        disconnectAndCloseGatt();
        this.connectionState = STATE_DISCONNECTED;
    }

    void handleServicesDiscovered(int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {

            if(!servicesDiscovered) {
                servicesDiscovered();
            }

        } else if(discoveryTries-- >= 0) {
            Timber.w("onServicesDiscovered received: %d", status);
            try {
                Thread.sleep(1800);
            } catch (InterruptedException e) {
                Timber.e("onServicesDiscovered sleep fail %s", e.getMessage());
            }
            gatt.discoverServices();
        }
    }

    public void servicesDiscovered() {
        Timber.d("Services discovered %d", System.identityHashCode(this));
        servicesDiscovered = true;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void disconnectAndCloseGatt() {
        Timber.d("le connection disconnected and closed: %s, gatt null=%b", device.getName(), gatt==null);
        if(gatt != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    gatt.disconnect();
                    gatt.close();
                    gatt = null;
                    try { Thread.sleep(300); } catch(Exception e) {}
                } catch(NullPointerException e) {
                    // gatt.close() can result in NPE. TODO -- figure out a better solution here
                    Timber.d(e, "disconnectAndCloseGatt NPE");
                    gatt = null;
                }
            });
        }
    }

    public int getAclConnections() {
        return aclConnections;
    }

    private void setAclConnections(int aclConnections) {
        this.aclConnections = aclConnections;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public String getName() {
        return (device == null) ? null : device.getName();
    }

    public void setConnecting(BluetoothGatt gatt) {
        connectionTries += 1;
        this.gatt = gatt;
        this.connectionState = STATE_CONNECTING;
    }

    public void requestNotification(UUID serviceUuid, UUID uuid, boolean on) {
        leService.requestNotification(gatt, serviceUuid, uuid, on);
    }

    public void requestIndication(UUID serviceUuid, UUID uuid) {
        leService.requestIndication(gatt, serviceUuid, uuid);
    }

    public void readCharacteristic(UUID serviceUuid, UUID characteristic) {
        leService.readCharacteristic(gatt, serviceUuid, characteristic);
    }

    public void writeCharacteristic(UUID serviceUuid, UUID characteristic, byte[] data) {
        leService.writeCharacteristic(gatt, serviceUuid, characteristic, data);
    }

    @Override
    public String toString() {
        return "LeConnection name:" + device.getName() + ", connectionState:" + connectionState;
    }
}