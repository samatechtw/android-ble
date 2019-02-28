package com.sampullman.ble;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.sampullman.ble.event.LeConnectionEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;

import timber.log.Timber;

import static com.sampullman.ble.BluetoothLeService.*;

/**
 * Handles calls from BluetoothLeService
 * Manages a list of LE connections
 */
public class LeClient {

    private final EventBus bus = EventBus.getDefault();
    private BluetoothLeService leService;
    // list of current connections. the last index should always be the EDR connection;
    private final ArrayList<LeConnection> connectionList = new ArrayList<>();
    private BleListener bleListener;

    public interface BleListener {
        void servicesDiscovered(LeConnection connection);
        void characteristicRead(LeConnection connection, String uuid, byte[] data);
        void characteristicNotification(LeConnection connection, String uuid, byte[] data);
        void characetersticWriteComplete(LeConnection connection, String uuid, byte[] data);
    }

    public LeClient(Context appContext) {
        registerAndBind(appContext);
        bus.register(this);
    }

    public void setBleListener(BleListener listener) {
        this.bleListener = listener;
    }

    public BluetoothLeService getBleService() {
        return leService;
    }

    // Code to manage BLE Service lifecycle.
    private final ServiceConnection leServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Timber.d("onServiceConnected(). componentName:%s", componentName.toString());
            leService = ((BluetoothLeService.LocalBinder) service).getService();
            leService.initialize();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Timber.d("ServiceConnection disconnected:%s", componentName);
            leService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data as a result of read or notify
    // ACTION_GATT_NOTIFY: tell device to send data on a specific port
    // ACTION_GATT_WRITE: Finished writing a characteristic
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Timber.i("Received GATT update: %s", action);
            BluetoothDevice device = intent.getParcelableExtra(action);
            LeConnection connection = getConnectionFromDevice(device);

