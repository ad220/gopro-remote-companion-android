package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.Looper;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class BackgroundService extends Service {
    public static Object synchronizer = new Object();
    private static BackgroundService instance;
    private SharedPreferences pref;
    private ConnectIQ connectIQ;
    private GoPro goPro;
    private GarminDevice watch;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TextLog.deactivateUI();
        pref = getApplicationContext().getSharedPreferences("savedPrefs", MODE_PRIVATE);
        System.out.println("Service started");
        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<GoPro> pairedGoPros = getPairedGoPros();

                        Looper.prepare();
                        connectIQ = ConnectIQ.getInstance(BackgroundService.this, ConnectIQ.IQConnectType.WIRELESS);
                        connectIQ.initialize(getApplicationContext(), true, new ConnectIQ.ConnectIQListener() {
                            @Override
                            public void onSdkReady() {
                                try {
                                    ArrayList<IQDevice> pairedGarminDevices;
                                    pairedGarminDevices = (ArrayList<IQDevice>) connectIQ.getKnownDevices();
                                    TextLog.logInfo(pairedGarminDevices);
                                    // TODO: start connection ?
                                } catch (InvalidStateException | ServiceUnavailableException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onInitializeError(ConnectIQ.IQSdkErrorStatus iqSdkErrorStatus) {
                                TextLog.logInfo(iqSdkErrorStatus);
                            }

                            @Override
                            public void onSdkShutDown() {
                                TextLog.logInfo("iq sdk shutdown");
                            }
                        });

                        try {
                            setWatch(pref.getLong("garminID", 0), pref.getString("garminName", ""));
                            setGoPro(searchGoProAddress(pairedGoPros, pref.getString("gopro", "")));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }


                        synchronized (BackgroundService.synchronizer) {
                            instance = BackgroundService.this;
                            System.out.println(getInstance());
                            BackgroundService.synchronizer.notify();
                        }
                    }
                }
        ).start();

        if (pref.getBoolean("backgroundToggle", false)) {
            final String CHANNELID = "Foreground Service ID";
            NotificationChannel channel = new NotificationChannel(
                    CHANNELID,
                    CHANNELID,
                    NotificationManager.IMPORTANCE_NONE
            );

            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification.Builder notification = new Notification.Builder(this, CHANNELID)
                    .setContentText("Service is running")
                    .setContentTitle("Service enabled")
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .addAction(new NotificationCompat.Action());

            startForeground(1001, notification.build());
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void setBackgroundToggle(boolean toggle) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("backgroundToggle", toggle);
        editor.apply();
    }


    public GoPro getGoPro() {
        return goPro;
    }

    public void setGoPro(GoPro goPro) {
        this.goPro = goPro;
        this.goPro.setLinkedWatch(watch);
        if (this.watch != null) {
            watch.setLinkedGoPro(goPro);
        }

        SharedPreferences.Editor editor = pref.edit();
        editor.putString("gopro", goPro.getAddress());
        editor.apply();
    }

    private GoPro searchGoProAddress(ArrayList<GoPro> goProList, String address) {
        for (GoPro gp : goProList) {
            if (gp.getAddress().equals(address)) {
                return gp;
            }
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public ArrayList<GoPro> getPairedGoPros() {
        ArrayList<GoPro> pairedGoPros = new ArrayList<>();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) { Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                if (device.getName().contains("GoPro")) {
                    pairedGoPros.add(new GoPro(device, this));
                }
            }
        }

        return pairedGoPros;
    }


    public GarminDevice getWatch() {
        return watch;
    }

    public void setWatch(long watchID, String watchName) throws InvalidStateException, ServiceUnavailableException {
        GarminDevice watch = new GarminDevice(connectIQ, watchID, watchName);
        this.watch = watch;
        this.watch.setLinkedGoPro(goPro);
        if (this.goPro != null) {
            goPro.setLinkedWatch(watch);
        }

        SharedPreferences.Editor editor = pref.edit();
        editor.putLong("garminID", watch.getDeviceIdentifier());
        editor.putString("garminName", watch.getFriendlyName());
        editor.apply();
    }

    public ArrayList<IQDevice> getPairedWatches() {
        try {
            return (ArrayList<IQDevice>) connectIQ.getKnownDevices();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static BackgroundService getInstance() {
        return instance;
    }
}
