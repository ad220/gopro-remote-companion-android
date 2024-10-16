package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;

import java.util.ArrayList;
import java.util.Set;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    public static final Object serviceReadyLock = new Object();
    private boolean sdkReady;
    private final Object sdkReadyLock = new Object();
    private static BackgroundService instance;
    private SharedPreferences pref;
    private ConnectIQ connectIQ;
    private GoPro goPro;
    private GarminDevice watch;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        goPro.disconnect();
        watch.disconnect(getApplicationContext());
        stopSelf();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TextLog.deactivateUI();
        ServiceManager.setInstance(this);
        pref = getApplicationContext().getSharedPreferences("savedPrefs", MODE_PRIVATE);
        Log.v(TAG, "Service started");

        new Thread(this::initializeDevices).start();

        toggleBackground(pref.getBoolean("backgroundToggle", false));

        return super.onStartCommand(intent, flags, startId);
    }


    public void toggleBackground(boolean toggle) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("backgroundToggle", toggle);
        editor.apply();

        if (toggle) {
            final String CHANNELID = "Foreground Service ID";
            NotificationChannel channel = new NotificationChannel(
                    CHANNELID,
                    CHANNELID,
                    NotificationManager.IMPORTANCE_NONE
            );

            Intent stopIntent = new Intent(this, ServiceManager.class);
            stopIntent.setAction(ServiceManager.ACTION_SERVICE_STOP);
            PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNELID)
                    .setContentText("Garmin GoPro Mobile is running in the background")
                    .setContentTitle("Background service enabled")
                    .setSmallIcon(R.drawable.gopro_alpha_mini)
                    .addAction(R.drawable.icon_background, "Stop service", stopPendingIntent);

            startForeground(1001, notification.build());
        } else {
            stopForeground(true);
        }
    }


    private void initializeDevices() {
        ArrayList<GoPro> pairedGoPros = getPairedGoPros();

        sdkReady = false;

        new Handler(Looper.getMainLooper()).post(() -> {
            connectIQ = ConnectIQ.getInstance(BackgroundService.this, ConnectIQ.IQConnectType.TETHERED);
            connectIQ.initialize(getApplicationContext(), true, new ConnectIQ.ConnectIQListener() {
                @Override
                public void onSdkReady() {
                    synchronized (sdkReadyLock) {
                        sdkReady = true;
                        sdkReadyLock.notify();
                    }
                    Log.v(TAG, "Garmin SDK ready");
                }

                @Override
                public void onInitializeError(ConnectIQ.IQSdkErrorStatus iqSdkErrorStatus) {
                    synchronized (sdkReadyLock) {
                        sdkReady = false;
                        sdkReadyLock.notify();
                    }
                    TextLog.logWarn("Garmin SDK failed to initialize : "+iqSdkErrorStatus);
                }

                @Override
                public void onSdkShutDown() {
                    sdkReady = false;
                    TextLog.logWarn("Garmin SDK shut down");
                }
            });
        });

        try {
            synchronized (sdkReadyLock) {
                if (!sdkReady) sdkReadyLock.wait(2000);
                if (sdkReady) {
                    setWatch(pref.getLong("garminID", 0), pref.getString("garminName", ""));
                    setGoPro(searchGoProAddress(pairedGoPros, pref.getString("gopro", "")));
                } else {
                    TextLog.logError("Cannot initialize devices, Garmin SDK not ready");
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        synchronized (BackgroundService.serviceReadyLock) {
            instance = BackgroundService.this;
            BackgroundService.serviceReadyLock.notify();
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public GoPro getGoPro() {
        return goPro;
    }

    public void setGoPro(GoPro goPro) {
        this.goPro = goPro;
        this.goPro.setLinkedWatch(watch);
        if (this.watch != null)
            watch.setLinkedGoPro(goPro);

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

    public void setWatch(long watchID, String watchName) {
        GarminDevice watch;
        try {
            watch = new GarminDevice(connectIQ, watchID, watchName);
        } catch (InvalidStateException e) {
            Log.e(TAG, "Cannot create Garmin device, SDK must not be initialized yet", e);
            throw new RuntimeException(e);
        }
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
