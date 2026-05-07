package com.pos.sunmiprinter.printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.RemoteException;
import android.util.Log;

import com.pos.sunmiprinter.AppSettings;
import com.pos.sunmiprinter.LogManager;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import org.json.JSONArray;
import org.json.JSONObject;

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

    public void bind() {
        try {
            LogManager.i(TAG, "bind: requesting bindService");
            InnerPrinterManager.getInstance().bindService(context, callback);
            LogManager.i(TAG, "bind: bindService called (waiting onConnected callback)");
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

    public int getPrinterStatus() {
        if (!isConnected()) return -1;
        try {
            return printerService.updatePrinterState();
        } catch (RemoteException e) {
            LogManager.e(TAG, "getPrinterStatus failed", e);
            return -1;
        }
    }

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

    public boolean printText(String text) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printText(text, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printText failed", e);
            return false;
        }
    }

    public boolean printTextWithFont(String text, String typeface, float fontSize) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printTextWithFont(text, typeface, fontSize, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printTextWithFont failed", e);
            return false;
        }
    }

    public boolean printColumns(String[] colsText, int[] colsWidth, int[] colsAlign) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printColumnsString(colsText, colsWidth, colsAlign, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printColumns failed", e);
            return false;
        }
    }

    public boolean printBitmap(Bitmap bmp) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printBitmap(bmp, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printBitmap failed", e);
            return false;
        }
    }

    public boolean printBarcode(String data, int symbology, int height, int width, int textPos) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printBarCode(data, symbology, height, width, textPos, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printBarcode failed", e);
            return false;
        }
    }

    public boolean printQRCode(String data, int moduleSize, int errorLevel) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printQRCode(data, moduleSize, errorLevel, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printQRCode failed", e);
            return false;
        }
    }

    public boolean printReceipt(String title, String body) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            if (title != null && !title.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(title + "\n", null, 32, null);
                printerService.setAlignment(0, null);
            }
            printerService.printText("--------------------------------\n", null);
            if (body != null) printerService.printText(body + "\n", null);
            feedAndCut();
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printReceipt failed", e);
            return false;
        }
    }

    public boolean printPosReceipt(String json) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            LogManager.e(TAG, "printPosReceipt: printer not connected");
            return false;
        }
        try {
            String head = json == null ? "(null)" : (json.length() > 200 ? json.substring(0, 200) : json);
            LogManager.i(TAG, "printPosReceipt ENTRY len=" + (json == null ? -1 : json.length()) + " head=" + head);

            JSONObject obj = new JSONObject(json);
            String shopName = obj.has("shopName") ? obj.optString("shopName", "") : obj.optString("storeName", "");
            String subtitle = obj.optString("subtitle", "");
            String orderNo  = obj.has("orderNo") ? obj.optString("orderNo", "") : obj.optString("orderNumber", "");
            String datetime = obj.has("datetime") ? obj.optString("datetime", "") : obj.optString("dateTime", "");
            String orderType = obj.optString("orderType", "");
            String payment   = obj.optString("paymentMethod", "");
            String footer = obj.optString("footer", "");
            boolean openDrawer = obj.optBoolean("openDrawer", false);

            // 解析後逐欄位 log
            LogManager.i(TAG, "printPosReceipt parsed shopName=" + shopName);
            StringBuilder shopCps = new StringBuilder();
            int sLimit = Math.min(shopName.length(), 20);
            for (int i = 0; i < sLimit; i++) shopCps.append(Integer.toHexString(shopName.charAt(i))).append(' ');
            LogManager.i(TAG, "printPosReceipt shopName codepoints(first20)=" + shopCps);
            LogManager.i(TAG, "printPosReceipt orderNo=" + orderNo + " datetime=" + datetime
                    + " orderType=" + orderType + " payment=" + payment
                    + " footer=" + footer + " openDrawer=" + openDrawer);

            JSONArray items = obj.optJSONArray("items");
            int itemCount = items == null ? 0 : items.length();
            LogManager.i(TAG, "printPosReceipt items.length=" + itemCount);
            if (itemCount > 0) {
                JSONObject it0 = items.getJSONObject(0);
                String n0 = it0.optString("name", "");
                int q0 = it0.optInt("qty", 0);
                double p0 = it0.optDouble("price", 0);
                String o0 = it0.optString("options", "");
                LogManager.i(TAG, "printPosReceipt items[0] name=" + n0 + " qty=" + q0 + " price=" + p0 + " options=" + o0);
                StringBuilder nameCps = new StringBuilder();
                int nLimit = Math.min(n0.length(), 20);
                for (int i = 0; i < nLimit; i++) nameCps.append(Integer.toHexString(n0.charAt(i))).append(' ');
                LogManager.i(TAG, "printPosReceipt items[0].name codepoints(first20)=" + nameCps);
            }

            if (!shopName.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(shopName + "\n", null, 32, null);
            }
            if (!subtitle.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(subtitle + "\n", null, 24, null);
            }
            printerService.setAlignment(0, null);
            if (!orderNo.isEmpty())   printerService.printText("單號: " + orderNo + "\n", null);
            if (!datetime.isEmpty())  printerService.printText("時間: " + datetime + "\n", null);
            if (!orderType.isEmpty()) printerService.printText("類型: " + orderType + "\n", null);
            if (!payment.isEmpty())   printerService.printText("付款: " + payment + "\n", null);
            printerService.printText("--------------------------------\n", null);

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
                    String options = it.optString("options", "");
                    if (!options.isEmpty()) {
                        printerService.printText("  " + options + "\n", null);
                    }
                }
            }
            printerService.printText("--------------------------------\n", null);

            if (obj.has("total")) {
                String total = obj.optString("total", "0");
                printerService.setAlignment(2, null);
                printerService.printTextWithFont("合計: $" + total + "\n", null, 28, null);
                printerService.setAlignment(0, null);
            }

            if (!footer.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printText(footer + "\n", null);
                printerService.setAlignment(0, null);
            }

            feedAndCut();
            if (openDrawer) openCashDrawer();

            settings.recordPrintResult(true, "");
            LogManager.i(TAG, "printPosReceipt ok, orderNo=" + orderNo);
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printPosReceipt failed", e);
            return false;
        }
    }

    public boolean printReceiptJson(String json) {
        return printPosReceipt(json);
    }

    public boolean printHtml(String title, String html) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            if (title != null && !title.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(title + "\n", null, 28, null);
                printerService.setAlignment(0, null);
            }
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

    public boolean cutPaper() {
        if (!isConnected()) return false;
        try {
            printerService.lineWrap(1, null);
            printerService.cutPaper(null);
            return true;
        } catch (Throwable t) {
            LogManager.w(TAG, "cutPaper API failed, fallback RAW", t);
            try {
                printerService.lineWrap(1, null);
                byte[] raw = new byte[]{0x1D, 0x56, 0x42, 0x00};
                printerService.sendRAWData(raw, null);
                return true;
            } catch (Exception ex) {
                LogManager.e(TAG, "cutPaper RAW failed", ex);
                return false;
            }
        }
    }

    public boolean openCashDrawer() {
        LogManager.i(TAG, "openCashDrawer ENTRY isConnected=" + isConnected()
                + " printerService==null? " + (printerService == null));
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
                LogManager.i(TAG, "openCashDrawer via RAW ok");
                return true;
            } catch (Exception ex) {
                LogManager.e(TAG, "openCashDrawer RAW failed", ex);
                return false;
            }
        }
    }

    public boolean buzzer() {
        return buzzer(2, 100);
    }

    public boolean buzzer(int times, int interval) {
        if (!isConnected()) return false;
        try {
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
