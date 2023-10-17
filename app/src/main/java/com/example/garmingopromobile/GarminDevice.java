package com.example.garmingopromobile;

import android.content.Context;
import android.util.Log;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import java.util.ArrayList;
import java.util.List;

public class GarminDevice extends IQDevice {
    private static final String TAG = "GarminDevice";
    private ConnectIQ connectIQ;
    private GoPro linkedGoPro;
    private IQApp iqApp;

    public enum Communication {
        COM_CONNECT,
        COM_FETCH_SETTINGS,
        COM_PUSH_SETTINGS,
        COM_FETCH_STATES,
        COM_PUSH_STATES,
        COM_SHUTTER,
        COM_HIGHLIGHT,
        COM_LOCKED,
        COM_PROGRESS
    }

    public GarminDevice(ConnectIQ connectIQ, long deviceId, String name) throws InvalidStateException, ServiceUnavailableException {
        super(deviceId, name);
        this.connectIQ = connectIQ;

        connectIQ.setAdbPort(7381);

        connectIQ.registerForDeviceEvents(this, (device, newStatus) -> {
            switch (newStatus) {
                case CONNECTED -> {
                    TextLog.logInfo("Garmin device connected");
                    try {
                        registerForMessages();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case NOT_CONNECTED -> {
                    TextLog.logInfo("Garmin device not connected");
                    if (iqApp != null) {
                        try {
                            connectIQ.unregisterForApplicationEvents(GarminDevice.this, iqApp);
                        } catch (InvalidStateException e) {
                            e.printStackTrace();
                        }
                    }
                }
                case NOT_PAIRED -> {
                    TextLog.logError("Garmin device not paired");
                    try {
                        connectIQ.unregisterForDeviceEvents(GarminDevice.this);
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                    }
                }
                case UNKNOWN -> {
                    TextLog.logWarn("Garmin device unknown");
                    try {
                        connectIQ.unregisterForDeviceEvents(GarminDevice.this);
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void registerForMessages() throws InvalidStateException, ServiceUnavailableException {
        connectIQ.getApplicationInfo("e3998790-3052-4fe7-8c81-0c11d0fc52fc", this, new ConnectIQ.IQApplicationInfoListener() { // old uuid 1c0c49b8-5abb-4e59-a679-e102757ec01e
            @Override
            public void onApplicationInfoReceived(IQApp iqApp) {
                GarminDevice.this.iqApp = iqApp;
                TextLog.logInfo("ConnectIQ GoPro Remote widget status : "+iqApp.getStatus());
                try {
                    connectIQ.registerForAppEvents(GarminDevice.this, GarminDevice.this.iqApp, new ConnectIQ.IQApplicationEventListener() {
                        @Override
                        public void onMessageReceived(IQDevice iqDevice, IQApp iqApp, List<Object> list, ConnectIQ.IQMessageStatus iqMessageStatus) {
                            Log.v(TAG, "Message received from watch");
                            if (iqMessageStatus==ConnectIQ.IQMessageStatus.SUCCESS) {
                                try {
                                    onReceive(list);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onApplicationNotInstalled(String s) {
                TextLog.logError("ConnectIQ GoPro Remote widget not installed on device");
            }
        });
    }

    public void send(Communication messageType, Object data) {
        IQDevice device = this;

        List<Object> message = new ArrayList<>();
        message.add(messageType.ordinal());
        message.add(data);

        class ThreadedSend implements Runnable {
            List<Object> message;

            ThreadedSend(List<Object> message) {
                this.message = message;
            }

            public void run() {
                try {
                    connectIQ.sendMessage(device, iqApp, message, (iqDevice, iqApp, iqMessageStatus) -> {
                        if (iqMessageStatus != ConnectIQ.IQMessageStatus.SUCCESS) {
                            TextLog.logError("Message send to watch failed : "+iqMessageStatus);
                            Log.w(TAG, "Message content : "+message);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        Thread threadedSend = new Thread(new ThreadedSend(message));
        threadedSend.start();
    }


    public void onReceive(List<Object> data) throws InvalidStateException, ServiceUnavailableException {
        Log.v(TAG, "Data received from watch : "+data);
        Communication type = Communication.values()[(int) ((List<?>) data.get(0)).get(0)];
        Object loadout = ((List<?>) data.get(0)).get(1);
        switch (type) {
            case COM_CONNECT:
                if ((Integer) loadout == 0) TextLog.logInfo("Watch app started, connecting to gopro : "+linkedGoPro.connect());
                else TextLog.logInfo("Watch app stopped, disconnecting gopro : "+linkedGoPro.disconnect());
                break;
            case COM_FETCH_SETTINGS:
                break;
            case COM_PUSH_SETTINGS:
                linkedGoPro.sendSettings((List<Integer>) loadout);
                TextLog.logInfo("Watch sending settings to GoPro..."+loadout);
                break;
            case COM_FETCH_STATES:
                break;
            case COM_PUSH_STATES:
                break;
            case COM_SHUTTER:
                linkedGoPro.pressShutter();
                TextLog.logInfo("Watch sending shutter command to GoPro...");
                break;
            case COM_HIGHLIGHT:
                linkedGoPro.pressHilight();
                TextLog.logInfo("Watch sending highlight command to GoPro...");
                break;
            case COM_LOCKED:
                break;
        }
    }

    public void setLinkedGoPro(GoPro linkedGoPro) {
        this.linkedGoPro = linkedGoPro;
    }

    public void disconnect(Context context) {
        try {
            connectIQ.unregisterForApplicationEvents(this, iqApp);
            connectIQ.unregisterForDeviceEvents(this);
            connectIQ.shutdown(context);
        } catch (InvalidStateException e) {
            e.printStackTrace();
        }
    }
}
