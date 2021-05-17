package br.edu.uepb.nutes.simpleblescanner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.UUID;

import br.edu.uepb.nutes.simpleblescanner.database.DBManager;

import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static br.edu.uepb.nutes.simpleblescanner.MainActivity.CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID;

public class GattService extends Service {

    // https://stackoverflow.com/questions/30525784/android-keep-service-running-when-app-is-killed
    private BluetoothGatt mBluetoothGatt;
    private BluetoothAdapter mBluetoothAdapter;
    private DBManager dbManager;


    @Override
    public void onCreate() {
        super.onCreate();
        dbManager = new DBManager(this);
        dbManager.open();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
            startMyOwnForeground();
        else
            startForeground(1, new Notification());
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "example.permanence";
        String channelName = "Background Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("App is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        BluetoothDevice device = intent.getParcelableExtra("device");
        if (device != null) {
            mBluetoothGatt = device.connectGatt(getApplicationContext(), true, mGattCallback);
        } else {
            Cursor cursor = dbManager.fetch();
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                String data = cursor.getString(cursor.getColumnIndex("address"));
                BluetoothDevice deviceFromDatabase = mBluetoothAdapter.getRemoteDevice(data);
                deviceFromDatabase.connectGatt(getApplicationContext(), true, mGattCallback);
            }

        }
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
//        stoptimertask();

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (newState == STATE_CONNECTED) {
                Log.d("GattService", "Device connected");
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        boolean ans = mBluetoothGatt.discoverServices();
                        Log.d("GattService", "Discover Services started: " + ans);
                    }
                });
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


                Log.d("GattService", "setCharacteristicNotification");
                boolean enabled = gatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID);
                descriptor.setValue(enabled ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x00, 0x00});
                boolean writeDescriptorSuccess = gatt.writeDescriptor(descriptor); //descriptor write
                Log.d("GattService", "writeDescriptorSuccess " + writeDescriptorSuccess);
            }


        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("GattService", "oncharateristcsRead" + characteristic.toString());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.d("GattService", "onCharacteristicChanged: " + characteristic.toString());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

        }
    };


}
