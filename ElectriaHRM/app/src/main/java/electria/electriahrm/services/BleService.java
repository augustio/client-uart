
/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package electria.electriahrm.services;

        import android.app.Service;
        import android.bluetooth.BluetoothAdapter;
        import android.bluetooth.BluetoothDevice;
        import android.bluetooth.BluetoothGatt;
        import android.bluetooth.BluetoothGattCallback;
        import android.bluetooth.BluetoothGattCharacteristic;
        import android.bluetooth.BluetoothGattDescriptor;
        import android.bluetooth.BluetoothGattService;
        import android.bluetooth.BluetoothManager;
        import android.bluetooth.BluetoothProfile;
        import android.content.Context;
        import android.content.Intent;
        import android.os.Binder;
        import android.os.Handler;
        import android.os.IBinder;
        import android.support.v4.content.LocalBroadcastManager;
        import android.util.Log;

        import java.util.List;
        import java.util.UUID;

        import electria.electriahrm.R;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BleService extends Service {
    private final static String TAG = BleService.class.getSimpleName();
    private static final int FIRST_BITMASK = 0x01;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mBatteryCharacteristic;
    private BluetoothGattCharacteristic mHRLocationCharacteristic;
    private BluetoothGattCharacteristic mRXCharacteristic;
    private BluetoothGattCharacteristic mTXCharacteristic;
    private BluetoothGattCharacteristic mHRMCharacteristic;
    private int mConnectionState = STATE_DISCONNECTED;
    private int readBatteryInterval;
    private Handler mHandler = new Handler();
    private int initBatteryLevel = INVALID_LEVEL;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int SHORT_INTERVAL = 500;
    private static final int LONG_INTERVAL = 60000;
    private static final int INVALID_LEVEL = 255;

    public final static String ACTION_GATT_CONNECTED =
            "electria.electriahrm.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "electria.electriahrm.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "electria.electriahrm.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_RX_DATA_AVAILABLE =
            "electria.electriahrm.ACTION_DATA_AVAILABLE";
    public final static String ACTION_BATTERY_LEVEL_DATA_AVAILABLE =
            "electria.electriahrm.ACTION_BATTERY_LEVEL_DATA_AVAILABLE";
    public final static String ACTION_TX_CHAR_WRITE =
            "electria.electriahrm.ACTION_TX_CHAR_WRITE";
    public final static String EXTRA_DATA =
            "electria.electriahrm.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "electria.electriahrm.DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String ACTION_SENSOR_POSITION_READ =
            "electria.electriahrm.ACTION_SENSOR_POSITION_READ";
    public final static String ACTION_HEART_RATE_READ =
            "electria.electriahrm.ACTION_HEART_RATE_READ";

    private final static UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID TX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID RX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private final static UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHAR_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");
    private final static UUID HR_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    private static final UUID HRM_CHARACTERISTIC_UUID = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb");
    private static final UUID ECG_SENSOR_LOCATION_CHARACTERISTIC_UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.d(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.d(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                initBatteryLevel = INVALID_LEVEL;
                Log.d(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "mBluetoothGatt = " + mBluetoothGatt);
                List<BluetoothGattService> services = gatt.getServices();
                for (BluetoothGattService service : services) {
                    if (service.getUuid().equals(UART_SERVICE_UUID)) {
                        mRXCharacteristic = service.getCharacteristic(RX_CHAR_UUID);
                        mTXCharacteristic = service.getCharacteristic(TX_CHAR_UUID);
                    } else if (service.getUuid().equals(HR_SERVICE_UUID)) {
                        mHRMCharacteristic = service.getCharacteristic(HRM_CHARACTERISTIC_UUID );
                        mHRLocationCharacteristic = service.getCharacteristic(ECG_SENSOR_LOCATION_CHARACTERISTIC_UUID);
                        //Read sensor location
                        readECGSensorLocation();
                    }else if (service.getUuid().equals(BATTERY_SERVICE_UUID)) {
                        mBatteryCharacteristic = service.getCharacteristic(BATTERY_LEVEL_CHAR_UUID);
                        //Start reading battery characteristic every minute
                        mReadBatteryLevel.run();
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mBatteryCharacteristic != null &&
                        characteristic.getUuid().equals(BATTERY_LEVEL_CHAR_UUID)) {
                    int level = characteristic.getValue()[0];
                    broadcastUpdate(ACTION_BATTERY_LEVEL_DATA_AVAILABLE, level);
                    if(initBatteryLevel == INVALID_LEVEL)
                        initBatteryLevel = level;
                }
                else if (characteristic.getUuid().equals(ECG_SENSOR_LOCATION_CHARACTERISTIC_UUID)) {
                    final String sensorPosition = getBodySensorPosition(characteristic.getValue()[0]);
                    broadcastUpdate(ACTION_SENSOR_POSITION_READ, sensorPosition);
                    enableRXNotification();
                }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if(characteristic.getUuid().equals(RX_CHAR_UUID)) {
                Log.w("TEST", characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0).toString());
                broadcastUpdate(ACTION_RX_DATA_AVAILABLE, characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0));
            }
            else if (characteristic.getUuid().equals(HRM_CHARACTERISTIC_UUID)) {
                int hrValue = 0;
                if (isHeartRateInUINT16(characteristic.getValue()[0])) {
                    hrValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1);
                } else {
                    hrValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                }
                broadcastUpdate(ACTION_HEART_RATE_READ, hrValue);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(characteristic.getUuid().equals(TX_CHAR_UUID))
                    broadcastUpdate(ACTION_TX_CHAR_WRITE);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if(status == BluetoothGatt.GATT_SUCCESS){
                if(descriptor.getCharacteristic().getUuid().equals(RX_CHAR_UUID)) {
                    enableHRNotification();
                }
            }
        }
    };

    private Runnable mReadBatteryLevel = new Runnable() {
        @Override
        public void run() {
            if (mBluetoothGatt != null && mBatteryCharacteristic != null) {
                mBluetoothGatt.readCharacteristic(mBatteryCharacteristic);
                if(initBatteryLevel == INVALID_LEVEL)
                    readBatteryInterval = SHORT_INTERVAL;
                else
                    readBatteryInterval = LONG_INTERVAL;
                mHandler.postDelayed(mReadBatteryLevel, readBatteryInterval);
            }
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String stringValue) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, stringValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action, final int intValue){
        int newintValue = ((intValue & 0xFF00) >> 8) | ((intValue & 0xFF) << 8);
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, newintValue);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        Log.d(TAG, "mBluetoothGatt closed");
        mBluetoothGatt = null;
        mBluetoothDeviceAddress = null;
        mHandler.removeCallbacks(mReadBatteryLevel);
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if(characteristic != null && mConnectionState == STATE_CONNECTED) {
            boolean status = mBluetoothGatt.readCharacteristic(characteristic);
            Log.d(TAG, "Read char - status=" + status);
        }
        else if(characteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    private void readECGSensorLocation() {
        if (mHRLocationCharacteristic != null) {
            mBluetoothGatt.readCharacteristic(mHRLocationCharacteristic);
        }
    }

    private String getBodySensorPosition(byte bodySensorPositionValue) {
        String[] locations = this.getResources().getStringArray(R.array.sensor_locations);
        if (bodySensorPositionValue > locations.length)
            return this.getString(R.string.location_other);
        return locations[bodySensorPositionValue];
    }

    public void enableRXNotification()
    {
        if (mRXCharacteristic != null && mConnectionState == STATE_CONNECTED) {
            mBluetoothGatt.setCharacteristicNotification(mRXCharacteristic, true);
            BluetoothGattDescriptor descriptor = mRXCharacteristic.getDescriptor(CCCD_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }else if(mRXCharacteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    public void enableHRNotification()
    {
        if (mHRMCharacteristic != null && mConnectionState == STATE_CONNECTED) {
            mBluetoothGatt.setCharacteristicNotification(mHRMCharacteristic, true);
            BluetoothGattDescriptor descriptor = mHRMCharacteristic.getDescriptor(CCCD_UUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }else if(mHRMCharacteristic == null){
            Log.e(TAG, "Charateristic not found!");
        }
    }

    public void writeTXCharacteristic(byte[] value)
    {
        if(mTXCharacteristic != null && mConnectionState == STATE_CONNECTED) {
            mTXCharacteristic.setValue(value);
            boolean status = mBluetoothGatt.writeCharacteristic(mTXCharacteristic);
            Log.d(TAG, "write TXchar - status=" + status);
        }
        else if(mTXCharacteristic == null){
            Log.e(TAG, "Charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
        }
    }

    /**
     * This method will check if Heart rate value is in 8 bits or 16 bits
     */
    private boolean isHeartRateInUINT16(byte value) {
        if ((value & FIRST_BITMASK) != 0)
            return true;
        return false;
    }

}
