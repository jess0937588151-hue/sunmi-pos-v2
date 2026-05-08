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

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b == null ? "" : b;
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

            float fStore    = settings.getFontStore();
            float fSubtitle = settings.getFontSubtitle();
            float fInfo     = settings.getFontInfo();
            float fItem     = settings.getFontItem();
            float fTotal    = settings.getFontTotal();
            float fFooter   = settings.getFontFooter();
            LogManager.i(TAG, "printPosReceipt fonts store=" + fStore + " subtitle=" + fSubtitle
                    + " info=" + fInfo + " item=" + fItem + " total=" + fTotal + " footer=" + fFooter);

            JSONObject obj = new JSONObject(json);
            String mode = obj.optString("mode", "receipt");

            // ---  web/APK  key fallback ---
            String shopName     = firstNonEmpty(obj.optString("shopName", ""),     obj.optString("storeName", ""));
            String storePhone   = firstNonEmpty(obj.optString("shopPhone", ""),    obj.optString("storePhone", ""));
            String storeAddress = firstNonEmpty(obj.optString("shopAddress", ""),  obj.optString("storeAddress", ""));
            String subtitle     = obj.optString("subtitle", "");
            String orderNo      = firstNonEmpty(obj.optString("orderNo", ""),      obj.optString("orderNumber", ""));
            String datetime     = firstNonEmpty(obj.optString("dateTime", ""),     obj.optString("datetime", ""));
            String orderType    = obj.optString("orderType", "");
            String payment      = firstNonEmpty(obj.optString("paymentMethod", ""), obj.optString("payment", ""));

            //  customerInfo customerName + customerPhoneMasked  customerInfo
            String customerName  = obj.optString("customerName", "");
            String customerPhone = obj.optString("customerPhoneMasked", "");
            String customerInfo  = obj.optString("customerInfo", "");
            if (customerInfo.isEmpty()) {
                StringBuilder ci = new StringBuilder();
                if (!customerName.isEmpty())  ci.append(customerName);
                if (!customerPhone.isEmpty()) {
                    if (ci.length() > 0) ci.append("  ");
                    ci.append(customerPhone);
                }
                customerInfo = ci.toString();
            }

            String customerNote = obj.optString("customerNote", "");
            String orderNote    = obj.optString("orderNote", "");
            String footer       = obj.optString("footer", "");
            boolean openDrawer  = obj.optBoolean("openDrawer", false);

            //  / 
            String subtotalStr = obj.has("subtotal") ? obj.optString("subtotal", "")
                              : (obj.has("subtotalAmount") ? obj.optString("subtotalAmount", "") : "");
            String discountStr = obj.has("discountAmount") ? obj.optString("discountAmount", "")
                              : (obj.has("discount") ? obj.optString("discount", "") : "");
            String totalStr    = obj.optString("total", "");

            // ---  RAW  ---
            LogManager.i(TAG, "printPosReceipt RAW mode=" + mode
                    + " shopName=[" + shopName + "]"
                    + " shopPhone=[" + storePhone + "]"
                    + " shopAddress=[" + storeAddress + "]"
                    + " subtitle=[" + subtitle + "]"
                    + " orderNo=[" + orderNo + "]"
                    + " dateTime=[" + datetime + "]"
                    + " orderType=[" + orderType + "]"
                    + " payment=[" + payment + "]"
                    + " customerName=[" + customerName + "]"
                    + " customerPhone=[" + customerPhone + "]"
                    + " customerInfoMerged=[" + customerInfo + "]"
                    + " customerNote=[" + customerNote + "]"
                    + " orderNote=[" + orderNote + "]"
                    + " subtotal=[" + subtotalStr + "]"
                    + " discount=[" + discountStr + "]"
                    + " total=[" + totalStr + "]"
                    + " footer=[" + footer + "]"
                    + " openDrawer=" + openDrawer);

            // ---  fields obj.optBoolean ---
            JSONObject fields = obj.optJSONObject("fields");
            boolean fStoreName    = fields == null || fields.optBoolean("storeName",      true);
            boolean fStorePhone   = fields == null || fields.optBoolean("storePhone",     true);
            boolean fStoreAddress = fields == null || fields.optBoolean("storeAddress",   true);
            boolean fSubtitleOn   = fields == null || fields.optBoolean("subtitle",       true);
            boolean fOrderNo      = fields == null || fields.optBoolean("orderNo",        true);
            boolean fDatetime     = fields == null || fields.optBoolean("dateTime",       true);
            boolean fOrderType    = fields == null || fields.optBoolean("orderType",      true);
            boolean fPayment      = fields == null || fields.optBoolean("paymentMethod",  true);
            boolean fCustomerInfo = fields == null || fields.optBoolean("customerInfo",   true);
            boolean fCustomerNote = fields == null || fields.optBoolean("customerNote",   true);
            boolean fItemsOn      = fields == null || fields.optBoolean("items",          true);
            boolean fItemQty      = fields == null || fields.optBoolean("itemQty",        true);
            boolean fItemPrice    = fields == null || fields.optBoolean("itemPrice",      true);
            boolean fItemSel      = fields == null || fields.optBoolean("itemSelections", true);
            boolean fItemNote     = fields == null || fields.optBoolean("itemNote",       true);
            boolean fOrderNote    = fields == null || fields.optBoolean("orderNote",      true);
            boolean fSubtotal     = fields == null || fields.optBoolean("subtotal",       true);
            boolean fDiscount     = fields == null || fields.optBoolean("discount",       true);
            boolean fTotalLine    = fields == null || fields.optBoolean("total",          true);
            boolean fFooterOn     = fields == null || fields.optBoolean("footer",         true);

            LogManager.i(TAG, "printPosReceipt FIELDS mode=" + mode
                    + " storeName=" + fStoreName + " storePhone=" + fStorePhone
                    + " storeAddress=" + fStoreAddress + " subtitle=" + fSubtitleOn
                    + " orderNo=" + fOrderNo + " dateTime=" + fDatetime
                    + " orderType=" + fOrderType + " paymentMethod=" + fPayment
                    + " customerInfo=" + fCustomerInfo + " customerNote=" + fCustomerNote
                    + " items=" + fItemsOn + " itemQty=" + fItemQty + " itemPrice=" + fItemPrice
                    + " itemSelections=" + fItemSel + " itemNote=" + fItemNote
                    + " orderNote=" + fOrderNote + " subtotal=" + fSubtotal
                    + " discount=" + fDiscount + " total=" + fTotalLine + " footer=" + fFooterOn);

            JSONArray items = obj.optJSONArray("items");
            int itemCount = items == null ? 0 : items.length();
            LogManager.i(TAG, "printPosReceipt ITEMS count=" + itemCount);
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;
                    String iName = it.optString("name", "");
                    int iQty = it.optInt("qty", 1);
                    double iPrice = it.optDouble("price", 0);
                    //  selections options
                    String iSel = it.has("selections") ? it.optString("selections", "")
                                : it.optString("options", "");
                    String iNote = it.optString("note", "");
                    LogManager.i(TAG, "printPosReceipt ITEM[" + i + "] name=[" + iName
                            + "] qty=" + iQty + " price=" + iPrice
                            + " selections=[" + iSel + "] note=[" + iNote + "]");
                }
            }

            // 
            if (fStoreName && !shopName.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(shopName + "\n", null, fStore, null);
            }
            if (fStorePhone && !storePhone.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(storePhone + "\n", null, fSubtitle, null);
            }
            if (fStoreAddress && !storeAddress.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(storeAddress + "\n", null, fSubtitle, null);
            }
            if (fSubtitleOn && !subtitle.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(subtitle + "\n", null, fSubtitle, null);
            }
            printerService.setAlignment(0, null);

            // 
            boolean printedAnyHeader = false;
            if (fOrderNo   && !orderNo.isEmpty())   { printerService.printTextWithFont(": " + orderNo + "\n",  null, fInfo, null); printedAnyHeader = true; }
            if (fDatetime  && !datetime.isEmpty())  { printerService.printTextWithFont(": " + datetime + "\n", null, fInfo, null); printedAnyHeader = true; }
            if (fOrderType && !orderType.isEmpty()) { printerService.printTextWithFont(": " + orderType + "\n", null, fInfo, null); printedAnyHeader = true; }
            if (fPayment   && !payment.isEmpty())   { printerService.printTextWithFont(": " + payment + "\n",  null, fInfo, null); printedAnyHeader = true; }
            if (fCustomerInfo && !customerInfo.isEmpty()) { printerService.printTextWithFont(": " + customerInfo + "\n", null, fInfo, null); printedAnyHeader = true; }
            if (fCustomerNote && !customerNote.isEmpty()) { printerService.printTextWithFont(": " + customerNote + "\n", null, fInfo, null); printedAnyHeader = true; }
            if (printedAnyHeader) {
                printerService.printTextWithFont("--------------------------------\n", null, fInfo, null);
            }

            // 
            if (fItemsOn && items != null && items.length() > 0) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String name = it.optString("name", "");
                    int qty = it.optInt("qty", 1);
                    double price = it.optDouble("price", 0);
                    String selections = it.has("selections") ? it.optString("selections", "")
                                      : it.optString("options", "");
                    String note = it.optString("note", "");

                    StringBuilder mainLine = new StringBuilder();
                    if (!name.isEmpty()) mainLine.append(name);
                    if (fItemQty)        mainLine.append("  x").append(qty);
                    if (fItemPrice) {
                        if (mainLine.length() > 0) mainLine.append("   ");
                        mainLine.append("$").append(String.format("%.0f", price));
                    }
                    if (mainLine.length() > 0) {
                        printerService.printTextWithFont(mainLine.toString() + "\n", null, fItem, null);
                    }
                    if (fItemSel && !selections.isEmpty()) {
                        printerService.printTextWithFont("  " + selections + "\n", null, fItem, null);
                    }
                    if (fItemNote && !note.isEmpty()) {
                        printerService.printTextWithFont("  : " + note + "\n", null, fItem, null);
                    }
                }
                printerService.printTextWithFont("--------------------------------\n", null, fInfo, null);
            }

            // 
            if (fOrderNote && !orderNote.isEmpty()) {
                printerService.printTextWithFont(": " + orderNote + "\n", null, fInfo, null);
            }

            //  / / 
            if (fSubtotal && !subtotalStr.isEmpty()) {
                printerService.setAlignment(2, null);
                printerService.printTextWithFont(": $" + subtotalStr + "\n", null, fInfo, null);
                printerService.setAlignment(0, null);
            }
            if (fDiscount && !discountStr.isEmpty() && !"0".equals(discountStr)) {
                printerService.setAlignment(2, null);
                printerService.printTextWithFont(": -$" + discountStr + "\n", null, fInfo, null);
                printerService.setAlignment(0, null);
            }
            if (fTotalLine && !totalStr.isEmpty()) {
                printerService.setAlignment(2, null);
                printerService.printTextWithFont(": $" + totalStr + "\n", null, fTotal, null);
                printerService.setAlignment(0, null);
            }

            // 
            if (fFooterOn && !footer.isEmpty()) {
                printerService.setAlignment(1, null);
                printerService.printTextWithFont(footer + "\n", null, fFooter, null);
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
            printerService.printTextWithFont("**  **\n", null, 32, null);
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
