package com.sampullman.ble.operation;

import android.bluetooth.BluetoothGatt;

import com.sampullman.ble.BluetoothLeService;

public abstract class LeOperation {
    BluetoothGatt gatt;

    public LeOperation() {}

    public LeOperation(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    public BluetoothGatt getGatt() { return gatt; }

    public abstract boolean execute(BluetoothLeService service);
}
