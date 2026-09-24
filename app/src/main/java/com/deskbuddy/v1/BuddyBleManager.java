package com.deskbuddy.v1;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class BuddyBleManager {
    public static final UUID SERVICE_UUID =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID WRITE_UUID =
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    private static BuddyBleManager instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeCharacteristic;
    private String status = "Disconnected";
    private Runnable statusListener;

    private BuddyBleManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized BuddyBleManager get(Context context) {
        if (instance == null) instance = new BuddyBleManager(context);
        return instance;
    }

    public void setStatusListener(Runnable listener) {
        this.statusListener = listener;
    }

    public String getStatus() {
        return status;
    }

    private void setStatus(String value) {
        status = value;
        if (statusListener != null) main.post(statusListener);
    }

    public void connect(BluetoothDevice device) {
        if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            setStatus("Bluetooth permission needed");
            return;
        }
        close();
        String name = device.getName();
        setStatus("Connecting to " + (name == null ? "DeskBuddy" : name));
        gatt = device.connectGatt(context, false, callback);
    }

    public boolean isConnected() {
        return writeCharacteristic != null;
    }

    public void sendJson(String json) {
        if (gatt == null || writeCharacteristic == null) {
            setStatus("Not connected");
            return;
        }
        writeCharacteristic.setValue(json.getBytes(StandardCharsets.UTF_8));
        writeCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) return;
        boolean started = gatt.writeCharacteristic(writeCharacteristic);
        if (!started) setStatus("BLE write failed");
    }

    public void close() {
        if (gatt != null) {
            if (Build.VERSION.SDK_INT < 31 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            == PackageManager.PERMISSION_GRANTED) {
                gatt.disconnect();
                gatt.close();
            }
        }
        gatt = null;
        writeCharacteristic = null;
        setStatus("Disconnected");
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int statusCode, int newState) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                setStatus("Connected — discovering services");
                if (Build.VERSION.SDK_INT < 31 ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED) {
                    g.discoverServices();
                }
            } else {
                writeCharacteristic = null;
                setStatus("Disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int statusCode) {
            BluetoothGattService service = g.getService(SERVICE_UUID);
            if (service == null) {
                setStatus("DeskBuddy service not found");
                return;
            }
            writeCharacteristic = service.getCharacteristic(WRITE_UUID);
            if (writeCharacteristic == null) {
                setStatus("Write characteristic not found");
            } else {
                setStatus("Connected to DeskBuddy");
            }
        }
    };
}
