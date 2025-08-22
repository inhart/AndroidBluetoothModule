package com.kingdom13.mylibrary;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;

import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import kotlin.jvm.JvmStatic;

public class bluelight {

    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothLeScanner bleScanner;
    private static final List<String> devices = new ArrayList<>();

    private static final UUID UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private static BluetoothSocket bluetoothSocket;
    // Inicializar Bluetooth
    @JvmStatic
    public static boolean initBluetooth(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e("MyUnityModule", "Bluetooth no soportado en este dispositivo.");
            return false;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        return true;
    }

    // Comprobar si el Bluetooth está encendido
    public static boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    // Escanear dispositivos BLE
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @JvmStatic
    public static void startScan() {
        if (bleScanner == null) {
            Log.e("MyUnityModule", "BLE Scanner no disponible.");
            return;
        }
        devices.clear();
        bleScanner.startScan(scanCallback);
        Log.d("MyUnityModule", "Escaneo BLE iniciado...");
    }

    // Detener escaneo BLE
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @JvmStatic
    public static void stopScan() {
        if (bleScanner != null) {
            bleScanner.stopScan(scanCallback);
            Log.d("MyUnityModule", "Escaneo BLE detenido.");
        }
    }

    // Obtener lista de dispositivos encontrados
    public static String[] getFoundDevices() {
        return devices.toArray(new String[0]);
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    public static void connect(String deviceAddress){
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Log.e(TAG, "Dispositivo no encontrado.");
            return;
        }
else try {
            BluetoothSocket socket = device.createRfcommSocketToServiceRecord(UUID);
            socket.connect();
            Log.i(TAG, "Conectado");
        } catch (IOException e) {
            Log.e(TAG, "Error al conectar: " + e.getMessage());

        }
    }

    private static void disconnect() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
                Log.i(TAG, "Desconectado");
            } catch (IOException e) {
                Log.e(TAG, "Error al desconectar: " + e.getMessage());
            }
        }
    }

    public static boolean pairDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Log.e("MyUnityModule", "Dispositivo no encontrado.");
            return false;
        }

        try {
            device.createBond();
            Log.d("MyUnityModule", "Dispositivo parqueado.");
            return true;
        } catch (SecurityException e) {
            Log.e("MyUnityModule", "Error al intentar parquear el dispositivo: " + e.getMessage());
            return false;

        }
    }

    public static void connectToPairedDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        if (device == null) {
            Log.e("MyUnityModule", "Dispositivo no encontrado.");

        }else{
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                socket.connect();
                Log.d("MyUnityModule", "Conexión establecida con el dispositivo.");
            } catch (IOException e) {
                Log.e("MyUnityModule", "Error al establecer la conexión: " + e.getMessage());

            }
        }




    }

    private static final ScanCallback scanCallback = new ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String info = device.getName() + " - " + device.getAddress();
            if (!devices.contains(info)) {
                devices.add(info);
                Log.d("MyUnityModule", "Dispositivo encontrado: " + info);
            }
        }
    };
}
