package com.pos.sunmiprinter.printer;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.pos.sunmiprinter.AppSettings;
import com.pos.sunmiprinter.LogManager;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.InnerResultCallback;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sunmi 內建印表機管理
 * v20260603:
 *   - 新增 PrinterStatusInfo 詳細狀態（連線/缺紙/開蓋/過熱）
 *   - 每次列印結果寫入 AppSettings.recordPrintResult，供 /ping 與健康檢查 UI 使用
 *   - 所有 catch 改用 LogManager.e 紀錄
 */
public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinterManager";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY = 300;

    private SunmiPrinterService printerService;
    private final Context context;
    private final AppSettings settings;
    private boolean bound = false;

    private final InnerPrinterCallback callback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            printerService = service;
            bound = true;
            LogManager.i(TAG, "SunmiPrinterService connected");
        }
        @Override
        protected void onDisconnected() {
            printerService = null;
            bound = false;
            LogManager.w(TAG, "SunmiPrinterService disconnected");
        }
    };

    public SunmiPrinterManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.settings = new AppSettings(this.context);
    }

    // ===== Connection =====

    public void bind() {
        try {
            InnerPrinterManager.getInstance().bindService(context, callback);
        } catch (InnerPrinterException e) {
            LogManager.e(TAG, "bindService failed", e);
        }
    }

    public void unbind() {
        try {
            if (bound) {
                InnerPrinterManager.getInstance().unBindService(context, callback);
                bound = false;
            }
        } catch (InnerPrinterException e) {
            LogManager.e(TAG, "unBindService failed", e);
        }
    }

    public boolean isConnected() {
        return bound && printerService != null;
    }

    /**
     * 取得印表機原始狀態 int（向後相容）
     * 0 / -1 等定義依 Sunmi SDK
     */
    public int getPrinterStatus() {
        if (!isConnected()) return -1;
        try {
            return printerService.updatePrinterState();
        } catch (RemoteException e) {
            LogManager.e(TAG, "getPrinterStatus failed", e);
            return -1;
        }
    }

    /**
     * v20260603 詳細狀態
     * Sunmi updatePrinterState() 回傳值參考:
     *   1: 正常工作
     *   2: 印表機準備中
     *   3: 通訊異常
     *   4: 缺紙
     *   5: 過熱
     *   6: 開蓋
     *   7: 切刀異常
     *   8: 切刀復位
     *   9: 黑標未找到
     *   505: 沒有檢測到印表機
     */
    public PrinterStatusInfo getPrinterStatusInfo() {
        PrinterStatusInfo info = new PrinterStatusInfo();
        info.connected = isConnected();
        info.raw = getPrinterStatus();
        switch (info.raw) {
            case 4: info.paperOut = true; break;
            case 5: info.overheat = true; break;
            case 6: info.coverOpen = true; break;
            case 7: case 8: info.cutterError = true; break;
            default: break;
        }
        return info;
    }

    public static class PrinterStatusInfo {
        public boolean connected;
        public boolean paperOut;
        public boolean coverOpen;
        public boolean overheat;
        public boolean cutterError;
        public int raw = -1;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"connected\":").append(connected).append(",");
            sb.append("\"paperOut\":").append(paperOut).append(",");
            sb.append("\"coverOpen\":").append(coverOpen).append(",");
            sb.append("\"overheat\":").append(overheat).append(",");
            sb.append("\"cutterError\":").append(cutterError).append(",");
            sb.append("\"raw\":").append(raw);
            sb.append("}");
            return sb.toString();
        }
    }

    // ===== Basic Print =====

    public void printText(String text) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printText(text, null);
    }

    public void printTextWithFont(String text, String typeface, float fontSize) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printTextWithFont(text, typeface, fontSize, null);
    }

    public void printColumns(String[] colsText, int[] colsWidth, int[] colsAlign) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printColumnsString(colsText, colsWidth, colsAlign, null);
    }

    public void printBitmap(android.graphics.Bitmap bmp) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printBitmap(bmp, null);
    }

    public void printBarcode(String data, int symbology, int height, int width, int textPos) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printBarCode(data, symbology, height, width, textPos, null);
    }

    public void printQRCode(String data, int moduleSize, int errorLevel) throws RemoteException {
        if (!isConnected()) throw new RemoteException("printer not connected");
        printerService.printQRCode(data, moduleSize, errorLevel, null);
    }

    // ===== High-level: 收據 / POS 收據 =====

    /**
     * 列印 JSON 收據，回傳 true=成功
     * v20260603: 紀錄 lastPrintAt / lastPrintOk / lastPrintError
     */
    public boolean printReceipt(String json) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            LogManager.e(TAG, "printReceipt: printer not connected");
            return false;
        }
        try {
            JSONObject obj = new JSONObject(json);
            String text = obj.optString("text", "");
            if (!text.isEmpty()) {
                printerService.printText(text + "\n", null);
            }
            feedAndCut();
            settings.recordPrintResult(true, "");
            LogManager.i(TAG, "printReceipt ok, len=" + (text == null ? 0 : text.length()));
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printReceipt failed", e);
            return false;
        }
    }

    /**
     * 列印完整 POS 收據（解析 JSON 結構）
     * 結構: { shopName, subtitle, orderNo, datetime, items:[{name,qty,price}], total, openDrawer, footer }
     */
    public boolean printPosReceipt(String json) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            LogManager.e(TAG, "printPosReceipt: printer not connected");
            return false;
        }
        try {
            JSONObject obj = new JSONObject(json);
            String shopName = obj.optString("shopName", "");
            String subtitle = obj.optString("subtitle", "");
            String orderNo = obj.optString("orderNo", "");
            String datetime = obj.optString("datetime", "");
            String footer = obj.optString("footer", "");
            boolean openDrawer = obj.optBoolean("openDrawer", false);

            // Header
            if (!shopName.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(shopName + "\n", null, 32, null);
            }
            if (!subtitle.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(subtitle + "\n", null, 24, null);
            }
            printerService.setAlignment(0, null);
            if (!orderNo.isEmpty()) printerService.printText("Order: " + orderNo + "\n", null);
            if (!datetime.isEmpty()) printerService.printText("Time : " + datetime + "\n", null);
            printerService.printText("--------------------------------\n", null);

            // Items
            JSONArray items = obj.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String name = it.optString("name", "");
                    int qty = it.optInt("qty", 1);
                    double price = it.optDouble("price", 0);
                    String[] cols = new String[]{name, String.valueOf(qty), String.format("%.0f", price)};
                    int[] widths = new int[]{18, 4, 6};
                    int[] aligns = new int[]{0, 2, 2};
                    printerService.printColumnsString(cols, widths, aligns, null);
                }
            }
            printerService.printText("--------------------------------\n", null);

            // Total
            if (obj.has("total")) {
                double total = obj.optDouble("total", 0);
                printerService.setAlignment(2, null);
                printerService.printTextWithFont("TOTAL: " + String.format("%.0f", total) + "\n", null, 28, null);
                printerService.setAlignment(0, null);
            }

            // Footer
            if (!footer.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printText(footer + "\n", null);
                printerService.setAlignment(0, null);
            }

            feedAndCut();

            // 開錢箱（僅當 openDrawer=true）
            if (openDrawer) {
                openCashDrawer();
            }

            settings.recordPrintResult(true, "");
            LogManager.i(TAG, "printPosReceipt ok, orderNo=" + orderNo);
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printPosReceipt failed", e);
            return false;
        }
    }

    /** alias */
    public boolean printReceiptJson(String json) {
        return printPosReceipt(json);
    }

    /**
     * 簡易 HTML → 純文字後列印
     */
    public boolean printHtml(String html) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            String plain = html == null ? "" : html.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
            printerService.printText(plain + "\n", null);
            feedAndCut();
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printHtml failed", e);
            return false;
        }
    }

    public boolean printTestReceipt() {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.setAlignment(1, null);
            printerService.printTextWithFont("** 測試列印 **\n", null, 32, null);
            printerService.setAlignment(0, null);
            printerService.printText("Time: " + new java.util.Date().toString() + "\n", null);
            printerService.printText("APK : Sunmi POS Bridge\n", null);
            printerService.printText("Status raw: " + getPrinterStatus() + "\n", null);
            feedAndCut();
            settings.recordPrintResult(true, "");
            LogManager.i(TAG, "printTestReceipt ok");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printTestReceipt failed", e);
            return false;
        }
    }

    // ===== Hardware control =====

    public void cutPaper() {
        if (!isConnected()) return;
        try {
            printerService.lineWrap(1, null);
            printerService.cutPaper(null);
        } catch (Throwable t) {
            LogManager.w(TAG, "cutPaper API failed, fallback RAW", t);
            try {
                printerService.lineWrap(1, null);
                byte[] raw = new byte[]{0x1D, 0x56, 0x42, 0x00};
                printerService.sendRAWData(raw, null);
            } catch (Exception ex) {
                LogManager.e(TAG, "cutPaper RAW failed", ex);
            }
        }
    }

    public boolean openCashDrawer() {
        if (!isConnected()) {
            LogManager.w(TAG, "openCashDrawer: printer not connected");
            return false;
        }
        try {
            printerService.openDrawer(null);
            LogManager.i(TAG, "openCashDrawer via API ok");
            return true;
        } catch (Throwable t) {
            LogManager.w(TAG, "openCashDrawer API failed, fallback RAW", t);
            try {
                byte[] raw = new byte[]{0x1B, 0x70, 0x00, 0x19, (byte) 0xFA};
                printerService.sendRAWData(raw, null);
                return true;
            } catch (Exception ex) {
                LogManager.e(TAG, "openCashDrawer RAW failed", ex);
                return false;
            }
        }
    }

    public boolean buzzer(int times, int interval) {
        if (!isConnected()) return false;
        try {
            // ESC B n t  -> 0x1B 0x42 n t
            byte[] raw = new byte[]{0x1B, 0x42, (byte) times, (byte) interval};
            printerService.sendRAWData(raw, null);
            return true;
        } catch (Exception e) {
            LogManager.e(TAG, "buzzer failed", e);
            return false;
        }
    }

    public boolean sendRawData(byte[] data) {
        if (!isConnected()) return false;
        try {
            printerService.sendRAWData(data, null);
            return true;
        } catch (Exception e) {
            LogManager.e(TAG, "sendRawData failed", e);
            return false;
        }
    }

    private void feedAndCut() {
        if (!isConnected()) return;
        try {
            printerService.lineWrap(1, null);
            printerService.cutPaper(null);
        } catch (Throwable t) {
            LogManager.w(TAG, "feedAndCut failed, fallback RAW", t);
            try {
                printerService.lineWrap(1, null);
                byte[] raw = new byte[]{0x1D, 0x56, 0x42, 0x00};
                printerService.sendRAWData(raw, null);
            } catch (Exception ex) {
                LogManager.e(TAG, "feedAndCut RAW failed", ex);
            }
        }
    }

    // ===== Retry =====

    public interface PrintTask {
        boolean run() throws Exception;
    }

    public boolean retry(PrintTask task) {
        Exception last = null;
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                if (task.run()) return true;
            } catch (Exception e) {
                last = e;
                LogManager.w(TAG, "PrintTask attempt " + (i + 1) + " failed", e);
            }
            try { Thread.sleep(RETRY_DELAY); } catch (InterruptedException ignored) {}
        }
        if (last != null) {
            LogManager.e(TAG, "PrintTask exhausted retries", last);
        } else {
            LogManager.e(TAG, "PrintTask exhausted retries (no exception)");
        }
        return false;
    }
}
