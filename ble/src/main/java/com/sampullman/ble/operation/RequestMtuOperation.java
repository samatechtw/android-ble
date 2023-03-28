package com.sampullman.ble.operation;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import com.sampullman.ble.BluetoothLeService;
import com.sampullman.ble.LeConnection;
import com.sampullman.ble.GattCallback;

import timber.log.Timber;

public class RequestMtuOperation extends LeOperation {
    private final int mtu;

    public RequestMtuOperation(BluetoothGatt gatt, int mtu) {
        super(gatt);
        this.mtu = mtu;
    }

    public boolean execute(BluetoothLeService service) {
        GattCallback gattCallback = new GattCallback(service);
        gattCallback.setGatt(gatt);
        gatt.requestMtu(this.mtu);
        return true;
    }
}