            if(ACTION_GATT_CONNECTED.equals(action)) {
                updateLeConnectionState(true, connection);

            } else if(ACTION_GATT_DISCONNECTED.equals(action)) {
                updateLeConnectionState(false, connection);

            } else if(ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                if(connection != null) {
                    int status = intent.getIntExtra(EXTRA_STATUS, BluetoothGatt.GATT_FAILURE);
                    connection.handleServicesDiscovered(status);
                    if(status == BluetoothGatt.GATT_SUCCESS && bleListener != null) {
                        bleListener.servicesDiscovered(connection);
                    }
                }

            } else if (ACTION_DATA_AVAILABLE.equals(action)) {
                handleReadCharacteristic(connection, intent);

            } else if(ACTION_GATT_NOTIFY.equals(action)) {
                handleNotifyCharacteristic(connection, intent);

            } else if(ACTION_GATT_WRITE.equals(action)) {
                handleWriteComplete(connection, intent);

            } else if(ACTION_BLUETOOTH_ERROR.equals(action)) {
                handleBluetoothError();

            }
        }
    };

    private void handleBluetoothError() {
        LeConnection connection = getLastConnection();
        if(connection != null && connection.shouldRetry()) {
            connection.disconnectAndCloseGatt();
            connectLeDelayed(connection);
        } else {
            connectionList.clear();
            bus.post(new LeConnectionEvent(connection, false, true));
            Timber.d("Broadcast the LeConnectionEvent error");
        }
    }

    private void handleNotifyCharacteristic(LeConnection connection, Intent intent) {
        String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
        byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

        if(bleListener != null) {
            bleListener.characteristicNotification(connection, uuid, data);
        }
    }

    private void handleReadCharacteristic(LeConnection connection, Intent intent) {
        String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
        byte[] data = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

        if(bleListener != null) {
            bleListener.characteristicRead(connection, uuid, data);
        }
    }

    private void handleWriteComplete(LeConnection connection, Intent intent) {
        String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
        byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);

        if(bleListener != null) {
            bleListener.characetersticWriteComplete(connection, uuid, value);
        }
    }

    private void registerAndBind(Context c) {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_BLUETOOTH_ERROR);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_NOTIFY);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_WRITE);

        c.registerReceiver(gattUpdateReceiver, intentFilter);
        Intent gattServiceIntent = new Intent(c, BluetoothLeService.class);
        c.bindService(gattServiceIntent, leServiceConnection, Activity.BIND_AUTO_CREATE);
    }

    public void unregisterAndUnbind(Context c) {
        try {
            c.unregisterReceiver(gattUpdateReceiver);
            c.unbindService(leServiceConnection);

        } catch(IllegalArgumentException e) {
            Timber.e("Unregistered Bluetooth receivers twice");
        }
        leService = null;
    }

    public LeConnection getConnectionFromDevice(BluetoothDevice device) {
        if(device == null) {
            Timber.d("getConnectionFromDevice tried with null device");
            return null;
        }
        for(LeConnection connection : connectionList) {
            Timber.d("FIND %s", connection.getDevice().getAddress());
            if(device.getAddress().equals(connection.getDevice().getAddress())) {
                return connection;
            }
        }
        Timber.d("unable to find getConnectionFromDevice() %s, %d", device.getAddress(), connectionList.size());
        return null;
    }

    private void updateLeConnectionState(boolean connected, LeConnection connection) {

        Timber.d("updateLeConnectionState(). is connection null: %s, connected=%b",
                connection == null, connected);

        if(connection != null) {
            boolean wasConnected = connection.isConnected();
            connection.setConnected(connected);

            if(!connected && wasConnected) {

                connectionList.remove(connection);
            }

        } else {
            Timber.d("updateLeConnectionState nonexistent connection. connected:%b", connected);
        }
        bus.post(new LeConnectionEvent(connection, connected));
    }

    public BluetoothDevice getLastConnectedDevice() {
        LeConnection connection = getLastConnection();
        if(connection != null) {
            return connection.getDevice();
        } else {
            return null;
        }
    }

    public LeConnection getLastConnection() {
        if(connectionList.size() > 0) {
            return connectionList.get(connectionList.size() - 1);
        }
        return null;
    }

    public void connectLeDevice(BluetoothDevice device) {
        if(leService == null) {
            Timber.d("BLE Service not available");
        } else {
            LeConnection connection = new LeConnection(leService, device);
            connectLeDelayed(connection);
        }
    }

    public void connectLeDelayed(final LeConnection connection) {
        connectionList.add(connection);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> connectLe(connection),120);
    }

    // Abstracted from connectLe(BluetoothDevice) so we can reuse this code for LE reconnection
    // No need to add to connectionList, it should only happen on the first attempt
    private void connectLe(LeConnection connection) {

        if(!leService.connect(connection)) {
            connectionList.remove(connection);
        }

        for(BluetoothDevice device : leService.getConnectedDevices()) {
            if(device.getType() == BluetoothDevice.DEVICE_TYPE_LE || device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
                Timber.d("Already connected to %s", device.getName());

            }
        }
        Timber.d("Connecting to LE device");
    }

    // True if successfully disconnected last device
    public void disconnectLastConnection() {
        if(connectionList.size() == 0) {
            Timber.d("could not disconnect connectionList with 0 size");
            return;
        }
        LeConnection lastConnection = connectionList.get(connectionList.size()-1);
        lastConnection.disconnectAndCloseGatt();
        connectionList.remove(lastConnection);
        Timber.d("successfully disconnected last connection:%s", lastConnection.getName());
    }

    public void removeConnection(LeConnection connection) {
        boolean status = connectionList.remove(connection);
        Timber.d("removeConnection() success:%s for device:%s", status, connection.getName());
    }

    public ArrayList<LeConnection> getConnections() {
        return connectionList;
    }

    public boolean disconnectDeviceByName(String name) {
        if(name==null) {
            Timber.e("cannot disconnect with null name");
            return false;
        }
        for(LeConnection connection : connectionList) {
            if(connection.getName().equals(name)) {
                Timber.d("disconnected by name:%s", connection.getName());
                connection.disconnectAndCloseGatt();
                connectionList.remove(connection);
                return true;
            }
        }

        Timber.d("could not disconnect device by name:%s", name);
        return false;
    }

    public void disconnectAll() {
        for(LeConnection connection : connectionList) {
            connection.disconnectAndCloseGatt();
        }
        connectionList.clear();
    }

    public boolean isConnected() {
        if(leService == null) {
            Timber.d("BLE Service null!");
            return false;
        } else {
            for(LeConnection connection : connectionList) {
                if(connection.isConnected()) {
                    return true;
                }
            }
            return false;
        }
    }

    public int getConnectionCount() {
        return connectionList.size();
    }

    public String getDeviceName(int index) {
        return connectionList.get(index).getDevice().getName();
    }

}