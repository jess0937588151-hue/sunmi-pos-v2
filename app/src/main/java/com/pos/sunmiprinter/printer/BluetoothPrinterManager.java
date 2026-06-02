package com.pos.sunmiprinter.printer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterManager {

    private static final String TAG = "BtPrinter";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY = 500;
    private static final Charset GBK = Charset.forName("GBK");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private String connectedAddress = "";

    public BluetoothPrinterManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    // ==================== 连线管理 ====================

    public String getPairedPrintersJson() {
        JSONArray arr = new JSONArray();
        if (bluetoothAdapter == null) return arr.toString();
        try {
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            if (devices != null) {
                for (BluetoothDevice d : devices) {
                    JSONObject obj = new JSONObject();
                    obj.put("name", d.getName() != null ? d.getName() : "Unknown");
                    obj.put("address", d.getAddress());
                    arr.put(obj);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getPairedPrinters error", e);
        }
        return arr.toString();
    }

    public boolean connect(String address) {
        disconnect();
        if (bluetoothAdapter == null) return false;
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            bluetoothAdapter.cancelDiscovery();
            socket.connect();
            outputStream = socket.getOutputStream();
            connectedAddress = address;
            Log.d(TAG, "Connected to " + address);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "connect error", e);
            disconnect();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (outputStream != null) { outputStream.close(); outputStream = null; }
            if (socket != null) { socket.close(); socket = null; }
            connectedAddress = "";
        } catch (IOException e) {
            Log.e(TAG, "disconnect error", e);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && outputStream != null;
    }

    public String getConnectedAddress() {
        return connectedAddress;
    }

    // ==================== 基本列印 ====================

    public boolean printText(String text) {
        return retry(() -> {
            write(ESC_INIT);
            write(text.getBytes(GBK));
            write(LF);
        });
    }

        public boolean printTextWithFont(String text, int size, boolean bold) {
        return retry(() -> {
            write(ESC_INIT);
            if (bold) write(ESC_BOLD_ON);
            write(gsSizeByPoint(size));
            write(text.getBytes(GBK));
            write(LF);
            write(ESC_BOLD_OFF);
            write(GS_NORMAL_SIZE);
        });
    }

    /** 點數換算 ESC/POS 放大倍率：24-31→1x, 32-47→2x, 48以上→3x */
    private byte[] gsSizeByPoint(int pt) {
        if (pt >= 48) return GS_TRIPLE_SIZE;
        if (pt >= 32) return GS_DOUBLE_SIZE;
        return GS_NORMAL_SIZE;
    }


    public boolean printColumns(String left, String right, int totalWidth) {
        return retry(() -> {
            write(ESC_INIT);
            int rightLen = right.length();
            int leftMax = totalWidth - rightLen;
            String leftTrim = left.length() > leftMax ? left.substring(0, leftMax) : left;
            int padding = totalWidth - leftTrim.length() - rightLen;
            StringBuilder sb = new StringBuilder();
            sb.append(leftTrim);
            for (int i = 0; i < padding; i++) sb.append(' ');
            sb.append(right);
            write(sb.toString().getBytes(GBK));
            write(LF);
        });
    }

    public boolean printBitmap(byte[] bitmapData) {
        return retry(() -> {
            write(ESC_INIT);
            write(bitmapData);
            write(LF);
        });
    }

    public boolean printBarcode(String data, int type) {
        return retry(() -> {
            write(ESC_INIT);
            write(new byte[]{0x1D, 0x68, 0x50});
            write(new byte[]{0x1D, 0x77, 0x02});
            write(new byte[]{0x1D, 0x48, 0x02});
            write(new byte[]{0x1D, 0x6B, (byte) type, (byte) data.length()});
            write(data.getBytes(GBK));
        });
    }

    public boolean printQRCode(String data, int moduleSize) {
        return retry(() -> {
            byte[] d = data.getBytes(GBK);
            int len = d.length + 3;
            write(ESC_INIT);
            write(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00});
            write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) moduleSize});
            write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31});
            write(new byte[]{0x1D, 0x28, 0x6B, (byte) (len & 0xFF), (byte) ((len >> 8) & 0xFF), 0x31, 0x50, 0x30});
            write(d);
            write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});
        });
    }

    // ==================== 收据列印 ====================

    public boolean printReceipt(String title, String body) {
        return retry(() -> {
            write(ESC_INIT);
            write(ESC_CENTER);
            write(GS_DOUBLE_SIZE);
            write(title.getBytes(GBK));
            write(LF);
            write(GS_NORMAL_SIZE);
            write(ESC_LEFT);
            write(SEPARATOR.getBytes(GBK));
            write(LF);
            write(body.getBytes(GBK));
            write(LF);
            feedAndCut();
        });
    }

            public boolean printPosReceipt(String jsonStr) {
        return retry(() -> {
            JSONObject data = new JSONObject(jsonStr);
            // mode=="kitchen" 直接轉廚房格式，避免印出金額/合計/謝謝光臨
            if ("kitchen".equals(data.optString("mode", "receipt"))) {
                printKitchenReceipt(jsonStr);
                return;
            }
            // v20260620 顧客單：讀 fields 開關，未勾選的欄位一律不印（修藍牙不照欄位篩選/亂印金額）
            JSONObject f = data.optJSONObject("fields");
            write(ESC_INIT);

            // 店名
            if (fieldOn(f, "storeName")) {
                write(ESC_CENTER);
                write(GS_DOUBLE_SIZE);
                write(data.optString("shopName", "POS").getBytes(GBK));
                write(LF);
                write(GS_NORMAL_SIZE);
            }

            // 副标题
            String subtitle = data.optString("subtitle", "");
            if (!subtitle.isEmpty()) {
                write(ESC_CENTER);
                write(subtitle.getBytes(GBK));
                write(LF);
            }

            write(ESC_LEFT);
            if (fieldOn(f, "storePhone")) writeLineIfNotEmpty("电话: ", data.optString("shopPhone", ""));
            if (fieldOn(f, "storeAddress")) writeLineIfNotEmpty("地址: ", data.optString("shopAddress", ""));

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            // 订单资讯（各自看 fields）
            if (fieldOn(f, "orderNo"))        writeLineIfNotEmpty("单号: ", data.optString("orderNumber", ""));
            if (fieldOn(f, "dateTime"))       writeLineIfNotEmpty("时间: ", data.optString("dateTime", ""));
            if (fieldOn(f, "orderType"))      writeLineIfNotEmpty("类型: ", data.optString("orderType", ""));
            if (fieldOn(f, "paymentMethod"))  writeLineIfNotEmpty("付款: ", data.optString("paymentMethod", ""));
            if (fieldOn(f, "customerInfo")) {
                writeLineIfNotEmpty("顾客: ", data.optString("customerName", ""));
                writeLineIfNotEmpty("电话: ", data.optString("customerPhoneMasked", ""));
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            // 品项
            boolean showItems = fieldOn(f, "items");
            boolean showQty   = fieldOn(f, "itemQty");
            boolean showPrice = fieldOn(f, "itemPrice");
            boolean showSel   = fieldOn(f, "itemSelections");
            boolean showNote  = fieldOn(f, "itemNote");
            JSONArray items = data.optJSONArray("items");
            if (showItems && items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int qty = item.optInt("qty", 0);
                    int price = item.optInt("price", 0);

                    String left = name + (showQty ? " x" + qty : "");
                    String right = showPrice ? ("$" + price) : "";
                    int pad = 32 - left.length() - right.length();
                    if (pad < 1) pad = 1;
                    StringBuilder sb = new StringBuilder(left);
                    for (int p = 0; p < pad; p++) sb.append(' ');
                    sb.append(right);
                    write(sb.toString().getBytes(GBK));
                    write(LF);

                    if (showSel) {
                        String options = item.optString("options", "");
                        if (!options.isEmpty()) {
                            write(("  " + options).getBytes(GBK));
                            write(LF);
                        }
                    }
                    if (showNote) {
                        String note = item.optString("note", "");
                        if (!note.isEmpty()) {
                            write(("  备注: " + note).getBytes(GBK));
                            write(LF);
                        }
                    }
                }
            }

            // 整单备注
            if (fieldOn(f, "orderNote")) {
                String cNote = data.optString("customerNote", "");
                if (!cNote.isEmpty()) {
                    write(SEPARATOR.getBytes(GBK));
                    write(LF);
                    write(("整单备注: " + cNote).getBytes(GBK));
                    write(LF);
                }
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            // 金额（各自看 fields，未勾选不印）
            write(ESC_RIGHT);
            if (fieldOn(f, "subtotal")) {
                String st = data.optString("subtotal", "0");
                if (!"0".equals(st) && !st.isEmpty()) { write(("小计: $" + st).getBytes(GBK)); write(LF); }
            }
            if (fieldOn(f, "discount")) {
                String dc = data.optString("discountAmount", "0");
                if (!"0".equals(dc) && !dc.isEmpty()) { write(("折扣: -$" + dc).getBytes(GBK)); write(LF); }
            }
            if (fieldOn(f, "total")) {
                write(GS_DOUBLE_SIZE);
                write(("合计: $" + data.optString("total", "0")).getBytes(GBK));
                write(LF);
                write(GS_NORMAL_SIZE);
            }
            write(ESC_LEFT);

            // 页尾（footer，未勾选不印；取代写死的「谢谢光临」）
            if (fieldOn(f, "footer")) {
                String footer = data.optString("footer", "");
                if (!footer.isEmpty()) {
                    write(SEPARATOR.getBytes(GBK));
                    write(LF);
                    write(ESC_CENTER);
                    write(footer.getBytes(GBK));
                    write(LF);
                    write(ESC_LEFT);
                }
            }

            feedAndCut();
            // 开钱箱只在 payload 要求时（廚房單不会带 openDrawer=true）
            if (data.optBoolean("openDrawer", false)) openCashDrawer();

            Log.d(TAG, "BT POS receipt printed (fields applied)");
        });
    }


       public boolean printKitchenReceipt(String jsonStr) {
        return retry(() -> {
            JSONObject data = new JSONObject(jsonStr);
            // v20260620 廚房單：讀 fields 開關 + 字級點數（修不照欄位篩選/字體沒放大）
            JSONObject f = data.optJSONObject("fields");
            int fKItem   = data.optInt("fontKitchenItem", 32); // 品名字級點數
            int fKInfo   = data.optInt("fontKitchenInfo", 24); // 選項/資訊字級點數
            write(ESC_INIT);

            // 标题
            write(ESC_CENTER);
            write(GS_DOUBLE_SIZE);
            write("** 厨房单 **".getBytes(GBK));
            write(LF);
            write(GS_NORMAL_SIZE);

            if (fieldOn(f, "storeName")) {
                String shopName = data.optString("shopName", "");
                if (!shopName.isEmpty()) {
                    write(shopName.getBytes(GBK));
                    write(LF);
                }
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);
            write(ESC_LEFT);

            if (fieldOn(f, "orderNo"))   writeLineIfNotEmpty("单号: ", data.optString("orderNumber", ""));
            if (fieldOn(f, "dateTime"))  writeLineIfNotEmpty("时间: ", data.optString("dateTime", ""));
            if (fieldOn(f, "orderType")) writeLineIfNotEmpty("类型: ", data.optString("orderType", ""));
            if (fieldOn(f, "customerInfo")) {
                writeLineIfNotEmpty("顾客: ", data.optString("customerName", ""));
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            // 品项（品名套 fontKitchenItem、选项套 fontKitchenInfo；字级换算成 ESC/POS 倍率）
            boolean showItems = fieldOn(f, "items");
            boolean showQty   = fieldOn(f, "itemQty");
            boolean showSel   = fieldOn(f, "itemSelections");
            boolean showNote  = fieldOn(f, "itemNote");
            JSONArray items = data.optJSONArray("items");
            if (showItems && items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    // 品名：用品名字级
                    write(gsSizeByPoint(fKItem));
                    String nameLine = item.optString("name", "") + (showQty ? " x" + item.optInt("qty", 0) : "");
                    write(nameLine.getBytes(GBK));
                    write(LF);
                    write(GS_NORMAL_SIZE);

                    // 选项：用资讯字级
                    if (showSel) {
                        String options = item.optString("options", "");
                        if (!options.isEmpty()) {
                            write(gsSizeByPoint(fKInfo));
                            write(("  " + options).getBytes(GBK));
                            write(LF);
                            write(GS_NORMAL_SIZE);
                        }
                    }
                    // 备注：用资讯字级
                    if (showNote) {
                        String note = item.optString("note", "");
                        if (!note.isEmpty()) {
                            write(gsSizeByPoint(fKInfo));
                            write(("  备注: " + note).getBytes(GBK));
                            write(LF);
                            write(GS_NORMAL_SIZE);
                        }
                    }
                }
            }

            // 整单备注
            if (fieldOn(f, "orderNote")) {
                String cNote = data.optString("customerNote", "");
                if (!cNote.isEmpty()) {
                    write(("整单备注: " + cNote).getBytes(GBK));
                    write(LF);
                }
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            feedAndCut();
            buzzer();

            Log.d(TAG, "BT kitchen receipt printed (fields + font applied)");
        });
    }


    // ==================== 硬体控制 ====================

    public boolean cutPaper() {
        if (!isConnected()) return false;
        try {
            feedAndCut();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "cutPaper error", e);
            return false;
        }
    }

    public boolean openCashDrawer() {
        if (!isConnected()) return false;
        try {
            write(new byte[]{0x10, 0x14, 0x01, 0x00, 0x05});
            return true;
        } catch (IOException e) {
            Log.e(TAG, "openCashDrawer error", e);
            return false;
        }
    }

    public boolean buzzer() {
        if (!isConnected()) return false;
        try {
            write(new byte[]{0x1B, 0x42, 0x03, 0x03});
            return true;
        } catch (IOException e) {
            Log.e(TAG, "buzzer error", e);
            return false;
        }
    }

    public boolean sendRawData(byte[] data) {
        if (!isConnected()) return false;
        try {
            write(data);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "sendRawData error", e);
            return false;
        }
    }

    // ==================== ESC/POS 常数 ====================

    private static final byte[] ESC_INIT = {0x1B, 0x40};
    private static final byte[] LF = {0x0A};
    private static final byte[] ESC_CENTER = {0x1B, 0x61, 0x01};
    private static final byte[] ESC_LEFT = {0x1B, 0x61, 0x00};
    private static final byte[] ESC_RIGHT = {0x1B, 0x61, 0x02};
    private static final byte[] ESC_BOLD_ON = {0x1B, 0x45, 0x01};
    private static final byte[] ESC_BOLD_OFF = {0x1B, 0x45, 0x00};
    private static final byte[] GS_DOUBLE_SIZE = {0x1D, 0x21, 0x11};
    private static final byte[] GS_TRIPLE_SIZE = {0x1D, 0x21, 0x22};
    private static final byte[] GS_NORMAL_SIZE = {0x1D, 0x21, 0x00};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x01};
    private static final byte[] OPEN_DRAWER = {0x10, 0x14, 0x01, 0x00, 0x05};
    private static final byte[] BUZZER = {0x1B, 0x42, 0x03, 0x03};
    private static final String SEPARATOR = "--------------------------------";
   

        // ==================== 内部工具 ====================

    /**
     * v20260620 讀 payload 的 fields 開關。
     * fields 不存在（舊版 POS 沒送）→ 視為全印（回 true），維持相容；
     * 存在 → 讀該欄位，缺哪個欄位也預設 true（跟 Sunmi 端 optBoolean(...,true) 一致）。
     */
    private boolean fieldOn(JSONObject fields, String key) {
        if (fields == null) return true;
        return fields.optBoolean(key, true);
    }

    private void write(byte[] data) throws IOException {
        if (outputStream == null) throw new IOException("Not connected");
        outputStream.write(data);
        outputStream.flush();
    }


    private void writeLineIfNotEmpty(String label, String value) throws IOException {
        if (!value.isEmpty()) {
            write((label + value).getBytes(GBK));
            write(LF);
        }
    }

    private void feedAndCut() throws IOException {
        write(LF);
        write(LF);
        write(LF);
        write(LF);
        write(CUT_PAPER);
    }

    private interface PrintTask {
        void run() throws Exception;
    }

    private boolean retry(PrintTask task) {
        if (!isConnected()) return false;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                task.run();
                return true;
            } catch (Exception e) {
                Log.w(TAG, "BT print attempt " + (i + 1) + " failed", e);
                if (i < MAX_RETRY - 1) {
                    try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
                }
            }
        }
        Log.e(TAG, "BT print failed after " + MAX_RETRY + " attempts");
        return false;
    }
}
