package com.kingdom13.mylibrary;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;

import androidx.annotation.RequiresPermission;

public class BluetoothLEManager {
    private static final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static BluetoothGatt bluetoothGatt;
    private static BluetoothGattCharacteristic currentCharacteristic;

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void startScan(BluetoothAdapter.LeScanCallback callback) {
        bluetoothAdapter.startLeScan(callback);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void stopScan(BluetoothAdapter.LeScanCallback callback) {
        bluetoothAdapter.stopLeScan(callback);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void connect(Context context, String macAddress, BluetoothGattCallback gattCallback) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static boolean writeData(byte[] data) {
        if (currentCharacteristic != null) {
            currentCharacteristic.setValue(data);
            return bluetoothGatt.writeCharacteristic(currentCharacteristic);
        }
        return false;
    }


}
