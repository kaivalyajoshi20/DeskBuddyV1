package com.deskbuddy.v1;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private BuddyBleManager ble;
    private TextView statusText;
    private BluetoothLeScanner scanner;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> startScan());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        ble = BuddyBleManager.get(this);
        ble.setStatusListener(() -> statusText.setText(ble.getStatus()));
        statusText.setText(ble.getStatus());

        findViewById(R.id.connectCard).setOnClickListener(v -> requestBluetoothAndScan());

        findViewById(R.id.musicCard).setOnClickListener(v -> sendTestMusic());
        findViewById(R.id.testButton).setOnClickListener(v -> sendTestMusic());

        findViewById(R.id.notificationCard).setOnClickListener(v ->
                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));

        findViewById(R.id.settingsButton).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));

        requestBluetoothAndScan();
    }

    private void sendTestMusic() {
        ble.sendJson("{\"type\":\"MUSIC\",\"title\":\"Desk Buddy Test\",\"artist\":\"ESP32\",\"app\":\"Desk Buddy\",\"playing\":true}");
    }

    private void requestBluetoothAndScan() {
        if (Build.VERSION.SDK_INT >= 31) {
            boolean scan = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
            boolean connect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            if (!scan || !connect) {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                });
                return;
            }
        }
        startScan();
    }

    private void startScan() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            statusText.setText("Bluetooth is off");
            return;
        }

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            statusText.setText("BLE scanner unavailable");
            return;
        }

        statusText.setText("Searching for Desk Buddy…");
        scanner.startScan(scanCallback);

        statusText.postDelayed(() -> {
            try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
            if (!ble.isConnected()) statusText.setText("Not connected");
        }, 8000);
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice d = result.getDevice();
            String name = d.getName();
            if ("DeskBuddy".equals(name)) {
                try { scanner.stopScan(this); } catch (Exception ignored) {}
                ble.connect(d);
            }
        }
    };
}
