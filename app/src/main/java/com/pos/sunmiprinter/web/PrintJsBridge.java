package com.pos.sunmiprinter.web;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.pos.sunmiprinter.AppSettings;
import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;

public class PrintJsBridge {

    private static final String TAG = "PrintJsBridge";

    private final Context context;
    private final WebView webView;
    private final SunmiPrinterManager sunmi;
    private final BluetoothPrinterManager bt;
    private final NetworkPrinterManager net;
    private final AppSettings settings;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public PrintJsBridge(Context context, WebView webView, SunmiPrinterManager sunmi) {
        this.context = context;
        this.webView = webView;
        this.sunmi = sunmi;
        this.bt = new BluetoothPrinterManager();
        this.net = new NetworkPrinterManager();
        this.settings = new AppSettings(context);
    }

    // ==================== 设定 ====================

    @JavascriptInterface
    public String getSettings() {
        return settings.toJson();
    }

    @JavascriptInterface
    public void saveSettings(String jsonStr) {
        settings.fromJson(jsonStr);
    }

    @JavascriptInterface
    public void resetSettings() {
        settings.resetAll();
    }

    // ==================== 内建印表机 — 状态 ====================

    @JavascriptInterface
    public boolean isPrinterReady() {
        return sunmi.isConnected();
    }

    @JavascriptInterface
    public int getPrinterStatus() {
        return sunmi.getPrinterStatus();
    }

    // ==================== 内建印表机 — 基本列印 ====================

    @JavascriptInterface
    public boolean printText(String text) {
        return sunmi.printText(text);
    }

    @JavascriptInterface
    public boolean printTextWithFont(String text, String typeface, float size) {
        return sunmi.printTextWithFont(text, typeface, size);
    }

    @JavascriptInterface
    public boolean printColumns(String[] texts, int[] widths, int[] aligns) {
        return sunmi.printColumns(texts, widths, aligns);
    }

