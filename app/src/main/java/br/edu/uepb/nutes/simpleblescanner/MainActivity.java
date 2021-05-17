package br.edu.uepb.nutes.simpleblescanner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int REQUEST_ENABLE_LOCATION = 2;
    protected static final UUID CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    private TextView mResultTextView;
    private ProgressBar mProgresBar;
    private SimpleBleScanner mScanner;
    private List<String> mDevicesAdded;
    private BluetoothGatt mBluetoothGatt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.scan_fab);
        fab.setOnClickListener(this);

        mResultTextView = findViewById(R.id.result_scan_textview);
        mProgresBar = findViewById(R.id.progressBar);
        mDevicesAdded = new ArrayList<>();

        // Initialize scanner settings
        mScanner = new SimpleBleScanner.Builder()
                .addFilterServiceUuid(
                        "00001523-c2a2-bd96-044f-58f09944c3ad"
                )//panicb5 service
                .addScanPeriod(150000) // 15s
                .build();
    }


    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * Checks if you have permission to use.
     * Required bluetooth ble and location.
     *
     * @return boolean
     */
    private void checkPermissions() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            requestBluetoothEnable();
        }

        if (!hasLocationPermissions()) {
            requestLocationPermission();
        }
    }

    /**
     * Request Bluetooth permission
     */
    private void requestBluetoothEnable() {
        startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BLUETOOTH);
    }

    /**
     * Checks whether the location permission was given.
     *
     * @return boolean
     */
    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }

    /**
     * Request Location permission.
     */
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ENABLE_LOCATION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode != Activity.RESULT_OK) {
            requestBluetoothEnable();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scan_fab:
                if (mScanner != null) {
                    mScanner.stopScan();
                    mProgresBar.setVisibility(View.VISIBLE);
                    mScanner.startScan(mScanCallback);
                    mDevicesAdded.clear();
                    Toast.makeText(getApplicationContext(), "Scanning started", Toast.LENGTH_LONG).show();
                }
                break;

            default:
                break;
        }
    }

    public final SimpleScannerCallback mScanCallback = new SimpleScannerCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult scanResult) {
            BluetoothDevice device = scanResult.getDevice();
            Log.d("MainActivity", device.getName()!=null ? device.getName() : "name not known");
            if (device.getAddress() == null || mDevicesAdded.contains(device.getAddress())) return;


            mDevicesAdded.add(device.getAddress());
            mResultTextView.setText(String.valueOf(mResultTextView.getText())
                    .concat("\n\n")
                    .concat(device.getName() != null ? device.getName() : "Unnamed")
                    .concat(" | ")
                    .concat(device.getAddress()));
            mBluetoothGatt = device.connectGatt(getApplicationContext(), true, mGattCallback);
            Log.d("MainActivity", "Trying to create a new connection.");
            Log.d("MainActivity", String.valueOf(mBluetoothGatt.connect())+ " CONNECTED TO THE DEVICE");
        }

        @Override
        public void onBatchScanResults(List<ScanResult> scanResults) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onFinish() {
            Log.d("MainActivity", "onFinish()");
            Toast.makeText(getApplicationContext(), "Scanning finished", Toast.LENGTH_LONG).show();
            mProgresBar.setVisibility(View.GONE);

        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("MainActivity", "onScanFailed() " + errorCode);
        }
    };




    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                //bluetooth is connected so discover services
                mBluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mBluetoothGatt.disconnect();
                //Bluetooth is disconnected
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt.getServices();
                // services are discoverd

                BluetoothGattCharacteristic characteristic =
                        gatt.getService(UUID.fromString("00001523-c2a2-bd96-044f-58f09944c3ad"))
                                .getCharacteristic(UUID.fromString("00001524-c2a2-bd96-044f-58f09944c3ad"));


                Log.d("MainActivity", "setCharacteristicNotification");
                boolean enabled = gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
                descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
                boolean writeDescriptorSuccess = gatt.writeDescriptor(descriptor); //descriptor write
                Log.d("MainActivity", "writeDescriptorSuccess "+writeDescriptorSuccess);
            }


        }



        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("MainActivity", "oncharateristcsRead" + characteristic.toString());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d("MainActivity", "onCharacteristicChanged: "+ characteristic.toString());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

        }
    };
}
