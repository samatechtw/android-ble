package com.sampullman.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Handler;

import java.util.List;

import timber.log.Timber;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static com.sampullman.ble.BluetoothLeService.*;

/**
 * Implements callback methods for GATT events that the app cares about.
 * For example: connection change and services discovered
 */
public class GattCallback extends BluetoothGattCallback {
    private final Handler handler = new Handler();
    private BluetoothGatt gatt;
    private final BluetoothLeService leService;

    public GattCallback(BluetoothLeService leService) {
        this.leService = leService;
    }

    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    public void startConnectTimer() {
        handler.postDelayed(() -> {
            Timber.d("GATT CALLBACK FAIL gatt null=%b", gatt==null);
            if(gatt != null) {
                List<BluetoothDevice> devices = leService.getConnectedDevices();
                if(devices != null && devices.size() == 0) {
                    broadcastUpdate(gatt.getDevice(), ACTION_GATT_DISCONNECTED, BluetoothGatt.GATT_SUCCESS);
                }
                gatt.disconnect();
                gatt.close();
                gatt = null;
            }
        }, 10000);
    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, final int status, int newState) {
        Timber.d("GATT STATUS: %d, newState=%d", status, newState);
        leService.operationComplete();
        handler.removeCallbacksAndMessages(null);

        if(status == GATT_ERROR || status == GATT_CONN_FAIL_ESTABLISH) {
            broadcastUpdate(null, ACTION_BLUETOOTH_ERROR, status);

        } else {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(gatt.getDevice(), ACTION_GATT_CONNECTED, GATT_SUCCESS);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(gatt.getDevice(), ACTION_GATT_DISCONNECTED, GATT_SUCCESS);
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        broadcastUpdate(gatt.getDevice(), ACTION_GATT_SERVICES_DISCOVERED, status);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic cha, int status) {
        super.onCharacteristicWrite(gatt, cha, status);
        if(status == BluetoothGatt.GATT_SUCCESS) {
            broadcastUpdate(gatt.getDevice(), ACTION_GATT_WRITE, status, cha);
        } else {
            Timber.d("LE CHARACTERISTIC WRITE FAILED. status:%s", status);
        }
        leService.operationComplete();
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt,
                                     BluetoothGattCharacteristic characteristic,
                                     int status) {
        if(status == BluetoothGatt.GATT_SUCCESS) {
            broadcastUpdate(gatt.getDevice(), ACTION_DATA_AVAILABLE, status, characteristic);
        } else {
            Timber.d("LE CHARACTERISTIC READ FAILED %d", status);
        }
        leService.operationComplete();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        broadcastUpdate(gatt.getDevice(), ACTION_GATT_NOTIFY, GATT_SUCCESS, characteristic);
    }

    @Override
    public void onDescriptorWrite (BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        leService.operationComplete();
    }

    private void broadcastUpdate(BluetoothDevice device, final String action, int status) {
        Intent intent = new Intent(action);
        intent.putExtra(action, device);
        intent.putExtra(EXTRA_STATUS, status);
        leService.sendBroadcast(intent);
    }

    private void broadcastUpdate(BluetoothDevice device, final String action, int status,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        // Writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();

        intent.putExtra(action, device);
        intent.putExtra(EXTRA_DATA, data);
        intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
        intent.putExtra(EXTRA_STATUS, status);
        leService.sendBroadcast(intent);
    }

}