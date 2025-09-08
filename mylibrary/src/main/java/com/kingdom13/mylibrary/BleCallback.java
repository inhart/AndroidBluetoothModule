package com.kingdom13.mylibrary;

/**
 * Simple, stable interface for Unity callbacks.
 * The Java side will translate Android objects into primitive types / JSON
 * so Unity doesn't need to manipulate AndroidJavaObjects for GATT internals.
 */
public interface BleCallback {
    // Scan
    void onScanResult(String name, String address, int rssi, String firstServiceUuid);
    void onScanError(String error);

    // Connection
    void onConnected(String macAddress);
    void onDisconnected(String macAddress);

    // Full snapshot of services/characteristics/descriptors (JSON string)
    void onServicesSnapshot(String json);

    // Characteristic notifications / read / write results
    void onCharacteristicChanged(String serviceUuid, String characteristicUuid, byte[] value);
    void onCharacteristicRead(String serviceUuid, String characteristicUuid, byte[] value, int status);
    void onCharacteristicWrite(String serviceUuid, String characteristicUuid, int status);

    // MTU changed
    void onMtuChanged(int mtu, int status);
}
