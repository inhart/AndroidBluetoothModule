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
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BlueLight: Android-side BLE helper.
 * - Scans for devices
 * - Connects and manages GATT
 * - Builds JSON snapshots for discovered services/characteristics/descriptors
 * - Exposes helpers for read/write/notify/requestMtu
 *
 * Unity registers a BleCallback (AndroidJavaProxy) via setCallback()
 */
public class BlueLight {
    private static final String TAG = "BlueLight";
    private static BleCallback callback;

    private final Context appContext;
    private final BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    // -------- Scan --------
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String name = device != null ? device.getName() : null;
            if (name == null && result.getScanRecord() != null)
                name = result.getScanRecord().getDeviceName();
            String addr = device != null ? device.getAddress() : null;
            int rssi = result.getRssi();
            String firstService = null;
            ScanRecord rec = result.getScanRecord();
            if (rec != null && rec.getServiceUuids() != null && !rec.getServiceUuids().isEmpty()) {
                firstService = rec.getServiceUuids().get(0).toString();
            }
            if (callback != null) callback.onScanResult(name, addr, rssi, firstService);
        }

        @Override public void onScanFailed(int errorCode) {
            if (callback != null) callback.onScanError("Scan failed: code " + errorCode);
        }
    };
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (callback != null) callback.onConnected(g.getDevice().getAddress());
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (callback != null) callback.onDisconnected(g.getDevice().getAddress());
                closeGatt();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            try {
                JSONObject root = buildServicesSnapshot(g);
                if (callback != null) callback.onServicesSnapshot(root.toString());
            } catch (Exception e) {
                Log.e(TAG, "build snapshot: " + e.getMessage());
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (callback != null)
                callback.onCharacteristicChanged(safeSvcUuid(c), c.getUuid().toString(), c.getValue());
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (callback != null)
                callback.onCharacteristicRead(safeSvcUuid(c), c.getUuid().toString(), c.getValue(), status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            if (callback != null)
                callback.onCharacteristicWrite(safeSvcUuid(c), c.getUuid().toString(), status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (callback != null) callback.onMtuChanged(mtu, status);
        }
    };

    public BlueLight(Context context) {
        this.appContext = context.getApplicationContext();
        BluetoothManager mgr = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = mgr != null ? mgr.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        if (this.adapter == null) Log.e(TAG, "BluetoothAdapter is null");
    }

    /**
     * Register Unity proxy. Call once from Unity.
     */
    public static void setCallback(BleCallback cb) {
        callback = cb;
    }

    // -------- Utilities --------
    private static String safeSvcUuid(BluetoothGattCharacteristic c) {
        if (c == null) return null;
        BluetoothGattService s = c.getService();
        return s != null ? s.getUuid().toString() : null;
    }

    // -------- Connect / GATT --------

    private static JSONArray propertiesToJson(int p) throws Exception {
        List<String> list = new ArrayList<>();
        if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) list.add("READ");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) list.add("WRITE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)
            list.add("WRITE_NO_RESPONSE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) list.add("NOTIFY");
        if ((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) list.add("INDICATE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_BROADCAST) != 0) list.add("BROADCAST");
        if ((p & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) != 0) list.add("SIGNED_WRITE");
        if ((p & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) != 0) list.add("EXTENDED");
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr;
    }

    public void startScan() {
        if (adapter == null || !adapter.isEnabled()) {
            if (callback != null) callback.onScanError("Bluetooth disabled or unavailable");
            return;
        }
        if (scanner == null) scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            if (callback != null) callback.onScanError("BluetoothLeScanner unavailable");
            return;
        }
        try {
            scanner.startScan(scanCallback);
        } catch (Throwable t) {
            if (callback != null) callback.onScanError("startScan error: " + t.getMessage());
        }
    }

    public void stopScan() {
        if (scanner != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (Throwable ignore) {
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void connect(String address) {
        if (adapter == null) {
            if (callback != null) callback.onScanError("No adapter");
            return;
        }
        BluetoothDevice dev = null;
        try {
            dev = adapter.getRemoteDevice(address);
        } catch (Throwable t) {
            if (callback != null) callback.onScanError("Invalid address: " + address);
            return;
        }
        if (dev == null) {
            if (callback != null) callback.onScanError("Device not found: " + address);
            return;
        }
        closeGatt();
        try {
            gatt = dev.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (Throwable t) {
            // fallback for older APIs
            gatt = dev.connectGatt(appContext, false, gattCallback);
        }
    }

    public void disconnect() {
        closeGatt();
    }

    private void closeGatt() {
        if (gatt != null) {
            try {
                gatt.disconnect();
            } catch (Throwable ignore) {
            }
            try {
                gatt.close();
            } catch (Throwable ignore) {
            }
            gatt = null;
        }
    }

    // -------- Public GATT ops (Unity calls these) --------
    public boolean requestMtu(int mtu) {
        return gatt != null && gatt.requestMtu(mtu);
    }

    public boolean readCharacteristic(String serviceUuid, String characteristicUuid) {
        if (gatt == null) return false;
        BluetoothGattService s = gatt.getService(UUID.fromString(serviceUuid));
        if (s == null) return false;
        BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(characteristicUuid));
        if (c == null) return false;
        return gatt.readCharacteristic(c);
    }

    public boolean writeCharacteristic(String serviceUuid, String characteristicUuid, byte[] value, int writeType) {
        if (gatt == null) return false;
        BluetoothGattService s = gatt.getService(UUID.fromString(serviceUuid));
        if (s == null) return false;
        BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(characteristicUuid));
        if (c == null) return false;
        c.setWriteType(writeType);
        c.setValue(value);
        return gatt.writeCharacteristic(c);
    }

    public boolean setNotify(String serviceUuid, String characteristicUuid, boolean enable) {
        if (gatt == null) return false;
        BluetoothGattService s = gatt.getService(UUID.fromString(serviceUuid));
        if (s == null) return false;
        BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(characteristicUuid));
        if (c == null) return false;
        boolean ok = gatt.setCharacteristicNotification(c, enable);
        BluetoothGattDescriptor ccc = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
        if (ccc != null) {
            ccc.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(ccc);
        }
        return ok;
    }

    private JSONObject buildServicesSnapshot(BluetoothGatt g) throws Exception {
        JSONObject root = new JSONObject();
        root.put("device", g.getDevice() != null ? g.getDevice().getAddress() : null);
        JSONArray services = new JSONArray();
        for (BluetoothGattService svc : g.getServices()) {
            JSONObject js = new JSONObject();
            js.put("uuid", svc.getUuid().toString());
            js.put("type", svc.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY ? "PRIMARY" : "SECONDARY");
            JSONArray chars = new JSONArray();
            JSONArray descs = new JSONArray();

            for (BluetoothGattCharacteristic ch : svc.getCharacteristics()) {
                JSONObject jc = new JSONObject();
                jc.put("uuid", ch.getUuid().toString());
                jc.put("properties", propertiesToJson(ch.getProperties()));
                chars.put(jc);

                for (BluetoothGattDescriptor d : ch.getDescriptors()) {
                    descs.put(d.getUuid().toString());
                }
            }
            js.put("characteristics", chars);
            js.put("descriptors", descs);
            services.put(js);
        }
        root.put("services", services);
        return root;
    }
}
