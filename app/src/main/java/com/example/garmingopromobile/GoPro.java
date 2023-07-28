package com.example.garmingopromobile;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public class GoPro {

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
        _5K3,
        _4K,
        _2K7,
        _1080p
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

    public enum Region {
        NTSC,
        PAL
    }

    public enum Hypersmooth {
        OFF,
        ON,
        HIGH,
        BOOST,
        AUTO_BOOST,
        STANDARD
    }

    GarminDevice linkedWatch;

    BleService bleService;
    String bleAddress;
    String bleName;

    boolean recording = false;
    Region region;
    Hypersmooth hypersmooth;
    LinkedHashMap<Settings, Integer> settings;


    public GoPro(BluetoothDevice bluetoothDevice, Context appContext) {
        this.bleService = new BleService(bluetoothDevice, appContext, this);
        this.bleAddress = bluetoothDevice.getAddress();
        this.bleName = bluetoothDevice.getName();

        settings = new LinkedHashMap<>();
        region = Region.PAL;
        hypersmooth = null;

//        sets settings map order
        settings.put(Settings.RESOLUTION, null);
        settings.put(Settings.RATIO, null);
        settings.put(Settings.LENS, null);
        settings.put(Settings.FRAMERATE, null);
    }

    public boolean connect() {
        return bleService.connect();
    }

    public boolean disconnect() {
        return bleService.disconnect();
    }


    public void pressShutter() {
        // Send the start recording request to the GoPro
        byte[] request = new byte[]{(byte) 0x03, (byte) 0x01, (byte) 0x01, (byte) (recording ? 0x00 : 0x01)};
        recording = !recording;
        bleService.prepareRequest(COMMAND_REQUEST, request, COMMAND_RESPONSE);
    }

    public void pressHilight() {
        // Hilight current video
        byte[] request = new byte[]{(byte) 0x01, (byte) 0x18};
        bleService.prepareRequest(COMMAND_REQUEST, request, COMMAND_RESPONSE);

    }

    public void setSettings(byte[] updatedSettings) {
        int i = 0;
        int settingLength;
        byte[] setting;
        do {
            settingLength = updatedSettings[i+1];
            setting = Arrays.copyOfRange(updatedSettings, i+2, i+2+settingLength);

            switch (BleService.SettingID.get(updatedSettings[i])) {
                case RESOLUTION:
                    switch (setting[0]) {
                        case (byte) 1:
                            this.settings.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._16R9.ordinal());
                            break;
                        case (byte) 4:
                            this.settings.put(Settings.RESOLUTION, Resolutions._2K7.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._16R9.ordinal());
                            break;
                        case (byte) 6:
                            this.settings.put(Settings.RESOLUTION, Resolutions._2K7.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._4R3.ordinal());
                            break;
                        case (byte) 9:
                            this.settings.put(Settings.RESOLUTION, Resolutions._1080p.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._16R9.ordinal());
                            break;
                        case (byte) 18:
                            this.settings.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._4R3.ordinal());
                            break;
                        case (byte) 26:
                            this.settings.put(Settings.RESOLUTION, Resolutions._5K3.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._8R7.ordinal());
                            break;
                        case (byte) 27:
                            this.settings.put(Settings.RESOLUTION, Resolutions._5K3.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._4R3.ordinal());
                            break;
                        case (byte) 28:
                            this.settings.put(Settings.RESOLUTION, Resolutions._4K.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._8R7.ordinal());
                            break;
                        case (byte) 100:
                            this.settings.put(Settings.RESOLUTION, Resolutions._5K3.ordinal());
                            this.settings.put(Settings.RATIO, Ratios._16R9.ordinal());
                            break;
                    }
                break;
                case FRAMERATE:
                    this.settings.put(Settings.FRAMERATE, switch (setting[0]) {
                        case (byte) 0, (byte) 13    -> Framerates._240.ordinal();
                        case (byte) 1, (byte) 2     -> Framerates._120.ordinal();
                        case (byte) 5, (byte) 6     -> Framerates._60.ordinal();
                        case (byte) 8, (byte) 9     -> Framerates._30.ordinal();
                        case (byte) 10 -> Framerates._24.ordinal();
                        default -> 0xff;
                    });
                break;
                case LENS:
                    this.settings.put(Settings.LENS, switch (setting[0]) {
                        case (byte) 0               -> Lenses._LARGE.ordinal();
                        case (byte) 3               -> Lenses._SUPERVIEW.ordinal();
                        case (byte) 4               -> Lenses._LINEAR.ordinal();
                        case (byte) 9               -> Lenses._HYPERVIEW.ordinal();
                        case (byte) 10              -> Lenses._LINEARLOCK.ordinal();
                        default -> 0xff;
                    });
                break;
                case FLICKER:
                    this.region = switch (setting[0]) {
                        case (byte) 2 -> Region.NTSC;
                        case (byte) 3 -> Region.PAL;
                        default -> null;
                    };
                break;
                case HYPERSMOOTH:
                    try {
                        this.hypersmooth = Hypersmooth.values()[setting[0]];
                    } catch (Exception e) {
                        this.hypersmooth = null;
                        e.printStackTrace();
                    }
                break;
                default:
                    System.out.println("Unexpected setting ID");
            }

            i+=2+settingLength;
        } while (i<updatedSettings.length);


        for (int s : settings.values()) {
            if (s == 0xff) System.out.println("Wrong setting detected");
        }

        System.out.println("Sending settings to watch : "+settings);
        linkedWatch.send(GarminDevice.Communication.COM_FETCH_SETTINGS, new ArrayList<Integer>(settings.values()));
    }

    public void sendSettings(List<Integer> settings) {
//        settings format :
//        res, ratio, lens, framerate

        byte[] request;

//        Resolution and ratio
        request = new byte[]{(byte) 0x03, BleService.SettingID.RESOLUTION.getValue(), (byte) 0x01, (byte) 0xff};
        System.out.println("Setting following resolution :"+ Resolutions.values()[settings.get(Settings.RESOLUTION.ordinal())]);
        request[3] = switch (Resolutions.values()[settings.get(Settings.RESOLUTION.ordinal())]) {
            case _5K3 -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
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
            case _1080p -> switch (Ratios.values()[settings.get(Settings.RATIO.ordinal())]) {
                case _16R9 -> (byte) 0x09;
                default -> (byte) 0xff;
            };
        };
//        System.out.println("Sending request"+  String.format("%02X", request));
        bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);

