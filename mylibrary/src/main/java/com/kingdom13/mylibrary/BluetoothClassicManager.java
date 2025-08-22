package com.kingdom13.mylibrary;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class BluetoothClassicManager {
    private static BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static ArrayList<String> discoveredDevices = new ArrayList<>();
    private static BluetoothSocket socket;
    private static OutputStream outputStream;
    private static InputStream inputStream;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    public static String[] getDiscoveredDevices() {
        return discoveredDevices.toArray(new String[0]);
    }

    public static boolean connect(String macAddress) {
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(macAddress);
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public static boolean sendData(String data) {
        try {
            if (outputStream != null) {
                outputStream.write(data.getBytes());
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String receiveData() {
        try {
            if (inputStream != null) {
                byte[] buffer = new byte[1024];
                int bytes = inputStream.read(buffer);
                return new String(buffer, 0, bytes);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}
