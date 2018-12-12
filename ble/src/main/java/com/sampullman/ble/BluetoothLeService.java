package com.sampullman.ble;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.sampullman.ble.operation.CharacteristicRequest;
import com.sampullman.ble.operation.ConnectOperation;
import com.sampullman.ble.operation.LeOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import timber.log.Timber;

/**
 * Service for managing connection and data communication with a GATT server and the device
 * Serializes BLE commands by maintaining them in a queue.
 */
@TargetApi(18)
public class BluetoothLeService extends Service {
    private final Handler operationHandler = new Handler(Looper.getMainLooper());

    // Android internal API constants
    public static final int GATT_ERROR = 0x0085;
    public static final int GATT_CONN_FAIL_ESTABLISH = 0x003E;

    private BluetoothAdapter bluetoothAdapter;
    private final ArrayList<LeOperation> operationQueue = new ArrayList<>();

    public final static String ACTION_GATT_CONNECTED = "com.sampullman.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.sampullman.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_BLUETOOTH_ERROR = "com.sampullman.bluetooth.le.ACTION_BLUETOOTH_ERROR";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.sampullman.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.sampullman.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_GATT_WRITE = "com.sampullman.bluetooth.le.ACTION_GATT_WRITE";
    public final static String ACTION_GATT_NOTIFY = "com.sampullman.bluetooth.le.ACTION_GATT_NOTIFY";

    public final static String EXTRA_DATA = "com.sampullman.bluetooth.le.EXTRA_DATA";
    // unique identifier that tells what type of data
    public final static String EXTRA_UUID = "com.sampullman.bluetooth.le.EXTRA_UUID";
    public final static String EXTRA_STATUS = "com.sampullman.bluetooth.le.EXTRA_STATUS";

    class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, we must ensure that BluetoothGatt.close() is called
        // so that resources are cleaned up properly.
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     */
    public void initialize() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param connection An uninitialized connection object
     *
     * @return Return a true if a connection attempt will be made. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final LeConnection connection) {
        if (bluetoothAdapter == null) {
            Timber.w("BluetoothAdapter not initialized");
            return false;
        }

        BluetoothDevice device = connection.getDevice();
        if (device == null) {
            Timber.w("Device not found. Unable to connect.");
            return false;
        }
        queueOperation(new ConnectOperation(connection));
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        if(bluetoothManager != null) {
            return bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        }
        return null;
    }

    private void executeOperation(final LeOperation operation) {
        operationHandler.post(() -> {
            // If the operation fails to execute, remove it and try the next one
            if(!operation.execute(BluetoothLeService.this)) {
                Timber.d("FAILED TO SEND LE");
                operationComplete();
            }
        });
    }

    private void queueOperation(LeOperation operation) {
        operationQueue.add(operation);
        if(operationQueue.size() == 1) {
            operation.execute(this);
        }
    }

    void operationComplete() {
        if(operationQueue.size() > 0) {
            operationQueue.remove(0);
        } else {
            Timber.d("Unqueued characteristic sent");
        }
        // Try to send the next characteristic, if one is queued
        if(operationQueue.size() > 0) {
            executeOperation(operationQueue.get(0));
        }
    }

    void clearOperations() {
        operationQueue.clear();
    }

    public void readCharacteristic(BluetoothGatt gatt, UUID serviceUuid, UUID characteristic) {
        queueOperation(new CharacteristicRequest(gatt, serviceUuid, characteristic, CharacteristicRequest.READ));
    }

    public void writeCharacteristic(BluetoothGatt gatt, UUID serviceUuid, UUID charUUID, byte[] data) {
        queueOperation(new CharacteristicRequest(gatt, serviceUuid, charUUID, data, CharacteristicRequest.WRITE));
    }

    public void requestNotification(BluetoothGatt gatt, UUID serviceUuid, UUID charUUID, boolean on) {
        byte[] data = on ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
        queueOperation(new CharacteristicRequest(gatt, serviceUuid, charUUID, data, CharacteristicRequest.REQUEST_NOTIFY));
    }
}