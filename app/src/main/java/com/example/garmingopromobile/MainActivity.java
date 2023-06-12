package com.example.garmingopromobile;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
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
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Set;


public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private DeviceInterface deviceInterface;
    private ConnectIQ connectIQ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);
        super.onCreate(savedInstanceState);
        deviceInterface = new DeviceInterface();


        refreshGarminSpinner(this);
        ArrayList<GoPro> pairedGoPros = getPairedGoPros();

        SharedPreferences pref = this.getSharedPreferences("setDevices", MODE_PRIVATE);
        try {
            deviceInterface.setWatch(new GarminDevice(connectIQ, pref.getLong("garminID", 0), pref.getString("garminName", "")));
            deviceInterface.setGoPro(searchGoProAddress(pairedGoPros, pref.getString("gopro", "")));
        } catch (Exception e) {
            e.printStackTrace();
        }


        Spinner goProSpinner = findViewById(R.id.gopro_spinner); // Récupère la référence du Spinner dans votre layout XML
        ArrayAdapter<GoPro> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, pairedGoPros);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        goProSpinner.setAdapter(adapter);
        goProSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                log gopro connected
                deviceInterface.setGoPro((GoPro) adapterView.getSelectedItem());
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("gopro", deviceInterface.getGoPro().getBluetoothDevice().getAddress());

            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        Spinner garminSpinner = findViewById(R.id.garmin_spinner);
        garminSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                try {
                    IQDevice device = (IQDevice) adapterView.getSelectedItem();
                    GarminDevice garminDevice = new GarminDevice(connectIQ, device.getDeviceIdentifier(), device.getFriendlyName());
                    deviceInterface.setWatch(garminDevice);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putLong("garminID", device.getDeviceIdentifier());
                    editor.putString("garminName", device.getFriendlyName());

                } catch (InvalidStateException | ServiceUnavailableException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

    }

    private ArrayList<GoPro> getPairedGoPros() {
        ArrayList<GoPro> pairedGoPros = new ArrayList<>();
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter != null) {
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();

            for (BluetoothDevice device : devices) {
                if (device.getName().contains("GoPro")) {
                    pairedGoPros.add(new GoPro(device, this));
                }
            }
        }

        return pairedGoPros;
    }

    private GoPro searchGoProAddress(ArrayList<GoPro> goProList, String address) {
        for (GoPro gp: goProList) {
            if (gp.getAddress().equals(address)) {
                return gp;
            }
        }
        return null;
    }

    private void refreshGarminSpinner(MainActivity parent) {
        connectIQ = ConnectIQ.getInstance(parent, ConnectIQ.IQConnectType.WIRELESS);
        connectIQ.initialize(getApplicationContext(), true, new ConnectIQ.ConnectIQListener() {
            @Override
            public void onSdkReady() {
                try {
                    ArrayList<IQDevice> pairedGarminDevices;
                    pairedGarminDevices = (ArrayList<IQDevice>) connectIQ.getKnownDevices();
                    System.out.println(pairedGarminDevices);

                    Spinner garminSpinner = findViewById(R.id.garmin_spinner);
                    ArrayAdapter<IQDevice> adapter = new ArrayAdapter<>(parent, android.R.layout.simple_spinner_item, pairedGarminDevices);
                    adapter.setDropDownViewResource((android.R.layout.simple_spinner_dropdown_item));

                    garminSpinner.setAdapter(adapter);
                } catch (InvalidStateException e) {
                    e.printStackTrace();
                } catch (ServiceUnavailableException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onInitializeError(ConnectIQ.IQSdkErrorStatus iqSdkErrorStatus) {
                System.out.println(iqSdkErrorStatus);
            }

            @Override
            public void onSdkShutDown() {
                System.out.println("iq sdk shutdown");
            }
        });
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