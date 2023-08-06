package com.example.garmingopromobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.garmingopromobile.databinding.ActivityMainBinding;
import com.garmin.android.connectiq.ConnectIQ;
import com.garmin.android.connectiq.IQDevice;
import com.garmin.android.connectiq.exception.InvalidStateException;
import com.garmin.android.connectiq.exception.ServiceUnavailableException;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Set;


public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private BackgroundService backgroundService;

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
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
            System.out.println("Foreground Service not running");
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (waitForService) {
                    try {
                        synchronized (BackgroundService.synchronizer) {
                            BackgroundService.synchronizer.wait(5000);
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                backgroundService = BackgroundService.getInstance();
                System.out.println(backgroundService);

                TextLog.bind(findViewById(R.id.textView), findViewById(R.id.scrollView), MainActivity.this);

                Spinner goProSpinner = findViewById(R.id.gopro_spinner);
                ArrayList<GoPro> pairedGoPros = backgroundService.getPairedGoPros();
                ArrayAdapter<GoPro> goproAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, pairedGoPros);
                goproAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                Spinner garminSpinner = findViewById(R.id.garmin_spinner);
                ArrayList<IQDevice> pairedGarminDevices = backgroundService.getPairedWatches();
                ArrayAdapter<IQDevice> garminAdapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, pairedGarminDevices);
                garminAdapter.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));


                @SuppressLint("UseSwitchCompatOrMaterialCode") Switch backgroundSwitch = findViewById(R.id.backgroundSwitch);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextLog.activateUI();

                        goProSpinner.setAdapter(goproAdapter);
                        goProSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                                backgroundService.setGoPro((GoPro) adapterView.getSelectedItem());
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> adapterView) {
                            }
                        });

                        garminSpinner.setAdapter(garminAdapter);
                        garminSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                                try {
                                    IQDevice device = (IQDevice) adapterView.getSelectedItem();
                                    backgroundService.setWatch(device.getDeviceIdentifier(), device.getFriendlyName());
                                } catch (InvalidStateException | ServiceUnavailableException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> adapterView) {
                            }
                        });

                        backgroundSwitch.setChecked(getSharedPreferences("savedPrefs", MODE_PRIVATE).getBoolean("backgroundToggle", false));
                        backgroundSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                backgroundService.setBackgroundToggle(isChecked);
                            }
                        });
                    }
                });
            }
        }).start();
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
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}