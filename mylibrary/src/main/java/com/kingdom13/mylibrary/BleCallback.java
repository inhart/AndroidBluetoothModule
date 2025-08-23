package com.kingdom13.mylibrary;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.List;

public interface BleCallback {
    void onConnectionStateChange(BluetoothGatt gatt, int status, int newState);
    void onServicesDiscovered(BluetoothGatt gatt, int status);

    void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
    void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status);
    void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);

    void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);
    void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status);

    void onMtuChanged(BluetoothGatt gatt, int mtu, int status);

    void onScanResult(String name, String address);

    void onConnected(String macAddress);

    void onDisconnected(String macAddress);

    void onCharacteristicChanged(String string, String string1, byte[] value);

    void onServicesDiscovered(List<String> uuids);
}
