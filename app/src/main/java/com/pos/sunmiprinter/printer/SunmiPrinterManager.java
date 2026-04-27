package com.pos.sunmiprinter.printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.RemoteException;
import android.util.Log;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallback;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import org.json.JSONArray;
import org.json.JSONObject;

public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinter";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY = 500;

    private SunmiPrinterService printerService;
    private final Context context;
    private boolean bound = false;

    private final InnerResultCallback innerCallback = new InnerResultCallback() {
        @Override
        public void onRunResult(boolean isSuccess) { Log.d(TAG, "onRunResult=" + isSuccess); }
        @Override
        public void onReturnString(String result) { Log.d(TAG, "onReturnString=" + result); }
        @Override
        public void onRaiseException(int code, String msg) { Log.e(TAG, "onRaiseException code=" + code + " msg=" + msg); }
        @Override
        public void onPrintResult(int code, String msg) { Log.d(TAG, "onPrintResult code=" + code + " msg=" + msg); }
    };

    public SunmiPrinterManager(Context context) {
        this.context = context;
    }

    // ==================== 連線管理 ====================

    public void bind() {
        try {
            boolean result = InnerPrinterManager.getInstance().bindService(context, new InnerPrinterCallback() {
                @Override
                protected void onConnected(SunmiPrinterService service) {
                    printerService = service;
                    bound = true;
                    Log.d(TAG, "Printer service connected via library");
                }

                @Override
                protected void onDisconnected() {
                    printerService = null;
                    bound = false;
                    Log.w(TAG, "Printer service disconnected");
                }
            });
            Log.d(TAG, "bindService result=" + result);
        } catch (Exception e) {
            Log.e(TAG, "bind error", e);
        }
    }

    public void unbind() {
        if (bound) {
            try {
                InnerPrinterManager.getInstance().unBindService(context, null);
            } catch (Exception e) {
                Log.w(TAG, "unbind error", e);
            }
            bound = false;
            printerService = null;
        }
    }

    public boolean isConnected() {
        return bound && printerService != null;
    }

    public int getPrinterStatus() {
        if (!isConnected()) return -1;
        try {
            return printerService.updatePrinterState();
        } catch (RemoteException e) {
            Log.e(TAG, "getPrinterStatus error", e);
            return -1;
        }
    }

    // ==================== 基本列印 ====================

    public boolean printText(String text) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printTextWithFont(text + "\n", "", 24, innerCallback);
        });
    }

    public boolean printTextWithFont(String text, String typeface, float size) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printTextWithFont(text + "\n", typeface, size, innerCallback);
        });
    }

    public boolean printColumns(String[] texts, int[] widths, int[] aligns) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printColumnsText(texts, widths, aligns, innerCallback);
        });
    }

    public boolean printBitmap(Bitmap bitmap) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printBitmap(bitmap, innerCallback);
        });
    }

    public boolean printBarcode(String data, int symbology, int height, int width, int textPosition) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printBarCode(data, symbology, height, width, textPosition, innerCallback);
        });
    }

    public boolean printQRCode(String data, int moduleSize, int errorLevel) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.printQRCode(data, moduleSize, errorLevel, innerCallback);
        });
    }

    // ==================== 收據列印 ====================

    public boolean printReceipt(String title, String body) {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.setAlignment(1, innerCallback);
            printerService.printTextWithFont(title + "\n", "", 32, innerCallback);
            printerService.setAlignment(0, innerCallback);
            printerService.printTextWithFont(body + "\n", "", 24, innerCallback);
            feedAndCut();
        });
    }

    public boolean printPosReceipt(String jsonStr) {
        return retry(() -> {
            JSONObject data = new JSONObject(jsonStr);
            printerService.printerInit(innerCallback);

            printerService.setAlignment(1, innerCallback);
            printerService.printTextWithFont(
                    data.optString("shopName", "POS") + "\n", "", 36, innerCallback);

            String subtitle = data.optString("subtitle", "");
            if (!subtitle.isEmpty()) {
                printerService.printTextWithFont(subtitle + "\n", "", 22, innerCallback);
            }

            printerService.printTextWithFont("--------------------------------\n", "", 24, innerCallback);
            printerService.setAlignment(0, innerCallback);

            String orderNo = data.optString("orderNumber", "");
            if (!orderNo.isEmpty())
                printerService.printTextWithFont("單號: " + orderNo + "\n", "", 24, innerCallback);
            String dateTime = data.optString("dateTime", "");
            if (!dateTime.isEmpty())
                printerService.printTextWithFont("時間: " + dateTime + "\n", "", 24, innerCallback);
            String orderType = data.optString("orderType", "");
            if (!orderType.isEmpty())
                printerService.printTextWithFont("類型: " + orderType + "\n", "", 24, innerCallback);
            String payment = data.optString("paymentMethod", "");
            if (!payment.isEmpty())
                printerService.printTextWithFont("付款: " + payment + "\n", "", 24, innerCallback);

            printerService.printTextWithFont("--------------------------------\n", "", 24, innerCallback);

            JSONArray items = data.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int qty = item.optInt("qty", 0);
                    int price = item.optInt("price", 0);
                    String options = item.optString("options", "");

                    printerService.printColumnsText(
                            new String[]{name, "x" + qty, "$" + price},
                            new int[]{14, 6, 10},
                            new int[]{0, 1, 2},
                            innerCallback);

                    if (!options.isEmpty()) {
                        printerService.printTextWithFont("  " + options + "\n", "", 20, innerCallback);
                    }
                }
            }

            printerService.printTextWithFont("--------------------------------\n", "", 24, innerCallback);

            printerService.setAlignment(2, innerCallback);
            printerService.printTextWithFont(
                    "合計: $" + data.optString("total", "0") + "\n", "", 32, innerCallback);
            printerService.setAlignment(0, innerCallback);

            printerService.printTextWithFont("--------------------------------\n", "", 24, innerCallback);
            printerService.setAlignment(1, innerCallback);
            printerService.printTextWithFont("謝謝光臨\n", "", 24, innerCallback);
            printerService.setAlignment(0, innerCallback);

            feedAndCut();
            openCashDrawer();
        });
    }

    public boolean printReceiptJson(String jsonStr) {
        return printPosReceipt(jsonStr);
    }

    public boolean printHtml(String title, String htmlContent) {
        String plainText = htmlContent
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .trim();
        return printReceipt(title, plainText);
    }

    public boolean printTestReceipt() {
        return retry(() -> {
            printerService.printerInit(innerCallback);
            printerService.setAlignment(1, innerCallback);
            printerService.printTextWithFont("** 測試收據 **\n", "", 32, innerCallback);
            printerService.setAlignment(0, innerCallback);
            printerService.printTextWithFont("印表機運作正常\n", "", 24, innerCallback);
            printerService.printTextWithFont("時間: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n", "", 24, innerCallback);
            feedAndCut();
        });
    }

    // ==================== 硬體控制 ====================

    public boolean cutPaper() {
        if (!isConnected()) return false;
        try {
            printerService.lineWrap(3, innerCallback);
            printerService.cutPaper(innerCallback);
            return true;
        } catch (RemoteException e) {
            // 如果 cutPaper 方法不存在，用 RAW 指令
            try {
                printerService.lineWrap(3, innerCallback);
                printerService.sendRAWData(new byte[]{0x1D, 0x56, 0x01}, innerCallback);
                return true;
            } catch (RemoteException e2) {
                Log.e(TAG, "cutPaper error", e2);
                return false;
            }
        }
    }

    public boolean openCashDrawer() {
        if (!isConnected()) return false;
        try {
            printerService.openDrawer(innerCallback);
            Log.d(TAG, "Cash drawer opened via API");
            return true;
        } catch (Exception e) {
            // fallback to RAW
            try {
                printerService.sendRAWData(new byte[]{0x10, 0x14, 0x01, 0x00, 0x05}, innerCallback);
                Log.d(TAG, "Cash drawer opened via RAW");
                return true;
            } catch (RemoteException e2) {
                Log.e(TAG, "openCashDrawer error", e2);
                return false;
            }
        }
    }

    public boolean buzzer() {
        if (!isConnected()) return false;
        try {
            printerService.sendRAWData(new byte[]{0x1B, 0x42, 0x03, 0x03}, innerCallback);
            Log.d(TAG, "Buzzer triggered");
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "buzzer error", e);
            return false;
        }
    }

    public boolean sendRawData(byte[] data) {
        if (!isConnected()) return false;
        try {
            printerService.sendRAWData(data, innerCallback);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "sendRawData error", e);
            return false;
        }
    }

    // ==================== 內部工具 ====================

    private void feedAndCut() throws RemoteException {
        printerService.lineWrap(4, innerCallback);
        try {
            printerService.cutPaper(innerCallback);
        } catch (Exception e) {
            printerService.sendRAWData(new byte[]{0x1D, 0x56, 0x01}, innerCallback);
        }
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
                Log.w(TAG, "Print attempt " + (i + 1) + " failed", e);
                if (i < MAX_RETRY - 1) {
                    try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
                }
            }
        }
        Log.e(TAG, "Print failed after " + MAX_RETRY + " attempts");
        return false;
    }
}
