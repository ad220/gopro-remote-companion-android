package com.example.garmingopromobile;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class GoPro {
    private static final String TAG = "GoPro";

    //    GP-XXXX is shorthand for GoPro's 128-bit UUIDs: b5f9xxxx-aa8d-11e3-9046-0002a5d5c51b
    public static final UUID COMMAND_REQUEST = UUID.fromString("b5f90072-aa8d-11e3-9046-0002a5d5c51b");
    public static final UUID COMMAND_RESPONSE = UUID.fromString("b5f90073-aa8d-11e3-9046-0002a5d5c51b");
    public static final UUID SETTINGS_REQUEST = UUID.fromString("b5f90074-aa8d-11e3-9046-0002a5d5c51b");
    public static final UUID SETTINGS_RESPONSE = UUID.fromString("b5f90075-aa8d-11e3-9046-0002a5d5c51b");
    public static final UUID QUERY_REQUEST = UUID.fromString("b5f90076-aa8d-11e3-9046-0002a5d5c51b");
    public static final UUID QUERY_RESPONSE = UUID.fromString("b5f90077-aa8d-11e3-9046-0002a5d5c51b");

    public enum Settings {
        RESOLUTION,
        RATIO,
        LENS,
        FRAMERATE
    }

    public enum Resolutions {
        _5K,
        _4K,
        _2K7,
        _1440,
        _1080
    }

    public enum Ratios {
        _8R7,
        _4R3,
        _16R9
    }

    public enum Lenses {
        _HYPERVIEW,
        _SUPERVIEW,
        _LARGE,
        _LINEAR,
        _LINEARLOCK
    }

    public enum Framerates {
        _240,
        _120,
        _60,
        _30,
        _24
    }


    public enum Hypersmooth {
        OFF,
        ON,
        HIGH,
        BOOST,
        AUTO_BOOST,
        STANDARD
    }

    public enum States {
        REGION,
        RECORDING
    }

    public enum Region {
        NTSC,
        PAL
    }

    GarminDevice linkedWatch;
    BleService bleService;
    String bleAddress;
    String bleName;

    boolean recording = false;
    Hypersmooth hypersmooth;
    LinkedHashMap<Settings, Integer> settings;
    LinkedHashMap<States, Integer> states;
    Map<Settings, List<Integer>> availableSettings;
    Map<Settings, Set<Integer>> tmpAvailableSettings;
    Map<Resolutions, Set<Ratios>> availableRatios;

    private interface MessageProcessor {
        void processTuple(byte type, byte[] value);
        void processResult();
    }


    public GoPro(BluetoothDevice bluetoothDevice, Context appContext) {
        this.bleService = new BleService(bluetoothDevice, appContext, this);
        this.bleAddress = bluetoothDevice.getAddress();
        this.bleName = bluetoothDevice.getName();

        settings = new LinkedHashMap<>();
        states = new LinkedHashMap<>();
        hypersmooth = null;
        availableSettings = new LinkedHashMap<>();
        availableRatios = new HashMap<>();

//        sets settings map order
        for (Settings s : Settings.values()) {
            settings.put(s, null);
            availableSettings.put(s, new ArrayList<>());
        }

//        sets states map order
        for (States s : States.values()) {
            states.put(s, null);
        }
    }

    public boolean connect() {
        return bleService.connect();
    }

    public boolean disconnect() {
        return bleService.disconnect();
    }

    public boolean isConnected() {
        return bleService.isConnected();
    }
    public void pressShutter() {
        // Send the start recording request to the GoPro
        byte[] request = new byte[]{(byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) (isRecording() ? 0x00 : 0x01)};
        bleService.prepareRequest(COMMAND_REQUEST, request, COMMAND_RESPONSE);
    }

    public void pressHilight() {
        // Hilight current video
        byte[] request = new byte[]{(byte) 0x01, (byte) 0x18};
        bleService.prepareRequest(COMMAND_REQUEST, request, COMMAND_RESPONSE);

    }

    public void readTLVMessage(byte[] message) {
        byte[] data = Arrays.copyOfRange(message, 2, message.length);
        BleService.QueryID queryID = BleService.QueryID.get(message[0]);
        byte status = message[1];

        // Initializes loop
        int i = 0;
        byte type;
        int length;
        byte[] value;

        MessageProcessor messageProcessor;

        if (status != 0) {
            TextLog.logWarn("Wrong query status result from camera");
        }

        switch (queryID) {
            case REGISTER_SETTINGS, NOTIF_SETTINGS -> {
                messageProcessor = new MessageProcessor() {
                    @Override
                    public void processTuple(byte type, byte[] value) {
                        Map<Settings, Integer> settingTuples = decodeSetting(type, value);
                        for (Settings key: settingTuples.keySet()) {
                            if (settingTuples.get(key) != 0xff) settings.put(key, settingTuples.get(key));
                            else Log.w(TAG, "Wrong setting detected");
                        }
                    }

                    @Override
                    public void processResult() {
                        syncSettings();
                        syncStates();
                    }
                };
            }
            case REGISTER_STATUS, NOTIF_STATUS, GET_STATUS -> {
                messageProcessor = new MessageProcessor() {
                    @Override
                    public void processTuple(byte type, byte[] value) {
                        decodeStatus(type, value);
                    }

                    @Override
                    public void processResult() {
                        syncStates();
                    }
                };
            }
            case REGISTER_AVAILABLE, NOTIF_AVAILABLE -> {
                tmpAvailableSettings = new LinkedHashMap<>();
                tmpAvailableSettings.put(Settings.RESOLUTION, new HashSet<>());
                tmpAvailableSettings.put(Settings.RATIO, new HashSet<>());
                tmpAvailableSettings.put(Settings.LENS, new HashSet<>());
                tmpAvailableSettings.put(Settings.FRAMERATE, new HashSet<>());
                messageProcessor = new MessageProcessor() {
                    @Override
                    public void processTuple(byte type, byte[] value) {
                        Map<Settings, Integer> settingTuples = decodeSetting(type, value);
                        for (Settings key: settingTuples.keySet()) {
                            if (type == BleService.SettingID.RESOLUTION.getValue()) {
                                Integer res = settingTuples.get(Settings.RESOLUTION);
                                Integer ratio = settingTuples.get(Settings.RATIO);
                                if (res.equals(GoPro.this.settings.get(Settings.RESOLUTION))) {
                                    Objects.requireNonNull(tmpAvailableSettings.get(Settings.RESOLUTION)).add(res);
                                    Objects.requireNonNull(tmpAvailableSettings.get(Settings.RATIO)).add(ratio);
                                } else {
                                    if (res != 0xff) Objects.requireNonNull(tmpAvailableSettings.get(Settings.RESOLUTION)).add(res);
                                }
                                if (!GoPro.this.availableRatios.containsKey(Resolutions.values()[res]))
                                    GoPro.this.availableRatios.put(Resolutions.values()[res], new HashSet<>());
                                GoPro.this.availableRatios.get(Resolutions.values()[res]).add(Ratios.values()[ratio]);
                            } else {
                                if (settingTuples.get(key) != 0xff) Objects.requireNonNull(GoPro.this.tmpAvailableSettings.get(key)).add(settingTuples.get(key));
                                else Log.w(TAG, "Wrong setting detected");
                            }
                        }
                    }

                    @Override
                    public void processResult() {
                        syncAvailable();
                    }
                };
            }
            default -> {
                TextLog.logWarn("Unexpected query ID");
                return;
            }
        }

        // Decode settings in received message
        do {
            type = data[i];
            length = data[i+1];
            value = Arrays.copyOfRange(data, i+2, i+2+length);

            messageProcessor.processTuple(type, value);

            i+=2+length;
        } while (i<data.length);

        messageProcessor.processResult();
    }

    private void syncSettings() {
        if (settings.containsValue(0xff)) TextLog.logWarn("Wrong setting detected, not sending to watch");
        else {
            TextLog.logInfo("Sending settings to watch...");
            Log.v(TAG, "Settings values :"+settings);
            linkedWatch.send(GarminDevice.Communication.COM_FETCH_SETTINGS, new ArrayList<Integer>(settings.values()));
        }
    }

    private void syncStates() {
        if (states.containsValue(0xff)) TextLog.logWarn("Wrong state detected, not sending to watch");
        else {
            TextLog.logInfo("Sending states to watch...");
            Log.v(TAG, "States values :"+states);
            linkedWatch.send(GarminDevice.Communication.COM_FETCH_STATES, new ArrayList<Integer>(states.values()));
        }
    }

    private void syncAvailable() {
        List<List<Integer>> availableSettingsArray = new ArrayList<>();
        List<Integer> tmpSettingList;
        Set<Integer> tmpSettingSet;
        for (Settings settingId : Settings.values()) {
            tmpSettingSet = tmpAvailableSettings.get(settingId);
            if (tmpSettingSet != null && !tmpSettingSet.isEmpty()) {
                tmpSettingList = new ArrayList<>(tmpSettingSet);
                Collections.sort(tmpSettingList);
                availableSettings.put(settingId, tmpSettingList);
                availableSettingsArray.add(tmpSettingList);
            } else {
                if (settingId == Settings.RATIO) {
                    List<Ratios> ratioList = new ArrayList<>(Objects.requireNonNull(availableRatios.get(Resolutions.values()[settings.get(Settings.RESOLUTION)])));
                    Collections.sort(ratioList);
                    ArrayList<Integer> intRatioList = new ArrayList<>();
                    for (Ratios r : ratioList) intRatioList.add(r.ordinal());
                    availableSettingsArray.add(intRatioList);
                    availableSettings.put(Settings.RATIO, intRatioList);
                }
                else
                    availableSettingsArray.add(new ArrayList<>(Objects.requireNonNull(availableSettings.get(settingId))));
            }
        }
        Log.v(TAG, "Available ratios: "+availableRatios);
        TextLog.logInfo("Sending available settings to watch...");
        Log.v(TAG, "Available settings values :" + availableSettingsArray);
        linkedWatch.send(GarminDevice.Communication.COM_FETCH_AVAILABLE, availableSettingsArray);
    }

    private Map<Settings, Integer> decodeSetting(byte setting, byte[] value) {
        Map<Settings, Integer> result = new LinkedHashMap<Settings, Integer>();

        if (!BleService.SettingID.getAllValues().contains(setting)) {
            Log.w(TAG, "Unexpected camera setting ID : %x --> %x\n" + setting + " --> " + value[0]);
            return result;
        }
        switch (Objects.requireNonNull(BleService.SettingID.get(setting))) {
            case RESOLUTION:
                switch (value[0]) {
                    case (byte) 1 -> {
                        result.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                        result.put(Settings.RATIO, Ratios._16R9.ordinal());
                    }
                    case (byte) 4 -> {
                        result.put(Settings.RESOLUTION, Resolutions._2K7.ordinal());
                        result.put(Settings.RATIO, Ratios._16R9.ordinal());
                    }
                    case (byte) 6 -> {
                        result.put(Settings.RESOLUTION, Resolutions._2K7.ordinal());
                        result.put(Settings.RATIO, Ratios._4R3.ordinal());
                    }
                    case (byte) 9 -> {
                        result.put(Settings.RESOLUTION, Resolutions._1080.ordinal());
                        result.put(Settings.RATIO, Ratios._16R9.ordinal());
                    }
                    case (byte) 18 -> {
                        result.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                        result.put(Settings.RATIO, Ratios._4R3.ordinal());
                    }
                    case (byte) 26 -> {
                        result.put(Settings.RESOLUTION, Resolutions._5K.ordinal());
                        result.put(Settings.RATIO, Ratios._8R7.ordinal());
                    }
                    case (byte) 27 -> {
                        result.put(Settings.RESOLUTION, Resolutions._5K.ordinal());
                        result.put(Settings.RATIO, Ratios._4R3.ordinal());
                    }
                    case (byte) 28 -> {
                        result.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                        result.put(Settings.RATIO, Ratios._8R7.ordinal());
                    }
                    case (byte) 100 -> {
                        result.put(Settings.RESOLUTION, Resolutions._5K.ordinal());
                        result.put(Settings.RATIO, Ratios._16R9.ordinal());
                    }
                    default -> {
                        result.put(Settings.RESOLUTION, 0xff);
                        result.put(Settings.RATIO, 0xff);
                    }
                }
                break;
            case FRAMERATE:
                result.put(Settings.FRAMERATE, switch (value[0]) {
                    case (byte) 0, (byte) 13    -> Framerates._240.ordinal();
                    case (byte) 1, (byte) 2     -> Framerates._120.ordinal();
                    case (byte) 5, (byte) 6     -> Framerates._60.ordinal();
                    case (byte) 8, (byte) 9     -> Framerates._30.ordinal();
                    case (byte) 10 -> Framerates._24.ordinal();
                    default -> 0xff;
                });
                break;
            case LENS:
                result.put(Settings.LENS, switch (value[0]) {
                    case (byte) 0               -> Lenses._LARGE.ordinal();
                    case (byte) 3               -> Lenses._SUPERVIEW.ordinal();
                    case (byte) 4               -> Lenses._LINEAR.ordinal();
                    case (byte) 9               -> Lenses._HYPERVIEW.ordinal();
                    case (byte) 10              -> Lenses._LINEARLOCK.ordinal();
                    default -> 0xff;
                });
                break;
            case FLICKER:
                this.states.put(States.REGION, switch (value[0]) {
                    case (byte) 2               -> Region.NTSC.ordinal();
                    case (byte) 3               -> Region.PAL.ordinal();
                    default -> 0xff;
                });
                break;
            case HYPERSMOOTH:
                try {
                    this.hypersmooth = Hypersmooth.values()[value[0]];
                } catch (Exception e) {
                    this.hypersmooth = null;
                    e.printStackTrace();
                }
                break;
            default:
                TextLog.logWarn("Unexpected setting ID");
        }
        return result;
    }

    public void sendSettings(List<Integer> settings) {
//        settings format :
//        res, ratio, lens, framerate
        byte[] request;

        Resolutions resolution = Resolutions.values()[settings.get(Settings.RESOLUTION.ordinal())];
        Ratios ratio = Ratios.values()[settings.get(Settings.RATIO.ordinal())];
        Lenses lens = Lenses.values()[settings.get(Settings.LENS.ordinal())];
        Framerates framerate = Framerates.values()[settings.get(Settings.FRAMERATE.ordinal())];

//        Resolution and ratio
        if (!this.settings.get(Settings.RESOLUTION).equals(resolution.ordinal()) || !this.settings.get(Settings.RATIO).equals(ratio.ordinal())) {
            request = new byte[]{(byte) 0x03, BleService.SettingID.RESOLUTION.getValue(), (byte) 0x01, (byte) 0xff};
            if (!availableRatios.get(resolution).contains(ratio)) {
                Log.w(TAG, "Requested resolution/ratio couple not available, resetting ratio");
                settings.set(Settings.RATIO.ordinal(), Objects.requireNonNull(availableRatios.get(resolution)).iterator().next().ordinal());
            }
            Log.v(TAG, "Setting following resolution :"+ Resolutions.values()[settings.get(Settings.RESOLUTION.ordinal())]);
            request[3] = switch (Resolutions.values()[settings.get(Settings.RESOLUTION.ordinal())]) {
                case _5K -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
                    case _8R7 -> (byte) 0x1a;
                    case _4R3 -> (byte) 0x1b;
                    case _16R9 -> (byte) 0x64;
                };
                case _4K -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
                    case _8R7 -> (byte) 0x1c;
                    case _4R3 -> (byte) 0x12;
                    case _16R9 -> (byte) 0x01;
                };
                case _2K7 -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
                    case _4R3 -> (byte) 0x06;
                    case _16R9 -> (byte) 0x04;
                    default -> (byte) 0xff;
                };
                case _1440 -> (byte) 0xff;
                case _1080 -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
                    case _16R9 -> (byte) 0x09;
                    default -> (byte) 0xff;
                };
            };
            bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);
        } else Log.v(TAG, "Resolution and ratio remained the same, no request needed");

