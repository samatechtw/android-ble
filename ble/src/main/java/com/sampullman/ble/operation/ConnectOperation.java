package com.sampullman.ble.operation;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;

import com.sampullman.ble.BluetoothLeService;
import com.sampullman.ble.LeConnection;
import com.sampullman.ble.GattCallback;

import timber.log.Timber;

public class ConnectOperation extends LeOperation {
    private final LeConnection leConnection;

    public ConnectOperation(LeConnection connection) {
        super();
        this.leConnection = connection;
    }

    public boolean execute(BluetoothLeService service) {
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        BluetoothGatt gatt;
        int currentApiVersion = android.os.Build.VERSION.SDK_INT;
        GattCallback gattCallback = new GattCallback(service);
        gattCallback.startConnectTimer();

        BluetoothDevice device = leConnection.getDevice();
        if (currentApiVersion >= android.os.Build.VERSION_CODES.M) {
            gatt = device.connectGatt(service, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            Timber.d("Connecting with old API");
            gatt = device.connectGatt(service, false, gattCallback);

        }
        leConnection.setConnecting(gatt);
        gattCallback.setGatt(gatt);
        return true;
    }
}
