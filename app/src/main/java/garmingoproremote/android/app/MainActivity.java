package garmingoproremote.android.app;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.garmin.android.connectiq.IQDevice;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.Objects;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private BackgroundService backgroundService;

    private Spinner goproSpinner;
    private Spinner garminSpinner;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch backgroundSwitch;
    private SwipeRefreshLayout swipeRefreshLayout;

    private static final Object swipeRefreshSynchronizer = new Object();

    @SuppressWarnings("deprecation") // getRunningServices is deprecated, but it's the only way to check if a service is running
    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        //noinspection deprecation
        for(ActivityManager.RunningServiceInfo service: activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if(BackgroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        super.onCreate(savedInstanceState);

        boolean waitForService = !foregroundServiceRunning();
        if (waitForService) {
            Intent serviceIntent = new Intent(this, BackgroundService.class);
            if (getSharedPreferences("savedPrefs", MODE_PRIVATE).getBoolean("backgroundToggle", false)) startForegroundService(serviceIntent);
            else startService(serviceIntent);
            Log.v(TAG, "MainActivity: Foreground Service not running");
        }

        new Thread(() -> initializeUI(waitForService)).start();
    }

    @Override
    protected void onDestroy() {
        TextLog.deactivateUI();
        super.onDestroy();
    }

    private void initializeUI(boolean waitForService) {
        if (waitForService) {
            try {
                Log.v(TAG, "Waiting for service");
                synchronized (BackgroundService.serviceReadyLock) {
                    BackgroundService.serviceReadyLock.wait(5000);
                }
                Log.v(TAG, "Service started, initializing UI");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        backgroundService = BackgroundService.getInstance();

        TextLog.bind(findViewById(R.id.textView), findViewById(R.id.scrollView), MainActivity.this);
        TextLog.activateUI();

        goproSpinner = findViewById(R.id.gopro_spinner);
        garminSpinner = findViewById(R.id.garmin_spinner);
        backgroundSwitch = findViewById(R.id.backgroundSwitch);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);

        runOnUiThread(() -> {
            goproSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    GoPro device = (GoPro) adapterView.getSelectedItem();
                    GoPro currentGoPro = backgroundService.getGoPro();
                    if (currentGoPro==null || !Objects.equals(device.getAddress(), currentGoPro.getAddress()) || !currentGoPro.isConnected())
                        backgroundService.setGoPro((GoPro) adapterView.getSelectedItem());
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });

            garminSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    IQDevice device = (IQDevice) adapterView.getSelectedItem();
                    GarminDevice currentWatch = backgroundService.getWatch();
                    if (currentWatch == null || device.getDeviceIdentifier() != currentWatch.getDeviceIdentifier() || !currentWatch.isConnected())
                        backgroundService.setWatch(device.getDeviceIdentifier(), device.getFriendlyName());
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });

            backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> backgroundService.toggleBackground(isChecked));

            swipeRefreshLayout.setOnRefreshListener(this::refreshUI);
            swipeRefreshLayout.setRefreshing(true);

            synchronized (swipeRefreshSynchronizer) {
                swipeRefreshSynchronizer.notify();
            }
        });

        synchronized (swipeRefreshSynchronizer) {
            try {
                swipeRefreshSynchronizer.wait();
                refreshUI();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
    }

    private void refreshUI() {
        ArrayList<GoPro> pairedGoPros = backgroundService.getPairedGoPros();
        int prefGoPro = -1;
        if (backgroundService.getGoPro() != null) {
            for (GoPro gopro : pairedGoPros) {
                if (gopro.getAddress().equals(backgroundService.getGoPro().getAddress())) {
                    prefGoPro = pairedGoPros.indexOf(gopro);
                    break;
                }
            }
        }
        ArrayAdapter<GoPro> goproAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, pairedGoPros);
        goproAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayList<IQDevice> pairedGarminDevices = backgroundService.getPairedWatches();
        int prefGarmin = -1;
        if (backgroundService.getWatch() != null) {
            for (IQDevice watch : pairedGarminDevices) {
                if (watch.getDeviceIdentifier() == backgroundService.getWatch().getDeviceIdentifier()) {
                    prefGarmin = pairedGarminDevices.indexOf(watch);
                    break;
                }
            }
        }
        ArrayAdapter<IQDevice> garminAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, pairedGarminDevices);
        garminAdapter.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));

        final int prefFinalGoPro = prefGoPro, prefFinalGarmin=prefGarmin;
        runOnUiThread(() -> {
            goproSpinner.setAdapter(goproAdapter);
            goproSpinner.setSelection(prefFinalGoPro);
            garminSpinner.setAdapter(garminAdapter);
            garminSpinner.setSelection(prefFinalGarmin);
            backgroundSwitch.setChecked(getSharedPreferences("savedPrefs", MODE_PRIVATE).getBoolean("backgroundToggle", false));
        });
        swipeRefreshLayout.setRefreshing(false);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return super.onSupportNavigateUp();
    }
}