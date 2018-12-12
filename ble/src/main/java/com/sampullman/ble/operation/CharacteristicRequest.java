package com.sampullman.ble.operation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.sampullman.ble.BluetoothLeService;

import java.util.UUID;

import timber.log.Timber;

public class CharacteristicRequest extends LeOperation {
    public static final int WRITE=1, READ=2, REQUEST_NOTIFY=3;
    private static final UUID CHAR_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    final UUID uuid, serviceUuid;
    final byte[] data;
    final int type;

    public CharacteristicRequest(BluetoothGatt gatt, UUID serviceUuid, UUID uuid, int type) {
        this(gatt, serviceUuid, uuid, null, type);
    }

    public CharacteristicRequest(BluetoothGatt gatt, UUID serviceUuid, UUID uuid, byte[] data, int type) {
        super(gatt);
        this.serviceUuid = serviceUuid;
        this.uuid = uuid;
        this.data = data;
        this.type = type;
    }

    public UUID getUuid() { return uuid; }

    public int getType() { return type; }

    public byte[] getData() { return data; }

    BluetoothGattCharacteristic getServiceCharacteristic(BluetoothGatt gatt, UUID serviceUuid, UUID uuid) {

        //check mBluetoothGatt is available
        if (gatt == null) {
            Timber.d("No BLE connection");
            return null;
        }
        BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) {
            Timber.d("Service not found! %s", serviceUuid.toString());
            return null;
        }
        return service.getCharacteristic(uuid);
    }

    public UUID getServiceUuid() {
        return serviceUuid;
    }

    public boolean execute(BluetoothLeService service) {
        BluetoothGatt gatt = getGatt();
        BluetoothGattCharacteristic cha = getServiceCharacteristic(gatt, getServiceUuid(), getUuid());
        if(cha == null) {
            Timber.e("Characteristic not found! %s", getUuid().toString());
            return false;
        }
        if(getType() == CharacteristicRequest.WRITE) {
            if(getData() != null) {
                cha.setValue(getData());
            }
            return gatt.writeCharacteristic(cha);

        } else if(getType() == CharacteristicRequest.READ) {
            //Timber.d("Sent read %s", cha.getUuid());
            return gatt.readCharacteristic(cha);

        } else if(getType() == CharacteristicRequest.REQUEST_NOTIFY) {
            //Timber.d("Sent notify %s", cha.getUuid());

            boolean enabled = getData() == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            gatt.setCharacteristicNotification(cha, enabled);
            BluetoothGattDescriptor desc = cha.getDescriptor(CHAR_NOTIFICATION_DESCRIPTOR_UUID);

            if(desc != null) {
                desc.setValue(getData());
                return gatt.writeDescriptor(desc);
            } else {
                return false;
            }
        }
        return false;
    }
}
