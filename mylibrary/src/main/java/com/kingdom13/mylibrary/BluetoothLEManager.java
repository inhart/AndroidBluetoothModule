package com.kingdom13.mylibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;

public class BluetoothLEManager {
    private static BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static BluetoothGatt bluetoothGatt;
    private static BluetoothGattCharacteristic currentCharacteristic;

    public static void startScan(BluetoothAdapter.LeScanCallback callback) {
        bluetoothAdapter.startLeScan(callback);
    }

    public static void stopScan(BluetoothAdapter.LeScanCallback callback) {
        bluetoothAdapter.stopLeScan(callback);
    }

    public static void connect(Context context, String macAddress, BluetoothGattCallback gattCallback) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    public static void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }

    public static boolean writeData(byte[] data) {
        if (currentCharacteristic != null) {
            currentCharacteristic.setValue(data);
            return bluetoothGatt.writeCharacteristic(currentCharacteristic);
        }
        return false;
    }
}
