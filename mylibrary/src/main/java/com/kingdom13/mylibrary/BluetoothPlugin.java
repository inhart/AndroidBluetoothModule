package com.kingdom13.mylibrary;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.List;

public class BluetoothPlugin {
    private static Context unityContext;
    private static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    public static void init(Context context) {
        unityContext = context;
    }

    // ======================
    // General
    // ======================
    public static boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void enableBluetooth() {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
    }

    // ======================
    // Classic RFCOMM
    // ======================
    public static String[] getDiscoveredDevices() {
        return BluetoothClassicManager.getDiscoveredDevices();
    }

    public static boolean connectClassic(String macAddress) {
        return BluetoothClassicManager.connect(macAddress);
    }

    public static void disconnectClassic() {
        BluetoothClassicManager.disconnect();
    }

    public static boolean sendClassic(String message) {
        return BluetoothClassicManager.sendData(message);
    }

    public static String receiveClassic() {
        return BluetoothClassicManager.receiveData();
    }

    // ======================
    // BLE
    // ======================
    private static final BluetoothAdapter.LeScanCallback bleCallback = new BluetoothAdapter.LeScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (device != null && device.getName() != null) {
                Log.d("BluetoothPlugin", "BLE Found: " + device.getName() + " - " + device.getAddress());
            }
        }
    };

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void startBLEScan() {
        if (bluetoothAdapter != null) {
            BluetoothLEManager.startScan(bleCallback);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void stopBLEScan() {
        if (bluetoothAdapter != null) {
            BluetoothLEManager.stopScan(bleCallback);
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void connectBLE(String macAddress) {
        BluetoothLEManager.connect(unityContext, macAddress, gattCallback);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void disconnectBLE() {
        BluetoothLEManager.disconnect();
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static boolean writeBLE(byte[] data) {
        return BluetoothLEManager.writeData(data);
    }

    // Simple callback handler for GATT
    private static final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothPlugin", "Connected to GATT server");
                List<BluetoothGattService> services = gatt.getServices();;
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BluetoothPlugin", "Disconnected from GATT server");
            }
        }
    };
}