//        Framerate
        request = new byte[]{(byte) 0x03, BleService.SettingID.FRAMERATE.getValue(), (byte) 0x01, (byte) 0xff};
        System.out.println("Setting following framerate :"+ Framerates.values()[settings.get(Settings.FRAMERATE.ordinal())]);
        request[3] = switch (Framerates.values()[settings.get(Settings.FRAMERATE.ordinal())]) {
            case _240 -> region.equals(Region.PAL) ? (byte) 0x0d : (byte) 0x00;
            case _120 -> region.equals(Region.PAL) ? (byte) 0x02 : (byte) 0x01;
            case _60 -> region.equals(Region.PAL) ? (byte) 0x06 : (byte) 0x05;
            case _30 -> region.equals(Region.PAL) ? (byte) 0x09 : (byte) 0x08;
            case _24 -> (byte) 0x0a;
        };
//        System.out.println("Sending request"+  String.format("%02X", request));
        bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);

//        Lens
        request = new byte[]{(byte) 0x03, BleService.SettingID.LENS.getValue(), (byte) 0x01, (byte) 0xff};
        System.out.println("Setting following lens :"+ Lenses.values()[settings.get(Settings.LENS.ordinal())]);
        request[3] = switch (Lenses.values()[settings.get(Settings.LENS.ordinal())]) {
            case _HYPERVIEW -> (byte) 0x09;
            case _SUPERVIEW -> (byte) 0x03;
            case _LARGE -> (byte) 0x00;
            case _LINEAR -> (byte) 0x04;
            case _LINEARLOCK -> (byte) 0x0a;
        };
//        System.out.println("Sending request"+  String.format("%02X", request));
        bleService.prepareRequest(SETTINGS_REQUEST, request, SETTINGS_RESPONSE);
    }

    public GarminDevice getLinkedWatch() {return this.linkedWatch;}

    public void setLinkedWatch(GarminDevice linkedWatch) {
        this.linkedWatch = linkedWatch;
    }

    public String getAddress() {
        return bleAddress;
    }

    @NonNull
    @Override
    public String toString() {
        return bleName;
    }
}
