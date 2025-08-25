package com.kingdom13.mylibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.List;

// Listener interface -> implement in Unity via AndroidJavaProxy
public interface BleCallback {
    void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);
    void onServicesDiscovered(BluetoothGatt gatt, int status);

    void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
    void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
    void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

    void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);
    void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);

    void onMtuChanged(BluetoothGatt gatt, int mtu, int status);
    void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status);

    void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status);
    void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status);

    void onScanStarted();
    void onScanStopped();
    void onScanResult(String name, String address, int rssi, String firstServiceUuid);
    void onScanError(String error);

    void onScanResult(String name, String address);

    void onConnected(String macAddress);

    void onDisconnected(String macAddress);

    void onCharacteristicChanged(String string, String string1, byte[] value);

    void onServicesDiscovered(List<String> uuids);
}