//        Framerate
        if (!this.settings.get(Settings.FRAMERATE).equals(settings.get(Settings.FRAMERATE.ordinal()))) {
            request = new byte[]{(byte) 0x03, BleService.SettingID.FRAMERATE.getValue(), (byte) 0x01, (byte) 0xff};
            Log.v(TAG, "Setting following framerate :"+ Framerates.values()[settings.get(Settings.FRAMERATE.ordinal())]);
            request[3] = switch (Framerates.values()[settings.get(Settings.FRAMERATE.ordinal())]) {
                case _240 -> isRegionPAL() ? (byte) 0x0d : (byte) 0x00;
                case _120 -> isRegionPAL() ? (byte) 0x02 : (byte) 0x01;
                case _60 -> isRegionPAL() ? (byte) 0x06 : (byte) 0x05;
                case _30 -> isRegionPAL() ? (byte) 0x09 : (byte) 0x08;
                case _24 -> (byte) 0x0a;
            };
            bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);
        } else Log.v(TAG, "Framerate remained the same, no request needed");

//        Lens
        if (!this.settings.get(Settings.LENS).equals(settings.get(Settings.LENS.ordinal()))) {
            request = new byte[]{(byte) 0x03, BleService.SettingID.LENS.getValue(), (byte) 0x01, (byte) 0xff};
            Log.v(TAG, "Setting following lens :"+ Lenses.values()[settings.get(Settings.LENS.ordinal())]);
            request[3] = switch (Lenses.values()[settings.get(Settings.LENS.ordinal())]) {
                case _HYPERVIEW -> (byte) 0x09;
                case _SUPERVIEW -> (byte) 0x03;
                case _LARGE -> (byte) 0x00;
                case _LINEAR -> (byte) 0x04;
                case _LINEARLOCK -> (byte) 0x0a;
            };
            bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);
        } else Log.v(TAG, "Lens remained the same, no request needed");
    }

    public void decodeStatus(byte status, byte[] value) {
        if (!BleService.StatusID.getAllValues().contains(status)) {
            Log.w(TAG, "Unexpected camera status ID : %x --> %x\n" + status + " --> " + value[0]);
            return;
        }
        switch (Objects.requireNonNull(BleService.StatusID.get(status))) {
            case ENCODING:
                this.states.put(States.RECORDING, (int) value[0]);
                if (isRecording()) bleService.prepareRequest(QUERY_REQUEST, new byte[]{(byte) 0x02, BleService.QueryID.GET_STATUS.getValue(), BleService.StatusID.PROGRESS.getValue()}, QUERY_RESPONSE);
                break;
            case PROGRESS:
                int progress = 0;
                for (byte b : value) {
                    progress = (progress << 8) + b;
                }
                linkedWatch.send(GarminDevice.Communication.COM_PROGRESS, progress);
                break;
            default:
                TextLog.logWarn("Unexpected status ID");
        }
    }

    public GarminDevice getLinkedWatch() {return this.linkedWatch;}

    public void setLinkedWatch(GarminDevice linkedWatch) {
        this.linkedWatch = linkedWatch;
    }

    public String getAddress() {
        return bleAddress;
    }

    public boolean isRecording() {return states.get(States.RECORDING) == 1;}

    @NonNull
    @Override
    public String toString() {
        return bleName;
    }

    private boolean isRegionPAL() {
        return states.get(States.REGION).equals(Region.PAL.ordinal());
    }
}
