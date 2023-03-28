package com.sampullman.ble.operation;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import com.sampullman.ble.BluetoothLeService;

import java.util.UUID;

import timber.log.Timber;

public class CharacteristicRequest extends LeOperation {
    public static final int WRITE=1, READ=2, REQUEST_NOTIFY=3, REQUEST_INDICATE=4;
    private static final UUID CHAR_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final UUID uuid, serviceUuid;
    private final byte[] data;
    private final int type;

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

    private boolean descriptorWorkaround(BluetoothGatt gatt, BluetoothGattDescriptor desc) {
        final BluetoothGattCharacteristic parentCharacteristic = desc.getCharacteristic();
        final int originalWriteType = parentCharacteristic.getWriteType();
        parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        final boolean result = gatt.writeDescriptor(desc);
        parentCharacteristic.setWriteType(originalWriteType);
        return result;
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
            //Timber.d("Sent read %s", cha.getUuid());v
            return gatt.readCharacteristic(cha);

        } else if(getType() == CharacteristicRequest.REQUEST_NOTIFY) {
            boolean enabled = getData() == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            gatt.setCharacteristicNotification(cha, enabled);
            BluetoothGattDescriptor desc = cha.getDescriptor(CHAR_NOTIFICATION_DESCRIPTOR_UUID);

            if(desc != null) {
                desc.setValue(getData());

                return descriptorWorkaround(gatt, desc);
            } else {
                return false;
            }
        } else if(getType() == CharacteristicRequest.REQUEST_INDICATE) {
            gatt.setCharacteristicNotification(cha, true);
            BluetoothGattDescriptor desc = cha.getDescriptor(CHAR_NOTIFICATION_DESCRIPTOR_UUID);

            if(desc != null) {
                desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);

                return descriptorWorkaround(gatt, desc);
            } else {
                return false;
            }
        }
        return false;
    }
}
