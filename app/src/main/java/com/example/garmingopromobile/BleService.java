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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class BleService {
    private static final UUID GOPRO_SERVICE = UUID.fromString("0000fea6-0000-1000-8000-00805f9b34fb");

    private enum RequestType {
        CHARACTERISTIC,
        TOGGLE_DESCRIPTOR_NOTIFICATIONS
    }

    private enum CommandID {
        SHUTTER         ((byte) 1),
        SLEEP           ((byte) 5),
        HILIGHT         ((byte) 24);

        private final byte value;
        CommandID(byte value) {
            this.value = value;
        }
        public byte getValue() {
            return value;
        }
        static CommandID get(byte value) {
            for (CommandID id: CommandID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }
    }

    public enum SettingID {
        RESOLUTION      ((byte) 2),
        FRAMERATE       ((byte) 3),
        LENS            ((byte) 121),
        FLICKER         ((byte) 134),
        HYPERSMOOTH     ((byte) 135);

        private final byte value;
        SettingID(byte value) {
            this.value = value;
        }
        public byte getValue() {
            return value;
        }
        static SettingID get(byte value) {
            for (SettingID id: SettingID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }
    }

    private enum StateID {
        HOT             ((byte) 6),
        BUSY            ((byte) 8),
        ENCODING        ((byte) 10),
        PROGRESS        ((byte) 13),
        BATTERY         ((byte) 70),
        READY           ((byte) 82),
        COLD            ((byte) 85);

        private final byte value;
        StateID(byte value) {
            this.value = value;
        }
        public byte getValue() {
            return value;
        }
        static StateID get(byte value) {
            for (StateID id: StateID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }
    }

    private enum QueryID {
        GET_SETTINGS            ((byte) 0x12),
        GET_STATUS              ((byte) 0x13),
        GET_AVAILABLE           ((byte) 0x32),
        REGISTER_SETTINGS       ((byte) 0x52),
        REGISTER_STATUS         ((byte) 0x53),
        REGISTER_AVAILABLE      ((byte) 0x62),
        UNREGISTER_SETTINGS     ((byte) 0x72),
        UNREGISTER_STATUS       ((byte) 0x73),
        UNREGISTER_AVAILABLE    ((byte) 0x82),
        NOTIF_SETTINGS          ((byte) 0x92),
        NOTIF_STATUS            ((byte) 0x93),
        NOTIF_AVAILABLE         ((byte) 0xA2);

        private final byte value;
        QueryID(byte value) {
            this.value = value;
        }
        public byte getValue() {
            return value;
        }
        static QueryID get(byte value) {
            for (QueryID id: QueryID.values()) {
                if (value == id.getValue()) return id;
            }
            return null;
        }
    }

//    TODO: tout passer en private
    GoPro gopro;
    Context appContext;
    BluetoothDevice bluetoothDevice;
    BluetoothLeService bleService;
    BluetoothGatt goproGatt;

    private Thread keepAliveProcess;


    ArrayList<RequestType> requestTypeQueue;
    ArrayList<UUID> requestUuidQueue;
    ArrayList<byte[]> requestDataQueue;
    ArrayList<UUID> responseUuidQueue;
    ArrayList<byte[]> responseExpectedQueue;
    boolean pendingRequest;

    public BleService(BluetoothDevice bluetoothDevice, Context appContext, GoPro gopro) {
        this.bluetoothDevice = bluetoothDevice;
        this.appContext = appContext;
        this.gopro = gopro;

        requestTypeQueue = new ArrayList<>();
        requestUuidQueue = new ArrayList<>();
        requestDataQueue = new ArrayList<>();
        responseUuidQueue = new ArrayList<>();
        responseExpectedQueue = new ArrayList<>();
    }


    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public Boolean ConnectedAndReady = false;
    byte[] lastStatus = new byte[0];

    public boolean disconnect() {
        goproGatt.close();
        stopKeepAlive();
        return true;
    }

    @SuppressLint("MissingPermission")
    public boolean connect() {

        if (ConnectedAndReady)
            return true;

        System.out.println(bluetoothDevice);
        if (bluetoothDevice != null) {
            if (goproGatt != null) {
                try {
                    goproGatt.close();
                } catch (Exception e) {
                }
            }
//            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//                System.out.println("perm not granted");
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                return false;
//            }
            System.out.println("connect gatt");
            goproGatt = bluetoothDevice.connectGatt(appContext, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gopro.getLinkedWatch().send(GarminDevice.Communication.COM_CONNECT, 0);
                        System.out.println("GoPro ble connected");
                        gatt.discoverServices();
                    } else {
                        gopro.disconnect();
                        gopro.getLinkedWatch().send(GarminDevice.Communication.COM_CONNECT, 1);
                        System.out.println("GoPro ble disconnected");
                        ConnectedAndReady = false;
                    }
                    super.onConnectionStateChange(gatt, status, newState);
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
                    System.out.println("Services discovered");
                    for (BluetoothGattService service : gatt.getServices()) {
                        for (BluetoothGattCharacteristic characteristic: service.getCharacteristics()) {
                            if (characteristic.getUuid().equals(GoPro.COMMAND_RESPONSE) || characteristic.getUuid().equals(GoPro.SETTINGS_RESPONSE) ||characteristic.getUuid().equals(GoPro.QUERY_RESPONSE)) {
                                gatt.setCharacteristicNotification(characteristic, true);
                                prepareRequest(RequestType.TOGGLE_DESCRIPTOR_NOTIFICATIONS, characteristic.getUuid(), BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, null, null);
                            }
                        }
                    }
                    byte[] request = new byte[] {(byte) 0x05, (byte) 0x52, (byte) 0x02, (byte) 0x03, (byte) 0x79};
                    prepareRequest(RequestType.CHARACTERISTIC, GoPro.QUERY_REQUEST, request, GoPro.QUERY_RESPONSE, null);
                    startKeepAlive();
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
//                    super.onCharacteristicWrite(gatt, characteristic, status);
//                    requestDataQueue.remove(0);
//                    requestUuidQueue.remove(0);
////                    responseExpectedQueue.remove(0);
//                    responseUuidQueue.remove(0);
//
//                    if (requestDataQueue.isEmpty()) pendingRequest = false;
//                    else writeCharacteristic();
//                    synchronized (writeLock) {
//                        waitingWrite = false;
//                        writeLock.notifyAll();
//                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    System.out.println("Descriptor written");
                    super.onDescriptorWrite(gatt, descriptor, status);
                    popRequest();
                    if (requestTypeQueue.isEmpty()) pendingRequest = false;
                    else processRequest();
                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    System.out.println("Received response from camera");
                    super.onCharacteristicChanged(gatt, characteristic);

                    if (!requestUuidQueue.isEmpty() && characteristic.getUuid().equals(responseUuidQueue.get(0))) {
//                        goproGatt.setCharacteristicNotification(characteristic, false);
//                        if (characteristic.getValue().equals(responseExpectedQueue.get(0))) {
//                            System.out.println("Good camera reponse");
//                        } else {
//                            System.out.println("Bad camera response error : expected "+responseExpectedQueue.get(0)+", but got "+characteristic.getValue());
//                        }
                        popRequest();
                    }

                    else if (characteristic.getUuid().equals(GoPro.QUERY_RESPONSE)) {
                        byte[] response = characteristic.getValue();
                        showBytes(response);
                        switch (QueryID.get(response[1])) {
                            case REGISTER_SETTINGS, NOTIF_SETTINGS:
                                byte[] updatedSettings = Arrays.copyOfRange(response, 3, response.length);
                                gopro.setSettings(updatedSettings);
                            break;
                            default:
                                System.out.println("Unexpected query ID");
                        }
                    }

                    if (requestTypeQueue.isEmpty()) pendingRequest = false;
                    else processRequest();
//                    synchronized (ChangeLock) {
//                        waitingChange = false;
//                        ChangeLock.notifyAll();
//                    }
//
//
//                    byte[] newStatus = characteristic.getValue();
//                    Log.d("tag", "onCharacteristicChanged " + Arrays.toString(newStatus));
//
//
//                    if (newStatus.length > 5 && newStatus[1] == -109) {
//                        if (newStatus[3] == 0xA) {
//                            Log.d("log", "Shutter : " + newStatus[5]);
//                            if (newStatus[5] == 1) {
//                                hasShutter = true;
//                                if (lastCamMode == 0)
//                                    linkedWatch.sendMessage(GoProRemoteIQ.MessageType.Status, "Recording", "");
//                            } else {
//                                if (lastCamMode == 0)
//                                    linkedWatch.sendMessage(GoProRemoteIQ.MessageType.Status, "Standby", "");
//                            }
//                        }
//                        if (newStatus[3] == 0x26 || (newStatus.length > 6 && newStatus[6] == 0x26)) {
//                            byte picNum = newStatus[newStatus.length - 1];
//                            Log.d("log", "Pictures num : " + picNum);
//                            if (lastCamMode == 1)
//                                linkedWatch.sendMessage(GoProRemoteIQ.MessageType.Status, "Pictures", Byte.toString(picNum));
//                        }
//                        if (newStatus[3] == 43) {
//                            if (newStatus[5] >= 0) {
//                                Log.d("log", "Set last cam status (" + newStatus[5] + ")");
//                                lastCamMode = newStatus[5];
//                            }
//                        }
//                        if (newStatus[3] == 70) {
//                            if (newStatus[5] >= 0) {
//                                if (newStatus[5] != lasBatteryPercent) {
//                                    Log.d("log", "Set battery percent (" + newStatus[5] + ")");
//                                    lasBatteryPercent = newStatus[5];
//                                    linkedWatch.sendMessage(GoProRemoteIQ.MessageType.Battery, Byte.toString(lasBatteryPercent), "");
//                                }
//                            }
//                        }
//                        return;
//                    }
//                    lastStatus = newStatus;
//                    //System.out.println("Status notified : " + Arrays.toString(lastStatus));
//                    synchronized (readyLock) {
//                        waitForReady = false;
//                        readyLock.notifyAll();
//                    }
//                    //record();
                }


            });

            //goproGatt.connect();
//            if (WaitConnectedAndReady()) {
//                MonitorStatus();
//                CurrentMode();
//                CurrentBattery();
//                return true;
//            }

        }
        return  false;








        // Code to manage Service lifecycle.

//        ServiceConnection mServiceConnection = new ServiceConnection() {
//
//            @Override
//            public void onServiceConnected(ComponentName componentName, IBinder service) {
//                bleService = ((BluetoothLeService.LocalBinder) service).getService();
//                if (!bleService.initialize()) {
////                    log unable to initialize bluetooth
//                    System.out.println("unable to initialize bluetooth");
//                }
//
//                ArrayList<Object> connectionSuccessMessage = new ArrayList<>();
//                connectionSuccessMessage.add(GarminDevice.Communication.COM_CONNECT);
//                connectionSuccessMessage.add(0);
//                try {
//                    linkedWatch.send(connectionSuccessMessage);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//
//                // Automatically connects to the device upon successful start-up initialization.
//                bleService.connect(bluetoothDevice.getAddress());
//            }
//
//            @Override
//            public void onServiceDisconnected(ComponentName componentName) {
//                bleService = null;
//            }
//        };
//
//        Intent gattServiceIntent = new Intent(appContext, BluetoothLeService.class);
//        appContext.bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
//        System.out.println("test");
    }

    private void startKeepAlive() {
        keepAliveProcess = new Thread() {
            @Override
            public void run() {
                byte[] request = new byte[]{(byte) 0x03, (byte) 0x5b, (byte) 0x01, (byte) 0x42};
                byte [] response = new byte[]{(byte) 0x02, (byte) 0x5b, (byte) 0x00};
                while (true) {
                    try {
                        sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("Keep alive thread interrupted");
                        break;
                    }
                    prepareRequest(RequestType.CHARACTERISTIC, GoPro.SETTINGS_REQUEST, request, GoPro.SETTINGS_RESPONSE, response);
                }
            }
        };
        keepAliveProcess.start();
    }

    private void stopKeepAlive() {
        keepAliveProcess.interrupt();
    }

    private void prepareRequest(RequestType type, UUID requestID, byte[] requestData, UUID responseID, byte[] expectedResponse) {
//        TODO: find expected response from request
        requestTypeQueue.add(type);
        requestDataQueue.add(requestData);
        requestUuidQueue.add(requestID);
        responseUuidQueue.add(responseID);
        responseExpectedQueue.add(expectedResponse);

        System.out.print("Submit request : "+type);
        if (!pendingRequest) {
            processRequest();
            System.out.println();
        }
        else System.out.println(" - Unable to process while pending request : "+requestTypeQueue.get(0));
    }

    public void prepareRequest(UUID requestID, byte[] requestData, UUID responseID) {
        prepareRequest(RequestType.CHARACTERISTIC, requestID, requestData, responseID, null);
    }

    public void processRequest() {
        pendingRequest = true;

        switch (requestTypeQueue.get(0)) {
            case CHARACTERISTIC:
//                Write Characteristic
                BluetoothGattCharacteristic characteristic = goproGatt.getService(GOPRO_SERVICE).getCharacteristic(requestUuidQueue.get(0));
                characteristic.setValue(requestDataQueue.get(0));
                goproGatt.writeCharacteristic(characteristic);
                break;
            case TOGGLE_DESCRIPTOR_NOTIFICATIONS:
//                Write Descriptor
                BluetoothGattDescriptor descriptor = goproGatt.getService(GOPRO_SERVICE).getCharacteristic(requestUuidQueue.get(0)).getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                descriptor.setValue(requestDataQueue.get(0));
                goproGatt.writeDescriptor(descriptor);
        }
    }

    public void popRequest() {
        requestTypeQueue.remove(0);
        requestUuidQueue.remove(0);
        requestDataQueue.remove(0);
        responseUuidQueue.remove(0);
        responseExpectedQueue.remove(0);
    }


    private void showBytes(byte[] data) {
        for (byte b: data) {
            System.out.printf("%02x:", b);
        }
        System.out.println();
    }
}
