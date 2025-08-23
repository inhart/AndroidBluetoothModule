package com.kingdom13.mylibrary;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.*;

import kotlin.jvm.JvmStatic;

public class BlueLight {

    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothGatt bluetoothGatt;
    private static BluetoothLeScanner bleScanner;
    private static BleCallback callback;

    // Callback interface

    public static interface BleCallback {
        void onScanResult(String name, String address);
        void onConnected(String address);
        void onDisconnected(String address);
        void onServicesDiscovered(List<String> serviceUuids);
        void onCharacteristicChanged(String service, String characteristic, byte[] value);
    }
    public BlueLight(Context context, BleCallback cb) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        this.callback = cb;
    }

    // Iniciar escaneo BLE
    @JvmStatic
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void startScan() {
        bleScanner.startScan(new ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (callback != null && result.getDevice() != null) {
                    callback.onScanResult(result.getDevice().getName(), result.getDevice().getAddress());
                }
            }
        });
    }

    // Detener escaneo
    @JvmStatic
    @RequiresPermission(allOf = {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT})
    public static void stopScan() {
        bleScanner.stopScan(new ScanCallback() {});

        bluetoothAdapter.getBluetoothLeScanner().stopScan(new ScanCallback() {});

    }

    public static boolean isBluetoothEnabled(){
        return bluetoothAdapter.isEnabled();
    }
    // Conectar a un dispositivo
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    public static void connect(Context context, String macAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
        bluetoothGatt = device.connectGatt(context, false, new BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (callback != null) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        callback.onConnected(macAddress);
                        gatt.discoverServices();
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        callback.onDisconnected(macAddress);
                    }
                }
            }

            @Override
            @JvmStatic
            public  void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (callback != null) {
                    List<String> uuids = new ArrayList<>();
                    for (BluetoothGattService svc : gatt.getServices()) {
                        uuids.add(svc.getUuid().toString());
                    }
                    callback.onServicesDiscovered(uuids);
                }
            }

            @Override
            @JvmStatic
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @JvmStatic
    public static void disconnect() {
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();


        }
    }
}