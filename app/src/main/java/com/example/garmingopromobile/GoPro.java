package com.example.garmingopromobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import java.util.UUID;

public class GoPro {
    Context appContext;
    BluetoothDevice bluetoothDevice;
    BluetoothLeService bleService;
    GarminDevice linkedWatch;
    BluetoothGatt goproGatt;


    public GoPro(BluetoothDevice bluetoothDevice, Context appContext) {
        this.bluetoothDevice = bluetoothDevice;
        this.appContext = appContext;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public Boolean ConnectedAndReady = false;
    byte[] lastStatus = new byte[0];

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
                        linkedWatch.send(GarminDevice.Communication.COM_CONNECT, 1);
                        System.out.println("GoPro ble connected");
                        gatt.discoverServices();
                    } else {
                        linkedWatch.send(GarminDevice.Communication.COM_CONNECT, 2);
                        System.out.println("GoPro ble disconnected");
                        ConnectedAndReady = false;
                    }
                    super.onConnectionStateChange(gatt, status, newState);
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    super.onServicesDiscovered(gatt, status);
//                    enableNotifications();
                }


                @Override
                public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    super.onCharacteristicWrite(gatt, characteristic, status);
//                    synchronized (writeLock) {
//                        waitingWrite = false;
//                        writeLock.notifyAll();
//                    }
                }

                @Override
                public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                    super.onDescriptorWrite(gatt, descriptor, status);

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ConnectedAndReady = true;
                    //MonitorRecording();

                }

                @Override
                public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                    super.onCharacteristicChanged(gatt, characteristic);

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

    private static final String REQUEST_START_VIDEO = "03:01:01:01";

    private static final UUID GOPRO_SERVICE_UUID = UUID.fromString("0000fea6-0000-1000-8000-00805f9b34fb");
    private static final UUID GOPRO_COMMAND_UUID = UUID.fromString("b5f90072-aa8d-11e3-9046-0002a5d5c51b");
    public void startRecording() {
        // Send the start recording command to the GoPro
        byte[] command = new byte[]{(byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) 0x01};
        writeCharacteristic(GOPRO_COMMAND_UUID, command);
    }

    private void writeCharacteristic(UUID command, byte[] request) {
        BluetoothGattCharacteristic characteristic = goproGatt.getService(GOPRO_SERVICE_UUID)
                .getCharacteristic(command);

        characteristic.setValue(request);
        goproGatt.writeCharacteristic(characteristic);
    }

    public void setLinkedWatch(GarminDevice linkedWatch) {
        this.linkedWatch = linkedWatch;
    }

    public String getAddress() {
        return bluetoothDevice.getAddress();
    }

    @NonNull
    @Override
    public String toString() {
        return bluetoothDevice.getName();
    }
}
