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
    ConnectIQ connectIQ;
    GoPro linkedGoPro;
    IQApp iqApp;

    public enum Communication {
        COM_CONNECT,
        COM_FETCH_SETTINGS,
        COM_PUSH_SETTINGS,
        COM_FETCH_STATES,
        COM_PUSH_STATES,
        COM_SHUTTER,
        COM_HIGHLIGHT,
        COM_LOCKED
    }

    public GarminDevice(ConnectIQ connectIQ, long deviceId, String name) throws InvalidStateException, ServiceUnavailableException {
        super(deviceId, name);
        this.connectIQ = connectIQ;

        connectIQ.registerForDeviceEvents(this, new ConnectIQ.IQDeviceEventListener() {
            @Override
            public void onDeviceStatusChanged(IQDevice device, IQDevice.IQDeviceStatus newStatus) {
                switch (newStatus) {
                    case CONNECTED:
                        System.out.println("Device connected");
                        try {
                            registerForMessages();
                        } catch (InvalidStateException e) {
                            throw new RuntimeException(e);
                        } catch (ServiceUnavailableException e) {
                            throw new RuntimeException(e);
                        }
                        break;
                    case NOT_CONNECTED:
                        System.out.println("Device not connected");
                        break;
                    case NOT_PAIRED:
                        System.out.println("Device not paired");
                        break;
                    case UNKNOWN:
                        System.out.println("Device unknown");
                        break;
                }
            }
        });
    }

    private void registerForMessages() throws InvalidStateException, ServiceUnavailableException {
        connectIQ.getApplicationInfo("1c0c49b8-5abb-4e59-a679-e102757ec01e", this, new ConnectIQ.IQApplicationInfoListener() {
            @Override
            public void onApplicationInfoReceived(IQApp iqApp) {
                GarminDevice.this.iqApp = iqApp;

                try {
                    connectIQ.registerForAppEvents(GarminDevice.this, GarminDevice.this.iqApp, new ConnectIQ.IQApplicationEventListener() {
                        @Override
                        public void onMessageReceived(IQDevice iqDevice, IQApp iqApp, List<Object> list, ConnectIQ.IQMessageStatus iqMessageStatus) {
                            if (iqMessageStatus==ConnectIQ.IQMessageStatus.SUCCESS) {
                                try {
                                    onReceive(list);
                                } catch (Exception e) {
                                    System.out.println(e);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    System.out.println(e);
                }

            }

            @Override
            public void onApplicationNotInstalled(String s) {
                System.out.println("app not installed");;
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
                            System.out.println("message send failed"+iqMessageStatus);
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
        System.out.println(data);
        Communication type = Communication.values()[(int) ((List) data.get(0)).get(0)];
        switch (type) {
            case COM_CONNECT:
                System.out.println("GP_CONNECT: "+linkedGoPro.connect());
//                ArrayList<Object> msg = new ArrayList<>();
//                msg.add(Communication.COM_CONNECT.ordinal());
//                msg.add(0);
//                this.send(msg);
                break;
            case COM_FETCH_SETTINGS:
                break;
            case COM_PUSH_SETTINGS:
                break;
            case COM_FETCH_STATES:
                break;
            case COM_PUSH_STATES:
                break;
            case COM_SHUTTER:
                linkedGoPro.startRecording();
                System.out.println("shutter");
                break;
            case COM_HIGHLIGHT:
                break;
            case COM_LOCKED:
                break;
        }
    }

    public void setLinkedGoPro(GoPro linkedGoPro) {
        this.linkedGoPro = linkedGoPro;
    }

    public void disconnect(Context context) throws InvalidStateException {
        connectIQ.unregisterForApplicationEvents(this, iqApp);
        connectIQ.unregisterForDeviceEvents(this);
        connectIQ.shutdown(context);
    }
}
