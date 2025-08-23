package com.kingdom13.mylibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;


public class BlueLight {


    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothGatt bluetoothGatt;
    private static BluetoothLeScanner bleScanner;
    private static BleCallback callback;
    private BleCallback bleCallback;

    // Callback interface
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (bleCallback != null) bleCallback.onConnectionStateChange(gatt, status, newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (bleCallback != null) bleCallback.onServicesDiscovered(gatt, status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (bleCallback != null) bleCallback.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (bleCallback != null)
                bleCallback.onCharacteristicWrite(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (bleCallback != null) bleCallback.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (bleCallback != null) bleCallback.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (bleCallback != null) bleCallback.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (bleCallback != null) bleCallback.onMtuChanged(gatt, mtu, status);
        }
    };

    public BlueLight(Context context, BleCallback cb) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        callback = cb;
    }

    // Detener escaneo
    public static void stopScan() {
        bleScanner.stopScan(new ScanCallback() {
        });
        callback = null;

    }

    // Iniciar escaneo BLE
    public static void startScan() {
        bleScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (callback != null && result.getDevice() != null) {
                    callback.onScanResult(result.getDevice().getName(), result.getDevice().getAddress());
                }
            }
        });
    }

    // Conectar a un dispositivo
    public static void connect() {
        connect(null, null);
    }

    // Conectar a un dispositivo
    public static void connect(Context context, String macAddress) {
        BluetoothDevice device = BlueLight.bluetoothAdapter.getRemoteDevice(macAddress);
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (BlueLight.callback != null) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        callback.onConnected(macAddress);
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        callback.onDisconnected(macAddress);
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (callback != null) {
                    List<String> uuids = new ArrayList<>();
                    for (BluetoothGattService svc : gatt.getServices()) {
                        uuids.add(svc.getUuid().toString());
                    }
                    callback.onServicesDiscovered(uuids);
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt,
                                                BluetoothGattCharacteristic characteristic) {
                if (callback != null) {
                    callback.onCharacteristicChanged(
                            characteristic.getService().getUuid().toString(),
                            characteristic.getUuid().toString(),
                            characteristic.getValue()
                    );
                }
            }
        });
    }

    public void setBleCallback(BleCallback callback) {
        this.bleCallback = callback;
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            callback = null;

        }
    }
}