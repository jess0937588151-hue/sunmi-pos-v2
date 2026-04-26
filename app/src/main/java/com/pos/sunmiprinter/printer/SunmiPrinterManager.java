package com.pos.sunmiprinter.printer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import woyou.aidlservice.jiuiv5.ICallback;
import woyou.aidlservice.jiuiv5.IWoyouService;

public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinter";
    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY = 500;

    private IWoyouService printerService;
    private final Context context;
    private final SunmiCallbackAdapter callbackAdapter = new SunmiCallbackAdapter();
    private boolean bound = false;

    public SunmiPrinterManager(Context context) {
        this.context = context;
    }

    // ==================== 连线管理 ====================

    public void bind() {
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        if (bound) {
            context.unbindService(serviceConnection);
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

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            bound = true;
            Log.d(TAG, "Printer service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            bound = false;
            Log.w(TAG, "Printer service disconnected");
        }
    };

    // ==================== 基本列印 ====================

    public boolean printText(String text) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printTextWithFont(text + "\n", "", 24, callbackAdapter);
        });
    }

    public boolean printTextWithFont(String text, String typeface, float size) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printTextWithFont(text + "\n", typeface, size, callbackAdapter);
        });
    }

    public boolean printColumns(String[] texts, int[] widths, int[] aligns) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printColumnsText(texts, widths, aligns, callbackAdapter);
        });
    }

    public boolean printBitmap(Bitmap bitmap) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printBitmap(bitmap, callbackAdapter);
        });
    }

    public boolean printBarcode(String data, int symbology, int height, int width, int textPosition) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printBarCode(data, symbology, height, width, textPosition, callbackAdapter);
        });
    }

    public boolean printQRCode(String data, int moduleSize, int errorLevel) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.printQRCode(data, moduleSize, errorLevel, callbackAdapter);
        });
    }

    // ==================== 收据列印 ====================

    public boolean printReceipt(String title, String body) {
        return retry(() -> {
            printerService.printerInit(callbackAdapter);
            printerService.setAlignment(1, callbackAdapter);
            printerService.printTextWithFont(title + "\n", "", 32, callbackAdapter);
            printerService.setAlignment(0, callbackAdapter);
            printerService.printTextWithFont(body + "\n", "", 24, callbackAdapter);
            feedAndCut();
        });
    }

    public boolean printPosReceipt(String jsonStr) {
        return retry(() -> {
            JSONObject data = new JSONObject(jsonStr);
            printerService.printerInit(callbackAdapter);

            // 店名
            printerService.setAlignment(1, callbackAdapter);
            printerService.printTextWithFont(
                    data.optString("shopName", "POS") + "\n", "", 36, callbackAdapter);

            // 副标题
            String subtitle = data.optString("subtitle", "");
            if (!subtitle.isEmpty()) {
                printerService.printTextWithFont(subtitle + "\n", "", 22, callbackAdapter);
            }

            printerService.printTextWithFont("--------------------------------\n", "", 24, callbackAdapter);
            printerService.setAlignment(0, callbackAdapter);

            // 订单资讯
            String orderNo = data.optString("orderNumber", "");
            if (!orderNo.isEmpty()) {
                printerService.printTextWithFont("单号: " + orderNo + "\n", "", 24, callbackAdapter);
            }
            String dateTime = data.optString("dateTime", "");
            if (!dateTime.isEmpty()) {
                printerService.printTextWithFont("时间: " + dateTime + "\n", "", 24, callbackAdapter);
            }
            String orderType = data.optString("orderType", "");
            if (!orderType.isEmpty()) {
                printerService.printTextWithFont("类型: " + orderType + "\n", "", 24, callbackAdapter);
            }
            String payment = data.optString("paymentMethod", "");
            if (!payment.isEmpty()) {
                printerService.printTextWithFont("付款: " + payment + "\n", "", 24, callbackAdapter);
            }

            printerService.printTextWithFont("--------------------------------\n", "", 24, callbackAdapter);

            // 品项
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
                            callbackAdapter);

                    if (!options.isEmpty()) {
                        printerService.printTextWithFont("  " + options + "\n", "", 20, callbackAdapter);
                    }
                }
            }

            printerService.printTextWithFont("--------------------------------\n", "", 24, callbackAdapter);

            // 总计
            printerService.setAlignment(2, callbackAdapter);
            printerService.printTextWithFont(
                    "合计: $" + data.optString("total", "0") + "\n", "", 32, callbackAdapter);
            printerService.setAlignment(0, callbackAdapter);

            printerService.printTextWithFont("--------------------------------\n", "", 24, callbackAdapter);
            printerService.setAlignment(1, callbackAdapter);
            printerService.printTextWithFont("谢谢光临\n", "", 24, callbackAdapter);
            printerService.setAlignment(0, callbackAdapter);

            feedAndCut();
            openCashDrawer();

            Log.d(TAG, "POS receipt printed successfully");
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
            printerService.printerInit(callbackAdapter);
            printerService.setAlignment(1, callbackAdapter);
            printerService.printTextWithFont("** 测试收据 **\n", "", 32, callbackAdapter);
            printerService.setAlignment(0, callbackAdapter);
            printerService.printTextWithFont("印表机运作正常\n", "", 24, callbackAdapter);
            printerService.printTextWithFont("时间: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n", "", 24, callbackAdapter);
            feedAndCut();
        });
    }

    // ==================== 硬体控制 ====================

    public boolean cutPaper() {
        if (!isConnected()) return false;
        try {
            printerService.lineWrap(3, callbackAdapter);
            byte[] cmd = {0x1D, 0x56, 0x01};
            printerService.sendRAWData(cmd, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "cutPaper error", e);
            return false;
        }
    }

    public boolean openCashDrawer() {
        if (!isConnected()) return false;
        try {
            byte[] cmd = {0x10, 0x14, 0x01, 0x00, 0x05};
            printerService.sendRAWData(cmd, callbackAdapter);
            Log.d(TAG, "Cash drawer opened");
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "openCashDrawer error", e);
            return false;
        }
    }

    public boolean buzzer() {
        if (!isConnected()) return false;
        try {
            byte[] cmd = {0x1B, 0x42, 0x03, 0x03};
            printerService.sendRAWData(cmd, callbackAdapter);
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
            printerService.sendRAWData(data, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "sendRawData error", e);
            return false;
        }
    }

    // ==================== 内部工具 ====================

    private void feedAndCut() throws RemoteException {
        printerService.lineWrap(4, callbackAdapter);
        byte[] cutCmd = {0x1D, 0x56, 0x01};
        printerService.sendRAWData(cutCmd, callbackAdapter);
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
