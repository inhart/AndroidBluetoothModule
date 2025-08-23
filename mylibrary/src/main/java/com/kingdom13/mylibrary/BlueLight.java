package com.kingdom13.mylibrary;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * BlueLight BLE - Android-only BLE manager (no Unity refs).
 *
 * Usage:
 *  - Create: new BlueLight(context)
 *  - setListener(listener)
 *  - startScan / stopScan
 *  - connect(mac, autoConnect)
 *  - discoverServices(), read/write, setNotify, requestMtu, etc.
 *
 * Listener interface provided; from Unity implement it with AndroidJavaProxy.
 */
@SuppressLint("MissingPermission")
public class BlueLight {

    public static final String TAG = "BlueLight";

    private static final UUID UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // System handles
    private final Context app;
    private final BluetoothManager btMgr;
    private final BluetoothAdapter btAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private int currentMtu = 23;

    // Cache
    private final Map<String, BluetoothGattCharacteristic> charCache = new HashMap<>();

    // Operation queue for GATT serialization
    private final Deque<Runnable> opQueue = new ArrayDeque<>();
    private boolean opInFlight = false;

    // Listener (callbacks delivered on main looper)
    private BleListener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    public BlueLight(Context ctx) {
        this.app = ctx.getApplicationContext();
        this.btMgr = (BluetoothManager) app.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = btMgr != null ? btMgr.getAdapter() : null;
        if (btAdapter != null) this.scanner = btAdapter.getBluetoothLeScanner();
    }

    /** Listener interface for async events. Implement this in C# via AndroidJavaProxy. */
    public interface BleListener {
        void onScanStarted();
        void onScanStopped();
        void onScanResult(String name, String address, int rssi, String serviceUuid); // serviceUuid can be empty
        void onScanError(String error);

        void onConnected(String address);
        void onDisconnected(String address);
        void onConnectionError(String error);

        void onServicesDiscovered(int status);

        void onCharacteristicRead(String serviceUuid, String charUuid, byte[] value, int status);
        void onCharacteristicWrite(String serviceUuid, String charUuid, byte[] value, int status);
        void onNotify(String serviceUuid, String charUuid, byte[] value);

        void onDescriptorRead(String serviceUuid, String charUuid, String descUuid, byte[] value, int status);
        void onDescriptorWrite(String serviceUuid, String charUuid, String descUuid, byte[] value, int status);

        void onMtuChanged(int mtu, int status);
        void onReadRemoteRssi(int rssi, int status);

        void onPhyUpdated(int txPhy, int rxPhy, int status);
        void onPhyRead(int txPhy, int rxPhy, int status);
    }

    public void setListener(BleListener l) {
        this.listener = l;
    }

    /* ====================== Utilities & State ====================== */

    public boolean isBleSupported() {
        return btAdapter != null && app.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le");
    }

    public boolean isEnabled() { return btAdapter != null && btAdapter.isEnabled(); }

    public String[] getBondedDevices() {
        if (btAdapter == null) return new String[0];
        List<String> out = new ArrayList<>();
        for (BluetoothDevice d : btAdapter.getBondedDevices()) {
            out.add(d.getName() + "|" + d.getAddress());
        }
        return out.toArray(new String[0]);
    }

    private static String key(UUID s, UUID c) { return s.toString() + "|" + c.toString(); }

    private void post(Runnable r) {
        if (main != null) main.post(r);
        else r.run();
    }

    private String b64(byte[] d) {
        if (d == null || d.length == 0) return "";
        if (Build.VERSION.SDK_INT >= 26) return Base64.getEncoder().encodeToString(d);
        else return android.util.Base64.encodeToString(d, android.util.Base64.NO_WRAP);
    }

    private byte[] fromB64(String s) {
        if (s == null || s.isEmpty()) return new byte[0];
        if (Build.VERSION.SDK_INT >= 26) return Base64.getDecoder().decode(s);
        else return android.util.Base64.decode(s, android.util.Base64.DEFAULT);
    }

    /* ====================== Scanning ====================== */

