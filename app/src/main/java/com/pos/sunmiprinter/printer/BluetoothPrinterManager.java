package com.pos.sunmiprinter.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterManager {

    private static final String TAG = "BTPrinter";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private OutputStream btOut;
    private String connectedAddress = null;

    public BluetoothPrinterManager() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public List<String[]> getPairedPrinters() {
        List<String[]> list = new ArrayList<>();
        if (btAdapter == null) return list;
        Set<BluetoothDevice> devices = btAdapter.getBondedDevices();
        if (devices == null) return list;
        for (BluetoothDevice d : devices) {
            list.add(new String[]{d.getName(), d.getAddress()});
        }
        return list;
    }

    public String getPairedPrintersJson() {
        List<String[]> list = getPairedPrinters();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(list.get(i)[0])
              .append("\",\"address\":\"").append(list.get(i)[1]).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean connect(String address) {
        disconnect();
        if (btAdapter == null || address == null) return false;
        try {
            BluetoothDevice device = btAdapter.getRemoteDevice(address);
            btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            btSocket.connect();
            btOut = btSocket.getOutputStream();
            connectedAddress = address;
            Log.d(TAG, "Connected to " + address);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Connect failed: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public void disconnect() {
        try { if (btOut != null) btOut.close(); } catch (Exception ignored) {}
        try { if (btSocket != null) btSocket.close(); } catch (Exception ignored) {}
        btOut = null;
        btSocket = null;
        connectedAddress = null;
    }

    public boolean isConnected() {
        return btSocket != null && btSocket.isConnected() && btOut != null;
    }

    public String getConnectedAddress() {
        return connectedAddress;
    }

    private byte[] toBytes(String text) {
        try { return text.getBytes("GBK"); }
        catch (UnsupportedEncodingException e) { return text.getBytes(); }
    }

    public boolean printKitchenReceipt(String jsonStr) {
        if (!isConnected()) return false;
        try {
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);

            // Init
            btOut.write(new byte[]{0x1B, 0x40});
            // Center
            btOut.write(new byte[]{0x1B, 0x61, 0x01});
            // Large font
            btOut.write(new byte[]{0x1D, 0x21, 0x11});
            btOut.write(toBytes(json.optString("shopName", "廚房出單") + "\n"));
            // Normal font
            btOut.write(new byte[]{0x1D, 0x21, 0x00});

            String subtitle = json.optString("subtitle", "");
            if (!subtitle.isEmpty()) btOut.write(toBytes(subtitle + "\n"));

            btOut.write(toBytes("--------------------------------\n"));

            // Left align
            btOut.write(new byte[]{0x1B, 0x61, 0x00});

            String orderNumber = json.optString("orderNumber", "");
            if (!orderNumber.isEmpty()) btOut.write(toBytes("單號：" + orderNumber + "\n"));
            String dateTime = json.optString("dateTime", "");
            if (!dateTime.isEmpty()) btOut.write(toBytes("時間：" + dateTime + "\n"));
            String orderType = json.optString("orderType", "");
            if (!orderType.isEmpty()) btOut.write(toBytes("類型：" + orderType + "\n"));

            btOut.write(toBytes("--------------------------------\n"));

            // Items - large font for kitchen
            btOut.write(new byte[]{0x1D, 0x21, 0x01});
            org.json.JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int qty = item.optInt("qty", 0);
                    btOut.write(toBytes(name + " x" + qty + "\n"));
                    String options = item.optString("options", "");
                    if (!options.isEmpty()) {
                        btOut.write(new byte[]{0x1D, 0x21, 0x00});
                        btOut.write(toBytes("  " + options + "\n"));
                        btOut.write(new byte[]{0x1D, 0x21, 0x01});
                    }
                }
            }
            btOut.write(new byte[]{0x1D, 0x21, 0x00});

            btOut.write(toBytes("--------------------------------\n"));

            // Feed and cut
            btOut.write(new byte[]{0x1B, 0x64, 0x04});
            btOut.write(new byte[]{0x1D, 0x56, 0x01});

            btOut.flush();
            Log.d(TAG, "Kitchen receipt printed via BT");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "printKitchenReceipt error: " + e.getMessage());
            return false;
        }
    }
}
