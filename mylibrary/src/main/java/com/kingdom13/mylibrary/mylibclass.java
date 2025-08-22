package com.kingdom13.mylibrary;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import androidx.annotation.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

public class mylibclass {
    public static String getMessage() {
        Log.d("MyUnityModule", "El módulo se cargó correctamente desde Unity.");
        return "Hola desde el módulo Android!";
    }
    private static BluetoothAdapter bluetoothAdapter;
    private static BluetoothLeScanner bleScanner;
    private static final List<String> devices = new ArrayList<>();

    // Inicializar Bluetooth
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

