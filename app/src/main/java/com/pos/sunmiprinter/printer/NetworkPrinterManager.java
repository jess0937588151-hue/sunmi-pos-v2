package com.pos.sunmiprinter.printer;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

public class NetworkPrinterManager {

    private static final String TAG = "NetPrinter";
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY = 500;
    private static final Charset GBK = Charset.forName("GBK");

    private Socket socket;
    private OutputStream outputStream;
    private String connectedIp = "";
    private int connectedPort = 9100;

    // ==================== 连线管理 ====================

    public boolean connect(String ip, int port) {
        disconnect();
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);
            outputStream = socket.getOutputStream();
            connectedIp = ip;
            connectedPort = port;
            Log.d(TAG, "Connected to " + ip + ":" + port);
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
            connectedIp = "";
        } catch (IOException e) {
            Log.e(TAG, "disconnect error", e);
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() && outputStream != null;
    }

    public String getConnectedInfo() {
        if (!isConnected()) return "";
        return connectedIp + ":" + connectedPort;
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
            if (size > 24) write(GS_DOUBLE_SIZE); else write(GS_NORMAL_SIZE);
            write(text.getBytes(GBK));
            write(LF);
            write(ESC_BOLD_OFF);
            write(GS_NORMAL_SIZE);
        });
    }

    public boolean printColumns(String left, String right, int totalWidth) {
        return retry(() -> {
            write(ESC_INIT);
            int rightLen = right.length();
            int leftMax = totalWidth - rightLen;
            String leftTrim = left.length() > leftMax ? left.substring(0, leftMax) : left;
            int padding = totalWidth - leftTrim.length() - rightLen;
            StringBuilder sb = new StringBuilder(leftTrim);
            for (int i = 0; i < padding; i++) sb.append(' ');
            sb.append(right);
            write(sb.toString().getBytes(GBK));
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
            write(new byte[]{0x1D, 0x28, 0x6B, (byte)(len & 0xFF), (byte)((len >> 8) & 0xFF), 0x31, 0x50, 0x30});
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
            write(ESC_INIT);

            write(ESC_CENTER);
            write(GS_DOUBLE_SIZE);
            write(data.optString("shopName", "POS").getBytes(GBK));
            write(LF);
            write(GS_NORMAL_SIZE);

            String subtitle = data.optString("subtitle", "");
            if (!subtitle.isEmpty()) {
                write(subtitle.getBytes(GBK));
                write(LF);
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);
            write(ESC_LEFT);

            writeLineIfNotEmpty("单号: ", data.optString("orderNumber", ""));
            writeLineIfNotEmpty("时间: ", data.optString("dateTime", ""));
            writeLineIfNotEmpty("类型: ", data.optString("orderType", ""));
            writeLineIfNotEmpty("付款: ", data.optString("paymentMethod", ""));

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int qty = item.optInt("qty", 0);
                    int price = item.optInt("price", 0);

                    String left = name + " x" + qty;
                    String right = "$" + price;
                    int pad = 32 - left.length() - right.length();
                    StringBuilder sb = new StringBuilder(left);
                    for (int p = 0; p < pad; p++) sb.append(' ');
                    sb.append(right);
                    write(sb.toString().getBytes(GBK));
                    write(LF);

                    String options = item.optString("options", "");
                    if (!options.isEmpty()) {
                        write(("  " + options).getBytes(GBK));
                        write(LF);
                    }
                }
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            write(ESC_RIGHT);
            write(GS_DOUBLE_SIZE);
            write(("合计: $" + data.optString("total", "0")).getBytes(GBK));
            write(LF);
            write(GS_NORMAL_SIZE);
            write(ESC_LEFT);

            write(SEPARATOR.getBytes(GBK));
            write(LF);
            write(ESC_CENTER);
            write("谢谢光临".getBytes(GBK));
            write(LF);
            write(ESC_LEFT);

            feedAndCut();
            openCashDrawer();

            Log.d(TAG, "NET POS receipt printed");
        });
    }

    public boolean printKitchenReceipt(String jsonStr) {
        return retry(() -> {
            JSONObject data = new JSONObject(jsonStr);
            write(ESC_INIT);

            write(ESC_CENTER);
            write(GS_DOUBLE_SIZE);
            write("** 厨房单 **".getBytes(GBK));
            write(LF);
            write(GS_NORMAL_SIZE);

            String shopName = data.optString("shopName", "");
            if (!shopName.isEmpty()) {
                write(shopName.getBytes(GBK));
                write(LF);
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);
            write(ESC_LEFT);

            writeLineIfNotEmpty("单号: ", data.optString("orderNumber", ""));
            writeLineIfNotEmpty("时间: ", data.optString("dateTime", ""));
            writeLineIfNotEmpty("类型: ", data.optString("orderType", ""));

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    write(GS_DOUBLE_SIZE);
                    write((item.optString("name", "") + " x" + item.optInt("qty", 0)).getBytes(GBK));
                    write(LF);
                    write(GS_NORMAL_SIZE);

                    String options = item.optString("options", "");
                    if (!options.isEmpty()) {
                        write(("  " + options).getBytes(GBK));
                        write(LF);
                    }
                }
            }

            write(SEPARATOR.getBytes(GBK));
            write(LF);

            feedAndCut();
            buzzer();

            Log.d(TAG, "NET kitchen receipt printed");
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
            write(OPEN_DRAWER);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "openCashDrawer error", e);
            return false;
        }
    }

    public boolean buzzer() {
        if (!isConnected()) return false;
        try {
            write(BUZZER);
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
    private static final byte[] GS_NORMAL_SIZE = {0x1D, 0x21, 0x00};
    private static final byte[] CUT_PAPER = {0x1D, 0x56, 0x01};
    private static final byte[] OPEN_DRAWER = {0x10, 0x14, 0x01, 0x00, 0x05};
    private static final byte[] BUZZER = {0x1B, 0x42, 0x03, 0x03};
    private static final String SEPARATOR = "--------------------------------";

    // ==================== 内部工具 ====================

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
                Log.w(TAG, "NET print attempt " + (i + 1) + " failed", e);
                if (i < MAX_RETRY - 1) {
                    try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
                }
            }
        }
        Log.e(TAG, "NET print failed after " + MAX_RETRY + " attempts");
        return false;
    }
}