    private ScanCallback internalScanCb = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult r) {
            if (r == null || r.getDevice() == null) return;
            final String name = r.getDevice().getName() != null ? r.getDevice().getName() : "";
            final String addr = r.getDevice().getAddress();
            final int rssi = r.getRssi();
            String firstSvc;
            if (r.getScanRecord() != null && r.getScanRecord().getServiceUuids() != null && !r.getScanRecord().getServiceUuids().isEmpty()) {
                firstSvc = r.getScanRecord().getServiceUuids().get(0).toString();
            } else {
                firstSvc = "";
            }
            post(() -> {
                if (listener != null) listener.onScanResult(name, addr, rssi, firstSvc);
            });
        }

        @Override public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult r : results) onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r);
        }

        @Override public void onScanFailed(int errorCode) {
            final String err = "ScanFailed:" + errorCode;
            post(() -> { if (listener != null) listener.onScanError(err); });
        }
    };

    /**
     * Start scanning.
     * @param serviceUuids filter by service UUID strings (nullable)
     * @param nameContains filter by device name contains (case-insensitive) (nullable)
     * @param scanMillis autos top after ms (<=0 means no auto stop)
     */
    public void startScan(String[] serviceUuids, String[] nameContains, int scanMillis) {
        if (btAdapter == null) { if (listener!=null) listener.onScanError("NO_ADAPTER"); return; }
        if (scanner == null) scanner = btAdapter.getBluetoothLeScanner();
        if (scanner == null) { if (listener!=null) listener.onScanError("NO_SCANNER"); return; }

        List<ScanFilter> filters = new ArrayList<>();
        if (serviceUuids != null) {
            for (String s : serviceUuids) {
                try { filters.add(new ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(s)).build()); }
                catch (Exception ignored) {}
            }
        }

        ScanSettings.Builder sb = new ScanSettings.Builder().setReportDelay(0).setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        if (Build.VERSION.SDK_INT >= 23) sb.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES);

        // wrap to apply name filtering
        ScanCallback cb = internalScanCb;
        if (nameContains != null && nameContains.length > 0) {
            final String[] names = nameContains;
            cb = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    String dn = result.getDevice().getName();
                    String cmp = dn == null ? "" : dn.toLowerCase(Locale.US);
                    boolean any = false;
                    for (String n : names) {
                        if (cmp.contains(n.toLowerCase(Locale.US))) { any = true; break; }
                    }
                    if (any) internalScanCb.onScanResult(callbackType, result);
                }
                @Override public void onBatchScanResults(List<ScanResult> results) {
                    for (ScanResult r : results) onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, r);
                }
                @Override public void onScanFailed(int errorCode) { internalScanCb.onScanFailed(errorCode); }
            };
        }

        try {
            scanner.startScan(filters, sb.build(), cb);
            post(() -> { if (listener != null) listener.onScanStarted(); });
            if (scanMillis > 0) main.postDelayed(this::stopScan, scanMillis);
        } catch (Throwable t) {
            post(() -> { if (listener != null) listener.onScanError("START_FAILED:" + t.getMessage()); });
        }
    }

    public void stopScan() {
        try {
            if (scanner != null) scanner.stopScan(internalScanCb);
        } catch (Throwable ignored) {}
        post(() -> { if (listener != null) listener.onScanStopped(); });
    }

    /* ====================== Connection / GATT ====================== */

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void connect(String macAddress, boolean autoConnect) {
        if (btAdapter == null) { post(() -> { if (listener!=null) listener.onConnectionError("NO_ADAPTER"); }); return; }
        try {
            BluetoothDevice device = btAdapter.getRemoteDevice(macAddress);
            gatt = device.connectGatt(app, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (IllegalArgumentException iae) {
            post(() -> { if (listener!=null) listener.onConnectionError("BAD_ADDRESS"); });
        }
    }

    public void disconnect() {
        if (gatt != null) gatt.disconnect();
    }

    public void close() {
        if (gatt != null) {
            try { gatt.close(); } catch (Throwable ignored) {}
            gatt = null;
        }
        charCache.clear();
        currentMtu = 23;
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                post(() -> { if (listener!=null) listener.onConnectionError(String.valueOf(status)); });
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                post(() -> { if (listener!=null) listener.onConnected(g.getDevice().getAddress()); });
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                post(() -> { if (listener!=null) listener.onDisconnected(g.getDevice().getAddress()); });
                close();
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            // cache chars
            charCache.clear();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (BluetoothGattService s : g.getServices()) {
                    for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                        charCache.put(key(s.getUuid(), c.getUuid()), c);
                    }
                }
            }
            post(() -> { if (listener!=null) listener.onServicesDiscovered(status); });
            opDone();
        }

        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            final String su = ch.getService().getUuid().toString();
            final String cu = ch.getUuid().toString();
            final byte[] val = ch.getValue();
            post(() -> { if (listener!=null) listener.onCharacteristicRead(su, cu, val, status); });
            opDone();
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic ch, int status) {
            final String su = ch.getService().getUuid().toString();
            final String cu = ch.getUuid().toString();
            final byte[] val = ch.getValue();
            post(() -> { if (listener!=null) listener.onCharacteristicWrite(su, cu, val, status); });
            opDone();
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic ch) {
            final String su = ch.getService().getUuid().toString();
            final String cu = ch.getUuid().toString();
            final byte[] val = ch.getValue();
            post(() -> { if (listener!=null) listener.onNotify(su, cu, val); });
        }

        @Override public void onDescriptorRead(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            final String su = d.getCharacteristic().getService().getUuid().toString();
            final String cu = d.getCharacteristic().getUuid().toString();
            final String du = d.getUuid().toString();
            final byte[] val = d.getValue();
            post(() -> { if (listener!=null) listener.onDescriptorRead(su, cu, du, val, status); });
            opDone();
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            final String su = d.getCharacteristic().getService().getUuid().toString();
            final String cu = d.getCharacteristic().getUuid().toString();
            final String du = d.getUuid().toString();
            final byte[] val = d.getValue();
            post(() -> { if (listener!=null) listener.onDescriptorWrite(su, cu, du, val, status); });
            opDone();
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            currentMtu = mtu;
            post(() -> { if (listener!=null) listener.onMtuChanged(mtu, status); });
            opDone();
        }

        @Override public void onReadRemoteRssi(BluetoothGatt g, int rssi, int status) {
            post(() -> { if (listener!=null) listener.onReadRemoteRssi(rssi, status); });
            opDone();
        }

        @Override public void onPhyUpdate(BluetoothGatt g, int txPhy, int rxPhy, int status) {
            post(() -> { if (listener!=null) listener.onPhyUpdated(txPhy, rxPhy, status); });
            opDone();
        }

        @Override public void onPhyRead(BluetoothGatt g, int txPhy, int rxPhy, int status) {
            post(() -> { if (listener!=null) listener.onPhyRead(txPhy, rxPhy, status); });
            opDone();
        }
    };

    /* ====================== Service/Characteristic helpers ====================== */

    public String[] getServiceUuids() {
        if (gatt == null) return new String[0];
        List<BluetoothGattService> svcs = gatt.getServices();
        String[] out = new String[svcs.size()];
        int i = 0;
        for (BluetoothGattService s : svcs) out[i++] = s.getUuid().toString();
        return out;
    }

    public String[] getCharacteristicUuids(String serviceUuid) {
        if (gatt == null) return new String[0];
        BluetoothGattService s = gatt.getService(UUID.fromString(serviceUuid));
        if (s == null) return new String[0];
        List<BluetoothGattCharacteristic> chs = s.getCharacteristics();
        String[] out = new String[chs.size()];
        int i = 0;
        for (BluetoothGattCharacteristic c : chs) out[i++] = c.getUuid().toString();
        return out;
    }

    private BluetoothGattCharacteristic findChar(String serviceUuid, String charUuid) {
        if (gatt == null) return null;
        String k = key(UUID.fromString(serviceUuid), UUID.fromString(charUuid));
        BluetoothGattCharacteristic cached = charCache.get(k);
        if (cached != null) return cached;
        BluetoothGattService s = gatt.getService(UUID.fromString(serviceUuid));
        if (s == null) return null;
        BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(charUuid));
        if (c != null) charCache.put(k, c);
        return c;
    }

    /* ====================== Queued GATT Ops ====================== */

    private void enqueue(Runnable op) {
        opQueue.add(op);
        if (!opInFlight) nextOp();
    }

    private void nextOp() {
        Runnable op = opQueue.poll();
        if (op == null) { opInFlight = false; return; }
        opInFlight = true;
        try { op.run(); }
        catch (Throwable t) {
            Log.e(TAG, "Op failed: " + t.getMessage());
            opInFlight = false;
            nextOp();
        }
    }

    private void opDone() {
        opInFlight = false;
        nextOp();
    }

    /* ====================== Public GATT APIs ====================== */

    public boolean discoverServices() {
        if (gatt == null) return false;
        enqueue(() -> gatt.discoverServices());
        return true;
    }

    public boolean readCharacteristic(String serviceUuid, String charUuid) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        enqueue(() -> { if (!gatt.readCharacteristic(ch)) opDone(); });
        return true;
    }

    public boolean writeCharacteristic(String serviceUuid, String charUuid, byte[] data, boolean noResponse) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        enqueue(() -> {
            ch.setValue(data);
            if (Build.VERSION.SDK_INT >= 33) {
                int writeType = noResponse ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                int res = gatt.writeCharacteristic(ch, data, writeType);
                if (res != BluetoothStatusCodes.SUCCESS) opDone();
            } else {
                ch.setWriteType(noResponse ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                if (!gatt.writeCharacteristic(ch)) opDone();
            }
        });
        return true;
    }

    /** Long write chunked according to current MTU (ATT payload = MTU - 3) */
    public boolean writeLongCharacteristic(String serviceUuid, String charUuid, byte[] data, boolean noResponse) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        enqueue(new ChunkedWriter(ch, data, noResponse));
        return true;
    }

    private class ChunkedWriter implements Runnable {
        private final BluetoothGattCharacteristic ch;
        private final byte[] data;
        private final boolean noResp;
        private int offset = 0;
        ChunkedWriter(BluetoothGattCharacteristic ch, byte[] data, boolean noResp) {
            this.ch = ch; this.data = data; this.noResp = noResp;
        }
        @Override public void run() {
            int chunk = Math.max(1, currentMtu - 3);
            int end = Math.min(data.length, offset + chunk);
            byte[] slice = java.util.Arrays.copyOfRange(data, offset, end);
            ch.setValue(slice);
            boolean started;
            if (Build.VERSION.SDK_INT >= 33) {
                int type = noResp ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                int res = gatt.writeCharacteristic(ch, slice, type);
                started = (res == BluetoothStatusCodes.SUCCESS);
            } else {
                ch.setWriteType(noResp ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                started = gatt.writeCharacteristic(ch);
            }
            if (!started) { opDone(); return; }
            offset = end;
            if (offset < data.length) {
                // re-enqueue remaining chunk; the write callback will call opDone() which triggers nextOp()
                opQueue.addFirst(this);
                // leave opInFlight true so nextOp doesn't start other ops until this sequence completes
            }
        }
    }

    public boolean setNotify(String serviceUuid, String charUuid, boolean enable, boolean indicate) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        enqueue(() -> {
            boolean ok = gatt.setCharacteristicNotification(ch, enable);
            BluetoothGattDescriptor cccd = ch.getDescriptor(UUID_CCCD);
            if (cccd != null) {
                if (indicate) cccd.setValue(enable ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                else cccd.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                if (Build.VERSION.SDK_INT >= 33) {
                    int res = gatt.writeDescriptor(cccd, cccd.getValue());
                    if (res != BluetoothStatusCodes.SUCCESS) opDone();
                } else {
                    if (!gatt.writeDescriptor(cccd)) opDone();
                }
            } else {
                // no CCCD available; still notify local success
                post(() -> { if (listener != null) listener.onDescriptorWrite(ch.getService().getUuid().toString(), ch.getUuid().toString(), UUID_CCCD.toString(), new byte[0], 0); });
                opDone();
            }
        });
        return true;
    }

    public boolean readDescriptor(String serviceUuid, String charUuid, String descUuid) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        BluetoothGattDescriptor d = ch.getDescriptor(UUID.fromString(descUuid));
        if (d == null) return false;
        enqueue(() -> { if (!gatt.readDescriptor(d)) opDone(); });
        return true;
    }

    public boolean writeDescriptor(String serviceUuid, String charUuid, String descUuid, byte[] value) {
        BluetoothGattCharacteristic ch = findChar(serviceUuid, charUuid);
        if (ch == null || gatt == null) return false;
        BluetoothGattDescriptor d = ch.getDescriptor(UUID.fromString(descUuid));
        if (d == null) return false;
        enqueue(() -> {
            d.setValue(value);
            if (Build.VERSION.SDK_INT >= 33) {
                int res = gatt.writeDescriptor(d, d.getValue());
                if (res != BluetoothStatusCodes.SUCCESS) opDone();
            } else {
                if (!gatt.writeDescriptor(d)) opDone();
            }
        });
        return true;
    }

    /* ====================== Advanced controls ====================== */

    public boolean requestMtu(int mtu) {
        if (gatt == null) return false;
        enqueue(() -> { if (!gatt.requestMtu(mtu)) opDone(); });
        return true;
    }

    /** priority: 0 = BALANCED, 1 = HIGH, 2 = LOW_POWER */
    public boolean requestConnectionPriority(int priority) {
        if (gatt == null) return false;
        int p = BluetoothGatt.CONNECTION_PRIORITY_BALANCED;
        if (priority == 1) p = BluetoothGatt.CONNECTION_PRIORITY_HIGH;
        else if (priority == 2) p = BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER;
        final int finalP = p;
        enqueue(() -> { try { gatt.requestConnectionPriority(finalP); } catch (Throwable t) { opDone(); }});
        return true;
    }

    public boolean readRemoteRssi() {
        if (gatt == null) return false;
        enqueue(() -> { if (!gatt.readRemoteRssi()) opDone(); });
        return true;
    }

    public boolean setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {
        if (gatt == null || Build.VERSION.SDK_INT < 26) return false;
        enqueue(() -> { gatt.setPreferredPhy(txPhy, rxPhy, phyOptions); });
        return true;
    }

    public boolean readPhy() {
        if (gatt == null || Build.VERSION.SDK_INT < 26) return false;
        enqueue(() -> { gatt.readPhy(); });
        return true;
    }

    /* ====================== Misc ====================== */

    public int getCurrentMtu() { return currentMtu; }

    public String getConnectedDeviceAddress() {
        return (gatt != null && gatt.getDevice() != null) ? gatt.getDevice().getAddress() : "";
    }
}
