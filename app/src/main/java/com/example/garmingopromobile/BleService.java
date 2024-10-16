package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class BleService {
    private static final String TAG = "BleService";
    private static final UUID GOPRO_SERVICE = UUID.fromString("0000fea6-0000-1000-8000-00805f9b34fb");

    private enum RequestType {
        CHARACTERISTIC,
        TOGGLE_DESCRIPTOR_NOTIFICATIONS
    }

    public enum SettingID {
        RESOLUTION((byte) 2),
        FRAMERATE((byte) 3),
        LENS((byte) 121),
        FLICKER((byte) 134),
        HYPERSMOOTH((byte) 135);

        private final byte value;

        SettingID(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        static SettingID get(byte value) {
            for (SettingID id : SettingID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }

        static ArrayList<Byte> getAllValues() {
            ArrayList<Byte> result = new ArrayList<>();
            for (SettingID id : SettingID.values()) {
                result.add(id.getValue());
            }
            return result;
        }
    }

    public enum StatusID {
//        TODO: send status notification to watch (HOT, COLD)
//        TODO: check for status before sending request (BUSY, READY)
//        TODO: set as camera state (BATTERY, ENCODING) + send PROGRESS when ENCODING changed
        HOT((byte) 6),
        BUSY((byte) 8),
        ENCODING((byte) 10),
        PROGRESS((byte) 13),
        BATTERY((byte) 70),
        READY((byte) 82),
        COLD((byte) 85);

        private final byte value;

        StatusID(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        static StatusID get(byte value) {
            for (StatusID id : StatusID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }

        static ArrayList<Byte> getAllValues() {
            ArrayList<Byte> result = new ArrayList<>();
            for (StatusID id : StatusID.values()) {
                result.add(id.getValue());
            }
            return result;
        }
    }

    public enum QueryID {
        GET_SETTINGS((byte) 0x12),
        GET_STATUS((byte) 0x13),
        GET_AVAILABLE((byte) 0x32),
        REGISTER_SETTINGS((byte) 0x52),
        REGISTER_STATUS((byte) 0x53),
        REGISTER_AVAILABLE((byte) 0x62),
        UNREGISTER_SETTINGS((byte) 0x72),
        UNREGISTER_STATUS((byte) 0x73),
        UNREGISTER_AVAILABLE((byte) 0x82),
        NOTIF_SETTINGS((byte) 0x92),
        NOTIF_STATUS((byte) 0x93),
        NOTIF_AVAILABLE((byte) 0xA2);

        private final byte value;

        QueryID(byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        static public QueryID get(byte value) {
            for (QueryID id : QueryID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }

        static ArrayList<Byte> getAllValues() {
            ArrayList<Byte> result = new ArrayList<>();
            for (QueryID id : QueryID.values()) {
                result.add(id.getValue());
            }
            return result;
        }

    }


    private final GoPro gopro;
    private final Context appContext;
    private final BluetoothDevice bluetoothDevice;
    private BluetoothGatt goproGatt;

    private Thread keepAliveProcess;


    private final ArrayList<RequestType> requestTypeQueue;
    private final ArrayList<UUID> requestUuidQueue;
    private final ArrayList<byte[]> requestDataQueue;
    private boolean requestPending;
    private boolean requestAnswered;
    private int requestCounter;
    private static final Object requestLock = new Object();
    private ByteArrayOutputStream longReplyBuffer;
    private int longReplyLength;

    public BleService(BluetoothDevice bluetoothDevice, Context appContext, GoPro gopro) {
        this.bluetoothDevice = bluetoothDevice;
        this.appContext = appContext;
        this.gopro = gopro;

        requestTypeQueue = new ArrayList<>();
        requestUuidQueue = new ArrayList<>();
        requestDataQueue = new ArrayList<>();
    }

    private boolean checkBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public Boolean ConnectedAndReady = false;

    public boolean isConnected() {
        return ConnectedAndReady;
    }

    public boolean disconnect() {
        stopKeepAlive();

        requestPending = false;
        requestTypeQueue.clear();
        requestUuidQueue.clear();
        requestDataQueue.clear();


        if (checkBluetoothPermission()) {
            if (goproGatt != null) goproGatt.close();
            ConnectedAndReady = false;
        } else {
            TextLog.logError("Bluetooth permission not granted");
            return false;
        }
        return true;
    }

    @SuppressLint("MissingPermission")
    public boolean connect() {

        if (ConnectedAndReady)
            return true;

        if (bluetoothDevice != null) {
            if (goproGatt != null) {
                disconnect();
            }
            TextLog.logInfo("Connecting to GoPro GATT");
            goproGatt = bluetoothDevice.connectGatt(appContext, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gopro.getLinkedWatch().send(GarminDevice.Communication.COM_CONNECT, 0);
                        TextLog.logInfo("GoPro BLE connected");
                        ConnectedAndReady = true;
                        gatt.discoverServices();
                    } else {
                        gopro.getLinkedWatch().send(GarminDevice.Communication.COM_CONNECT, 1);
                        TextLog.logInfo("GoPro BLE disconnected");
                        ConnectedAndReady = false;
                        disconnect();
                    }
                    super.onConnectionStateChange(gatt, status, newState);
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    startKeepAlive();

                    Log.v(TAG, "Services discovered");
                    for (BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(GoPro.COMMAND_RESPONSE) || characteristic.getUuid().equals(GoPro.SETTINGS_RESPONSE) || characteristic.getUuid().equals(GoPro.QUERY_RESPONSE)) {
                                gatt.setCharacteristicNotification(characteristic, true);
                                prepareRequest(RequestType.TOGGLE_DESCRIPTOR_NOTIFICATIONS, characteristic.getUuid(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            }
                        }
                    }
//                    Register for settings change : resolution, framerate, lens, hypersmooth and flicker
                    byte[] registerSettings = new byte[]{(byte) 0x06, QueryID.REGISTER_SETTINGS.getValue(), SettingID.RESOLUTION.getValue(), SettingID.FRAMERATE.getValue(), SettingID.LENS.getValue(), SettingID.HYPERSMOOTH.getValue(), SettingID.FLICKER.getValue()};
                    prepareRequest(RequestType.CHARACTERISTIC, GoPro.QUERY_REQUEST, registerSettings);

//                    Register for status change : encoding
                    byte[] registerStatus = new byte[]{(byte) 0x06, QueryID.REGISTER_STATUS.getValue(), StatusID.ENCODING.getValue()};
                    prepareRequest(RequestType.CHARACTERISTIC, GoPro.QUERY_REQUEST, registerStatus);

//                    Register for available settings changes
                    byte[] registerAvailable = new byte[]{(byte) 0x06, QueryID.REGISTER_AVAILABLE.getValue(), SettingID.RESOLUTION.getValue(), SettingID.FRAMERATE.getValue(), SettingID.LENS.getValue()};
                    prepareRequest(RequestType.CHARACTERISTIC, GoPro.QUERY_REQUEST, registerAvailable);
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    Log.v(TAG, "Characteristic written");
                    super.onCharacteristicWrite(gatt, characteristic, status);
                    synchronized (requestLock) {
                        requestAnswered = true;
                        requestLock.notify();
                    }
                    popRequest();
                    if (requestTypeQueue.isEmpty()) requestPending = false;
                    else processRequest();
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    Log.v(TAG, "Descriptor written");
                    super.onDescriptorWrite(gatt, descriptor, status);
                    synchronized (requestLock) {
                        requestAnswered = true;
                        requestLock.notify();
                    }
                    popRequest();
                    if (requestTypeQueue.isEmpty()) requestPending = false;
                    else processRequest();
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    Log.v(TAG, "Received response from camera, characteristic UUID = " + characteristic.getUuid());
                    Log.v(TAG, "Characteristic value = " + bytesToString(characteristic.getValue()));
                    super.onCharacteristicChanged(gatt, characteristic);

                    if (characteristic.getUuid().equals(GoPro.QUERY_RESPONSE)) {
                        try {
                            decodeQuery(characteristic.getValue());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });

        }

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                if (!ConnectedAndReady) {
                    gopro.getLinkedWatch().send(GarminDevice.Communication.COM_CONNECT, 1);
                    TextLog.logInfo("GoPro BLE disconnected");
                    ConnectedAndReady = false;
                    disconnect();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        return false;
    }

    private void decodeQuery(byte[] response) throws IOException {
        if ((response[0] & (byte) 0xe0) == (byte) 0x00) { // 5-bit length packets
            if (QueryID.getAllValues().contains(response[1])) {
                gopro.readTLVMessage(Arrays.copyOfRange(response, 1, response.length));
            } else TextLog.logWarn("Unexpected query ID");
        } else if ((response[0] & (byte) 0xe0) == (byte) 0x20) { // 13-bit length packets
            if (QueryID.getAllValues().contains(response[2])) {
                longReplyLength = ((response[0] & (byte) 0x1f) << 8) + response[1];
                longReplyBuffer = new ByteArrayOutputStream();
                longReplyBuffer.write(Arrays.copyOfRange(response, 2, response.length));
            } else TextLog.logWarn("Unexpected query ID");
        } else if ((response[0] & (byte) 0xe0) == (byte) 0x40) { // 16-bit length packets
            if (QueryID.getAllValues().contains(response[3])) {
                longReplyLength = (response[1] << 8) + response[2];
                longReplyBuffer = new ByteArrayOutputStream();
                longReplyBuffer.write(Arrays.copyOfRange(response, 3, response.length));
            } else TextLog.logWarn("Unexpected query ID");
        } else if ((response[0] & (byte) 0x80) == (byte) 0x80) { // Continuation packet
            longReplyBuffer.write(Arrays.copyOfRange(response, 1, response.length));
            if (longReplyBuffer.size() == longReplyLength) {
                gopro.readTLVMessage(longReplyBuffer.toByteArray());
            }
        } else {
            TextLog.logWarn("Unexpected packet header");
        }
    }

    /** @noinspection BusyWait*/
    private void startKeepAlive() {
        byte[] request = new byte[]{(byte) 0x03, (byte) 0x5b, (byte) 0x01, (byte) 0x42};
        prepareRequest(RequestType.CHARACTERISTIC, GoPro.SETTINGS_REQUEST, request);

        keepAliveProcess = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    TextLog.logWarn("GoPro keep alive loop interrupted");
                    break;
                }
                prepareRequest(RequestType.CHARACTERISTIC, GoPro.SETTINGS_REQUEST, request);
            }
        });
        keepAliveProcess.start();
    }

    private void stopKeepAlive() {
        if (keepAliveProcess != null) {
            keepAliveProcess.interrupt();
        }
    }

    private void prepareRequest(RequestType type, UUID requestID, byte[] requestData) {
        requestTypeQueue.add(type);
        requestUuidQueue.add(requestID);
        requestDataQueue.add(requestData);

        if (!requestPending) {
            Log.v(TAG, "Submit request : " + type);
            processRequest();
        } else Log.v(TAG, "New request posted in queue of type : " + type);
    }

    public void prepareRequest(UUID requestID, byte[] requestData) {
        prepareRequest(RequestType.CHARACTERISTIC, requestID, requestData);
    }

    private void processRequest() {
        requestPending = true;

        new Thread(() -> {
            switch (requestTypeQueue.get(0)) {
                case CHARACTERISTIC -> {
//                Write Characteristic
                    BluetoothGattCharacteristic characteristic = goproGatt.getService(GOPRO_SERVICE).getCharacteristic(requestUuidQueue.get(0));
                    characteristic.setValue(requestDataQueue.get(0));
                    writeCharacteristicLoop(goproGatt, characteristic);
                }
                case TOGGLE_DESCRIPTOR_NOTIFICATIONS -> {
//                Write Descriptor
                    BluetoothGattDescriptor descriptor = goproGatt.getService(GOPRO_SERVICE).getCharacteristic(requestUuidQueue.get(0)).getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                    descriptor.setValue(requestDataQueue.get(0));
                    writeDescriptorLoop(goproGatt, descriptor);
                }
            }
        }).start();
    }

    private void writeCharacteristicLoop(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        requestAnswered = false;
        requestCounter = 0;
        do {
            if (!checkBluetoothPermission()) {
                TextLog.logError("Bluetooth permission not granted, stopped writing characteristic");
                return;
            }
            gatt.writeCharacteristic(characteristic);
            synchronized (requestLock) {
                try {
                    requestLock.wait(500);
                    if (!requestAnswered) Log.w(TAG, "Writing characteristic failed, trying again");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (!requestAnswered && ++requestCounter < 5);
        if (requestCounter == 5) {
            Log.e(TAG, "Writing failed 5 times in a row, disconnecting and trying to reconnect");
            disconnect();
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            connect();
        }
    }

    private void writeDescriptorLoop(BluetoothGatt gatt, BluetoothGattDescriptor descriptor) {
        requestAnswered = false;
        requestCounter = 10;
        do {
            if (!checkBluetoothPermission()) {
                TextLog.logError("Bluetooth permission not granted, stopped writing descriptor");
                return;
            }
            gatt.writeDescriptor(descriptor);
            synchronized (requestLock) {
                try {
                    requestLock.wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } while (!requestAnswered || requestCounter--==0);
        if (requestCounter==0) {
            gopro.disconnect();
            disconnect();
            connect();
        }
    }

    public void popRequest() {
        requestTypeQueue.remove(0);
        requestUuidQueue.remove(0);
        requestDataQueue.remove(0);
    }

    public static String bytesToString(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte b: data) {
            result.append(String.format("%02x:", b));
        }
//        Log.v(TAG, result.toString());
        return result.toString();
    }
}
