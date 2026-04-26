package com.pos.sunmiprinter.printer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import woyou.aidlservice.jiuiv5.IWoyouService;

public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinterManager";
    private static final String SERVICE_PACKAGE = "woyou.aidlservice.jiuiv5";
    private static final String SERVICE_ACTION = "woyou.aidlservice.jiuiv5.IWoyouService";

    private final Context appContext;
    private final SunmiCallbackAdapter callbackAdapter = new SunmiCallbackAdapter();
    private IWoyouService printerService;
    private boolean bound;

    public SunmiPrinterManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            bound = true;
            Log.d(TAG, "Sunmi printer service connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            printerService = null;
            Log.w(TAG, "Sunmi printer service disconnected");
        }
    };

    public void bind() {
        if (bound) return;
        Intent intent = new Intent();
        intent.setPackage(SERVICE_PACKAGE);
        intent.setAction(SERVICE_ACTION);
        try {
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind Sunmi printer service", e);
        }
    }

    public void unbind() {
        if (!bound) return;
        try {
            appContext.unbindService(serviceConnection);
        } catch (Exception e) {
            Log.w(TAG, "unbindService error", e);
        }
        bound = false;
        printerService = null;
    }

    public boolean isConnected() {
        return printerService != null;
    }

    public boolean printTestReceipt(String title, String url) {
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        StringBuilder body = new StringBuilder();
        body.append("測試連線成功\n");
        body.append("時間：").append(now).append("\n");
        body.append("頁面：").append(url).append("\n");
        body.append("裝置：SUNMI T2 / Android 7.1.1\n");
        body.append("------------------------------\n");
        body.append("可再擴充為：顧客單 / 廚房單 / 標籤\n");
        return printReceipt(title, body.toString());
    }

    public boolean printText(String text) {
        if (!isConnected() || TextUtils.isEmpty(text)) return false;
        try {
            printerService.printText(text.endsWith("\n") ? text : text + "\n", callbackAdapter);
            printerService.lineWrap(2, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "printText error", e);
            return false;
        }
    }

    public boolean printReceipt(String title, String body) {
        if (!isConnected()) return false;
        try {
            printerService.printerInit(callbackAdapter);
            printerService.setAlignment(1, callbackAdapter);
            printerService.setFontSize(30f, callbackAdapter);
            printerService.printText(safe(title) + "\n", callbackAdapter);
            printerService.lineWrap(1, callbackAdapter);

            printerService.setAlignment(0, callbackAdapter);
            printerService.setFontSize(22f, callbackAdapter);
            printerService.printText(safe(body), callbackAdapter);
            printerService.lineWrap(3, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "printReceipt error", e);
            return false;
        }
    }

    public boolean printPosReceipt(String jsonStr) {
        if (!isConnected()) return false;
        try {
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
            printerService.printerInit(callbackAdapter);

            // Shop name - centered, large
            printerService.setAlignment(1, callbackAdapter);
            printerService.setFontSize(32f, callbackAdapter);
            printerService.printText(json.optString("shopName", "POS") + "\n", callbackAdapter);

            // Subtitle
            String subtitle = json.optString("subtitle", "");
            if (!subtitle.isEmpty()) {
                printerService.setFontSize(22f, callbackAdapter);
                printerService.printText(subtitle + "\n", callbackAdapter);
            }

            printerService.setFontSize(22f, callbackAdapter);
            printerService.printText("--------------------------------\n", callbackAdapter);

            // Order details - left aligned
            printerService.setAlignment(0, callbackAdapter);
            String orderNumber = json.optString("orderNumber", "");
            if (!orderNumber.isEmpty()) printerService.printText("單號：" + orderNumber + "\n", callbackAdapter);
            String dateTime = json.optString("dateTime", "");
            if (!dateTime.isEmpty()) printerService.printText("時間：" + dateTime + "\n", callbackAdapter);
            String orderType = json.optString("orderType", "");
            if (!orderType.isEmpty()) printerService.printText("類型：" + orderType + "\n", callbackAdapter);
            String paymentMethod = json.optString("paymentMethod", "");
            if (!paymentMethod.isEmpty()) printerService.printText("付款：" + paymentMethod + "\n", callbackAdapter);

            printerService.printText("--------------------------------\n", callbackAdapter);

            // Items
            org.json.JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONObject item = items.getJSONObject(i);
                    String name = item.optString("name", "");
                    int qty = item.optInt("qty", 0);
                    int price = item.optInt("price", 0);
                    printerService.printText(name + " x" + qty + "  $" + price + "\n", callbackAdapter);
                    String options = item.optString("options", "");
                    if (!options.isEmpty()) printerService.printText("  " + options + "\n", callbackAdapter);
                }
            }

            printerService.printText("--------------------------------\n", callbackAdapter);

            // Total - centered, large
            printerService.setAlignment(1, callbackAdapter);
            printerService.setFontSize(32f, callbackAdapter);
            printerService.printText("合計：$" + json.optString("total", "0") + "\n", callbackAdapter);

            // Feed and cut
            printerService.lineWrap(4, callbackAdapter);

            Log.d(TAG, "POS receipt printed successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "printPosReceipt error", e);
            return false;
        }
    }

    public boolean printColumns(String[] texts, int[] widths, int[] aligns) {
        if (!isConnected()) return false;
        try {
            printerService.printColumnsText(texts, widths, aligns, callbackAdapter);
            printerService.lineWrap(1, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "printColumns error", e);
            return false;
        }
    }

    public int getPrinterStatus() {
        if (!isConnected()) return 505;
        try {
            return printerService.updatePrinterState();
        } catch (RemoteException e) {
            Log.e(TAG, "getPrinterStatus error", e);
            return 507;
        }
    }

    private String safe(String value) {
        if (value == null) return "\n";
        return value.endsWith("\n") ? value : value + "\n";
    }
        public boolean cutPaper() {
        if (!isConnected()) return false;
        try {
            printerService.lineWrap(3, callbackAdapter);
            byte[] cutCmd = {0x1D, 0x56, 0x01};
            printerService.sendRAWData(cutCmd, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "cutPaper error", e);
            return false;
        }
    }

    public boolean openCashDrawer() {
        if (!isConnected()) return false;
        try {
            byte[] drawerCmd = {0x10, 0x14, 0x01, 0x00, 0x05};
            printerService.sendRAWData(drawerCmd, callbackAdapter);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "openCashDrawer error", e);
            return false;
        }
    }

}
