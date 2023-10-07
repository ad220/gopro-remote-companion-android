package com.example.garmingopromobile;

import android.content.Context;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQApp;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import java.util.ArrayList;
import java.util.List;

public class GarminDevice extends IQDevice {
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
        TextLog.logInfo("New watch object created");
        this.connectIQ = connectIQ;

        connectIQ.setAdbPort(7381);

        connectIQ.registerForDeviceEvents(this, (device, newStatus) -> {
            switch (newStatus) {
                case CONNECTED -> {
                    TextLog.logInfo("Device connected");
                    try {
                        registerForMessages();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                case NOT_CONNECTED -> {
                    TextLog.logInfo("Device not connected");
                    if (iqApp != null) {
                        try {
                            connectIQ.unregisterForApplicationEvents(GarminDevice.this, iqApp);
                        } catch (InvalidStateException e) {
                            e.printStackTrace();
                        }
                    }
                }
                case NOT_PAIRED -> {
                    TextLog.logInfo("Device not paired");
                    try {
                        connectIQ.unregisterForDeviceEvents(GarminDevice.this);
                    } catch (InvalidStateException e) {
                        e.printStackTrace();
                    }
                }
                case UNKNOWN -> {
                    TextLog.logInfo("Device unknown");
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
                TextLog.logInfo("IQAPP STATUS: "+iqApp.getStatus());
                try {
                    connectIQ.registerForAppEvents(GarminDevice.this, GarminDevice.this.iqApp, new ConnectIQ.IQApplicationEventListener() {
                        @Override
                        public void onMessageReceived(IQDevice iqDevice, IQApp iqApp, List<Object> list, ConnectIQ.IQMessageStatus iqMessageStatus) {
                            TextLog.logInfo("message received");
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
                TextLog.logInfo("app not installed");
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
                            TextLog.logInfo("message send failed : "+iqMessageStatus);
                            TextLog.logInfo("message content : "+message);
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
        TextLog.logInfo(data);
        Communication type = Communication.values()[(int) ((List<?>) data.get(0)).get(0)];
        Object loadout = ((List<?>) data.get(0)).get(1);
        switch (type) {
            case COM_CONNECT:
                if ((Integer) loadout == 0) TextLog.logInfo("Watch app started, connecting to gopro : "+linkedGoPro.connect());
                else TextLog.logInfo("Watch app stopped, disconnecting gopro : "+linkedGoPro.disconnect());
//                ArrayList<Object> msg = new ArrayList<>();
//                msg.add(Communication.COM_CONNECT.ordinal());
//                msg.add(0);
//                this.send(msg);
                break;
            case COM_FETCH_SETTINGS:
                break;
            case COM_PUSH_SETTINGS:
                linkedGoPro.sendSettings((List<Integer>) loadout);
                TextLog.logInfo("push settings : "+loadout);
                break;
            case COM_FETCH_STATES:
                break;
            case COM_PUSH_STATES:
                break;
            case COM_SHUTTER:
                linkedGoPro.pressShutter();
                TextLog.logInfo("shutter");
                break;
            case COM_HIGHLIGHT:
                linkedGoPro.pressHilight();
                TextLog.logInfo("hilight");
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
