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
    private final ConnectIQ connectIQ;
    private GoPro linkedGoPro;
    private IQApp iqApp;

    private boolean connected = false;

    public enum Communication {
        COM_CONNECT,
        COM_FETCH_SETTINGS,
        COM_PUSH_SETTINGS,
        COM_FETCH_STATES,
        COM_PUSH_STATES,
        COM_FETCH_AVAILABLE,
        COM_PUSH_AVAILABLE,
        COM_SHUTTER,
        COM_HIGHLIGHT,
        COM_LOCKED,
        COM_PROGRESS
    }

    public GarminDevice(ConnectIQ connectIQ, long deviceId, String name) throws InvalidStateException {
        super(deviceId, name);
        this.connectIQ = connectIQ;

        connectIQ.registerForDeviceEvents(this, (device, newStatus) -> {
            switch (newStatus) {
                case CONNECTED -> {
                    TextLog.logInfo("Garmin device connected");
                    connected = true;
                    try {
                        registerForMessages();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case NOT_CONNECTED -> {
                    TextLog.logInfo("Garmin device not connected");
                    connected = false;
                    if (linkedGoPro != null && linkedGoPro.isConnected()) linkedGoPro.disconnect();
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
                    connected = false;
                    try {
                        connectIQ.unregisterForDeviceEvents(GarminDevice.this);
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                    }
                }
                case UNKNOWN -> {
                    TextLog.logWarn("Garmin device unknown");
                    connected = false;
                    try {
                        connectIQ.unregisterForDeviceEvents(GarminDevice.this);
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public boolean isConnected() {
        return connected;
    }

    private void registerForMessages() throws InvalidStateException, ServiceUnavailableException {
        connectIQ.getApplicationInfo("ede98b81-5b9e-4b9b-b14a-3b46b8185ade", this, new ConnectIQ.IQApplicationInfoListener() { // old uuid 1c0c49b8-5abb-4e59-a679-e102757ec01e
            @Override
            public void onApplicationInfoReceived(IQApp iqApp) {
                TextLog.logInfo("ConnectIQ GoPro Remote widget status : "+iqApp.getStatus());
                // next line is a temporary fix because of the following garmin sdk issue
                // https://forums.garmin.com/developer/connect-iq/f/discussion/379720/android-mobile-sdk---tethered-connection---app-status-unknown/
                GarminDevice.this.iqApp = iqApp.getStatus() == IQApp.IQAppStatus.INSTALLED ? iqApp : new IQApp("");
                try {
                    connectIQ.registerForAppEvents(GarminDevice.this, GarminDevice.this.iqApp, (iqDevice, iqApp1, list, iqMessageStatus) -> {
                        Log.v(TAG, "Message received from watch");
                        if (iqMessageStatus==ConnectIQ.IQMessageStatus.SUCCESS) {
                            try {
                                onReceive(list);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "Failed to register for messages");
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
            final List<Object> message;

            ThreadedSend(List<Object> message) {
                this.message = message;
            }

            public void run() {
                try {
                    connectIQ.sendMessage(device, iqApp, message, (iqDevice, iqApp, iqMessageStatus) -> {
                        // second test in the next line is a temporary fix because of the following garmin sdk issue
                        // https://forums.garmin.com/developer/connect-iq/f/discussion/379720/android-mobile-sdk---tethered-connection---app-status-unknown/
                        if (iqMessageStatus != ConnectIQ.IQMessageStatus.SUCCESS && iqApp.getStatus() == IQApp.IQAppStatus.INSTALLED) {
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


    public void onReceive(List<Object> data) {
        Log.v(TAG, "Data received from watch : "+data);
        Communication type = Communication.values()[(int) ((List<?>) data.get(0)).get(0)];
        Object loadout = ((List<?>) data.get(0)).get(1);
        switch (type) {
            case COM_CONNECT -> {
                if ((Integer) loadout == 0)
                    TextLog.logInfo("Watch app started, connecting to gopro : " + linkedGoPro.connect());
                else
                    TextLog.logInfo("Watch app stopped, disconnecting gopro : " + linkedGoPro.disconnect());
            }
            case COM_PUSH_SETTINGS -> {
                linkedGoPro.sendSettings((List<Integer>) loadout);
                TextLog.logInfo("Watch sending settings to GoPro..." + loadout);
            }
            case COM_SHUTTER -> {
                linkedGoPro.pressShutter();
                TextLog.logInfo("Watch sending shutter command to GoPro...");
            }
            case COM_HIGHLIGHT -> {
                linkedGoPro.pressHilight();
                TextLog.logInfo("Watch sending highlight command to GoPro...");
            }
            default -> {
            }
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
            connected = false;
        } catch (InvalidStateException e) {
            e.printStackTrace();
        }
    }
}