    @JavascriptInterface
    public boolean printBitmapBase64(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return false;
            return sunmi.printBitmap(bmp);
        } catch (Exception e) {
            Log.e(TAG, "printBitmapBase64 error", e);
            return false;
        }
    }

    @JavascriptInterface
    public boolean printBarcode(String data, int symbology, int height, int width, int textPosition) {
        return sunmi.printBarcode(data, symbology, height, width, textPosition);
    }

    @JavascriptInterface
    public boolean printQRCode(String data, int moduleSize, int errorLevel) {
        return sunmi.printQRCode(data, moduleSize, errorLevel);
    }

    // ==================== 内建印表机 — 收据列印 ====================

    @JavascriptInterface
    public boolean printReceipt(String title, String body) {
        return sunmi.printReceipt(title, body);
    }

    @JavascriptInterface
    public boolean printPosReceipt(String jsonStr) {
        return sunmi.printPosReceipt(jsonStr);
    }

    @JavascriptInterface
    public boolean printReceiptJson(String jsonStr) {
        return sunmi.printReceiptJson(jsonStr);
    }

    @JavascriptInterface
    public boolean printHtml(String title, String html) {
        return sunmi.printHtml(title, html);
    }

    @JavascriptInterface
    public boolean printTestReceipt() {
        return sunmi.printTestReceipt();
    }

    @JavascriptInterface
    public void printCurrentPage() {
        handler.post(() -> webView.evaluateJavascript(
            "(function(){ return document.title + '\\n' + document.body.innerText; })()",
            value -> {
                if (value != null) {
                    String text = value.replace("\\n", "\n")
                                       .replace("\\\"", "\"");
                    if (text.startsWith("\"")) text = text.substring(1);
                    if (text.endsWith("\"")) text = text.substring(0, text.length() - 1);
                    sunmi.printReceipt("页面列印", text);
                }
            }
        ));
    }

    // ==================== 内建印表机 — 硬体控制 ====================

    @JavascriptInterface
    public boolean cutPaper() {
        return sunmi.cutPaper();
    }

    @JavascriptInterface
    public boolean openCashDrawer() {
        boolean ok = sunmi.openCashDrawer();
        handler.post(() -> toast(ok ? "钱箱已开启" : "开钱箱失败"));
        return ok;
    }

    @JavascriptInterface
    public boolean buzzer() {
        return sunmi.buzzer();
    }

    @JavascriptInterface
    public boolean sendRawData(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return sunmi.sendRawData(data);
        } catch (Exception e) {
            Log.e(TAG, "sendRawData error", e);
            return false;
        }
    }

    // ==================== 蓝牙印表机 ====================

    @JavascriptInterface
    public String getBtPrinters() {
        return bt.getPairedPrintersJson();
    }

    @JavascriptInterface
    public boolean connectBtPrinter(final String address) {
        final boolean[] result = {false};
        Thread t = new Thread(() -> result[0] = bt.connect(address));
        t.start();
        try { t.join(5000); } catch (InterruptedException ignored) {}
        if (result[0]) {
            settings.setBtAddress(address);
            settings.setBtEnabled(true);
        }
        handler.post(() -> toast(result[0] ? "蓝牙印表机已连线" : "蓝牙连线失败"));
        return result[0];
    }

    @JavascriptInterface
    public void disconnectBtPrinter() {
        bt.disconnect();
        handler.post(() -> toast("蓝牙印表机已断线"));
    }

    @JavascriptInterface
    public boolean isBtPrinterConnected() {
        return bt.isConnected();
    }

    @JavascriptInterface
    public String getBtConnectedAddress() {
        return bt.getConnectedAddress();
    }

    @JavascriptInterface
    public boolean btPrintText(String text) {
        return bt.printText(text);
    }

    @JavascriptInterface
    public boolean btPrintReceipt(String jsonStr) {
        return bt.printPosReceipt(jsonStr);
    }

    @JavascriptInterface
    public boolean btPrintKitchen(String jsonStr) {
        return bt.printKitchenReceipt(jsonStr);
    }

    @JavascriptInterface
    public boolean btPrintBarcode(String data, int type) {
        return bt.printBarcode(data, type);
    }

    @JavascriptInterface
    public boolean btPrintQRCode(String data, int moduleSize) {
        return bt.printQRCode(data, moduleSize);
    }

    @JavascriptInterface
    public boolean btCutPaper() {
        return bt.cutPaper();
    }

    @JavascriptInterface
    public boolean btOpenCashDrawer() {
        return bt.openCashDrawer();
    }

    @JavascriptInterface
    public boolean btBuzzer() {
        return bt.buzzer();
    }

    @JavascriptInterface
    public boolean btSendRawData(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return bt.sendRawData(data);
        } catch (Exception e) {
            Log.e(TAG, "btSendRawData error", e);
            return false;
        }
    }

    // ==================== 网路印表机 ====================

    @JavascriptInterface
    public boolean connectNetPrinter(final String ip, final int port) {
        final boolean[] result = {false};
        Thread t = new Thread(() -> result[0] = net.connect(ip, port));
        t.start();
        try { t.join(5000); } catch (InterruptedException ignored) {}
        if (result[0]) {
            settings.setNetIp(ip);
            settings.setNetPort(port);
            settings.setNetEnabled(true);
        }
        handler.post(() -> toast(result[0] ? "网路印表机已连线 " + ip : "网路连线失败"));
        return result[0];
    }

    @JavascriptInterface
    public void disconnectNetPrinter() {
        net.disconnect();
        handler.post(() -> toast("网路印表机已断线"));
    }

    @JavascriptInterface
    public boolean isNetPrinterConnected() {
        return net.isConnected();
    }

    @JavascriptInterface
    public String getNetConnectedInfo() {
        return net.getConnectedInfo();
    }

    @JavascriptInterface
    public boolean netPrintText(String text) {
        return net.printText(text);
    }

    @JavascriptInterface
    public boolean netPrintReceipt(String jsonStr) {
        return net.printPosReceipt(jsonStr);
    }

    @JavascriptInterface
    public boolean netPrintKitchen(String jsonStr) {
        return net.printKitchenReceipt(jsonStr);
    }

    @JavascriptInterface
    public boolean netPrintBarcode(String data, int type) {
        return net.printBarcode(data, type);
    }

    @JavascriptInterface
    public boolean netPrintQRCode(String data, int moduleSize) {
        return net.printQRCode(data, moduleSize);
    }

    @JavascriptInterface
    public boolean netCutPaper() {
        return net.cutPaper();
    }

    @JavascriptInterface
    public boolean netOpenCashDrawer() {
        return net.openCashDrawer();
    }

    @JavascriptInterface
    public boolean netBuzzer() {
        return net.buzzer();
    }

    @JavascriptInterface
    public boolean netSendRawData(String base64) {
        try {
            byte[] data = Base64.decode(base64, Base64.DEFAULT);
            return net.sendRawData(data);
        } catch (Exception e) {
            Log.e(TAG, "netSendRawData error", e);
            return false;
        }
    }

    // ==================== 内部方法（供 MainActivity 调用）====================

    public boolean connectBtPrinterInternal(String address) {
        return bt.connect(address);
    }

    public boolean connectNetPrinterInternal(String ip, int port) {
        return net.connect(ip, port);
    }

    // ==================== 工具 ====================

    private void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
}
