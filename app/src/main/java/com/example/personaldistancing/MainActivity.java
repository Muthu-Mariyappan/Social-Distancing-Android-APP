package com.example.personaldistancing;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private final int PERMISSION_ID = 44;
    private final int allowedThreshold = -60;

    private BluetoothAdapter BTAdapter;
    private TextView textView;
    private CheckBox checkBox;
    private boolean enableTextNotification = false;
    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private Vibrator v;

    private NotificationManager mNotificationManager;
    private Notification notification;
    private ScheduledFuture<?> future;

    private void setupBluetooth(){
        BTAdapter = BluetoothAdapter.getDefaultAdapter();
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    private void setupNotificationManager(){
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(getApplicationContext(), "notify_001");

        NotificationCompat.BigTextStyle bigText = new NotificationCompat.BigTextStyle();
        bigText.bigText("");
        bigText.setBigContentTitle("Close Proximity Alert");
        bigText.setSummaryText("");

        mBuilder.setSmallIcon(R.mipmap.ic_launcher_round);
        mBuilder.setContentTitle("Your Title");
        mBuilder.setContentText("Please Keep a safe distance!");
        mBuilder.setPriority(Notification.PRIORITY_MAX);
        mBuilder.setStyle(bigText);

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            String channelId = "Your_channel_id";
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channel);
            mBuilder.setChannelId(channelId);
        }
        notification = mBuilder.build();

    }

    private void setUpCheckBox(){

        checkBox = findViewById(R.id.checkBox);
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(checkBox.isChecked()){
                    enableTextNotification = true;
                }
                else
                    enableTextNotification = false;
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        final Switch onOffSwitch = findViewById(R.id.switch1);
        textView = findViewById(R.id.textView);

        setupBluetooth();
        setupNotificationManager();
        setUpCheckBox();
        //mNotificationManager.notify(0, notification);

        //Keep on asking permission till its granted
        while(!checkPermissions()){
            requestPermissions();
        }

        final Runnable scannerTask = new Runnable() {
            @Override
            public void run() {
                if (BTAdapter.isDiscovering()) {
                    BTAdapter.cancelDiscovery();
                }
                BTAdapter.startDiscovery();
            }
        };

        onOffSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                if(onOffSwitch.isChecked()){
                    if(!BTAdapter.isEnabled()){
                        BTAdapter.enable();
                    }
                    try {
                        future = scheduledExecutorService.scheduleAtFixedRate(
                                scannerTask,
                                0,
                                5,
                                TimeUnit.SECONDS);
                    }catch(RuntimeException e){
                        System.out.println(e);
                    }
                    Toast.makeText(getApplicationContext(),"Bluetooth Scan on",Toast.LENGTH_SHORT).show();
                }
                else {
                    if(future!=null){
                        future.cancel(true);
                    }
                    BTAdapter.disable();
                    textView.setText("Testing - Scan stopped");
                    Toast.makeText(getApplicationContext(), "Bluetooth scan off", Toast.LENGTH_SHORT).show();
                }

            }
        });

    }

    private void vibrateOnThresholdCross(int signalStrength){
        if(signalStrength>allowedThreshold){
            v.vibrate(1000);
            if(enableTextNotification)
                mNotificationManager.notify(0, notification);
        }
    }

    private boolean checkPermissions(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED){
            return true;
        }
        return false;
    }

    private void requestPermissions(){
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.BLUETOOTH_ADMIN,Manifest.permission.BLUETOOTH},
                PERMISSION_ID
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_ID) {
            if(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(),"Bluetooth permisions granted",Toast.LENGTH_LONG).show();
            }
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {

            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {

                int  rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                textView.setText("  RSSI: " + rssi + "dBm; Name: "+name);
                vibrateOnThresholdCross(rssi);
                //Toast.makeText(getApplicationContext(),"  RSSI: " + rssi + "dBm\tName: "+name, Toast.LENGTH_SHORT).show();
            }
        }
    };



    // add this line for Removing Force Close

    @Override
    protected void onDestroy() {
        // closing Entire Application
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }

}
