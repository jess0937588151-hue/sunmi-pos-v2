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
    // v20260531: 列印前若未連線，自動 rebind 並等待這麼久（毫秒）
    private static final long RECONNECT_WAIT_MS = 1500;
    private static final long RECONNECT_POLL_MS = 100;

    private SunmiPrinterService printerService;
    private final Context context;
    private final AppSettings settings;
    private volatile boolean bound = false;

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

    /**
     * v20260531: 列印前呼叫。若未連線，主動 rebind 並輪詢等待最多 RECONNECT_WAIT_MS。
     * Android 7.1 在背景被回收 binder 後 isConnected() 可能短暫為 false，
     * 或 callback 尚未回來，這裡給它一點時間重新接上，避免「明明開著卻印不出」。
     */
    private boolean ensureConnected() {
        if (isConnected()) {
            // 進一步確認 service 真的活著（呼叫狀態查詢，死掉會丟 RemoteException）
            try {
                printerService.updatePrinterState();
                return true;
            } catch (Throwable t) {
                LogManager.w(TAG, "ensureConnected: service stale, will rebind", t);
                bound = false;
                printerService = null;
            }
        }
        LogManager.i(TAG, "ensureConnected: not connected, rebind...");
        bind();
        long waited = 0;
        while (waited < RECONNECT_WAIT_MS) {
            if (isConnected()) {
                LogManager.i(TAG, "ensureConnected: reconnected after " + waited + "ms");
                return true;
            }
            try { Thread.sleep(RECONNECT_POLL_MS); } catch (InterruptedException ignored) {}
            waited += RECONNECT_POLL_MS;
        }
        LogManager.w(TAG, "ensureConnected: still not connected after " + RECONNECT_WAIT_MS + "ms");
        return isConnected();
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
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

    /**
     * v20260531: 列印單據主入口。整段包進 retry()，列印前 ensureConnected()。
     * 排版改用「數量在前 + 欄位對齊」：
     *   - 品項主行 = [數量欄 x1][品名欄]，數量靠左固定寬、品名自動折行
     *   - 選項每行一個、明顯縮排
     *   - 廚房單(mode=kitchen)品名套 fontKitchenItem、選項/資訊套 fontKitchenInfo
     */
    public boolean printPosReceipt(final String json) {
        return retry(() -> doPrintPosReceipt(json));
    }

    private boolean doPrintPosReceipt(String json) {
        if (!ensureConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            LogManager.e(TAG, "printPosReceipt: printer not connected");
            return false;
        }
        try {
            String head = json == null ? "(null)" : (json.length() > 200 ? json.substring(0, 200) : json);
            LogManager.i(TAG, "printPosReceipt ENTRY len=" + (json == null ? -1 : json.length()) + " head=" + head);

            JSONObject obj = new JSONObject(json);
            String mode = obj.optString("mode", "receipt");
            boolean isKitchen = "kitchen".equals(mode);

            // 顧客單字級
            float fStore    = settings.getFontStore();
            float fSubtitle = settings.getFontSubtitle();
            float fInfo     = settings.getFontInfo();
            float fItem     = settings.getFontItem();
            float fTotal    = settings.getFontTotal();
            float fFooter   = settings.getFontFooter();
            // 廚房單字級（可在設定頁調整）
            float fKItem = settings.getFontKitchenItem();
            float fKInfo = settings.getFontKitchenInfo();
                        // 廚房單子選項字級（v20260620 可在設定頁獨立調整）
            float fKOption = settings.getFontKitchenOption();
            // 本次實際採用：廚房單品名用 fKItem、資訊用 fKInfo、子選項用 fKOption
            float useItemFont = isKitchen ? fKItem : fItem;
            float useInfoFont = isKitchen ? fKInfo : fInfo;
            float useOptionFont = isKitchen ? fKOption : fItem;
            LogManager.i(TAG, "printPosReceipt mode=" + mode + " isKitchen=" + isKitchen
                    + " useItemFont=" + useItemFont + " useInfoFont=" + useInfoFont);

            // --- web/APK key fallback ---
            String shopName     = firstNonEmpty(obj.optString("shopName", ""),     obj.optString("storeName", ""));
            String storePhone   = firstNonEmpty(obj.optString("shopPhone", ""),    obj.optString("storePhone", ""));
            String storeAddress = firstNonEmpty(obj.optString("shopAddress", ""),  obj.optString("storeAddress", ""));
            String subtitle     = obj.optString("subtitle", "");
            String orderNo      = firstNonEmpty(obj.optString("orderNo", ""),      obj.optString("orderNumber", ""));
            String datetime     = firstNonEmpty(obj.optString("dateTime", ""),     obj.optString("datetime", ""));
            String orderType    = obj.optString("orderType", "");
            String payment      = firstNonEmpty(obj.optString("paymentMethod", ""), obj.optString("payment", ""));

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

            String subtotalStr = obj.has("subtotal") ? obj.optString("subtotal", "")
                              : (obj.has("subtotalAmount") ? obj.optString("subtotalAmount", "") : "");
            String discountStr = obj.has("discountAmount") ? obj.optString("discountAmount", "")
                              : (obj.has("discount") ? obj.optString("discount", "") : "");
            String totalStr    = obj.optString("total", "");

            // --- fields ---
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

            JSONArray items = obj.optJSONArray("items");

            // ===== 表頭（店名/副標）=====
            if (!isKitchen) {
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
            } else {
                // 廚房單：置中大標題
                printerService.setAlignment(1, null);
                printerService.printTextWithFont("** 廚房單 **\n", null, fKInfo, null);
            }
            printerService.setAlignment(0, null);

            // ===== 訂單資訊 =====
            boolean printedAnyHeader = false;
            if (fOrderNo   && !orderNo.isEmpty())   { printerService.printTextWithFont("單號 " + orderNo + "\n",  null, useInfoFont, null); printedAnyHeader = true; }
            if (fDatetime  && !datetime.isEmpty())  { printerService.printTextWithFont("時間 " + datetime + "\n", null, useInfoFont, null); printedAnyHeader = true; }
            if (fOrderType && !orderType.isEmpty()) { printerService.printTextWithFont("類型 " + orderType + "\n", null, useInfoFont, null); printedAnyHeader = true; }
            if (!isKitchen) {
                if (fPayment   && !payment.isEmpty())   { printerService.printTextWithFont("付款 " + payment + "\n",  null, useInfoFont, null); printedAnyHeader = true; }
                if (fCustomerInfo && !customerInfo.isEmpty()) { printerService.printTextWithFont("顧客 " + customerInfo + "\n", null, useInfoFont, null); printedAnyHeader = true; }
            }
            if (fCustomerNote && !customerNote.isEmpty()) { printerService.printTextWithFont("備註 " + customerNote + "\n", null, useInfoFont, null); printedAnyHeader = true; }
            if (printedAnyHeader) {
                printerService.printTextWithFont("--------------------------------\n", null, useInfoFont, null);
            }

            // ===== 品項（數量在前、欄位對齊）=====
            if (fItemsOn && items != null && items.length() > 0) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    String name = it.optString("name", "");
                    int qty = it.optInt("qty", 1);
                    double price = it.optDouble("price", 0);
                    String selections = it.has("selections") ? it.optString("selections", "")
                                      : it.optString("options", "");
                    String note = it.optString("note", "");

                    // 主行：數量在前。用 printColumnsString 對齊：
                    //   廚房單 = [數量][品名]（不印價格）
                    //   顧客單 = [數量][品名][價格]
                                       String qtyCol = fItemQty ? ("x" + qty) : "";
                    if (isKitchen) {
                        // v20260620 修正廚房單品名無法放大：原本用 printColumnsString 印品名，
                        // 但該方法不吃字級（setFontSize 對欄位列印在 T2 無效），導致 30/50 印出來一樣大。
                        // 改用 printTextWithFont 印「數量 + 品名」整行，字級才會真正生效。
                        printerService.setAlignment(0, null);
                        String kitchenItemLine = (qtyCol.isEmpty() ? "" : (qtyCol + " ")) + name + "\n";
                        printerService.printTextWithFont(kitchenItemLine, null, useItemFont, null);
                    } else {

                        // 顧客單三欄：數量 4、品名 18、價格 10（靠右）
                        String priceCol = fItemPrice ? ("$" + String.format("%.0f", price)) : "";
                        String[] cols = { qtyCol, name, priceCol };
                        int[] widths  = { 4, 18, 10 };
                        int[] aligns  = { 0, 0, 2 };
                        printerService.setAlignment(0, null);
                        try { printerService.setFontSize(useItemFont, null); } catch (Throwable ignore) {}
                        printerService.printColumnsString(cols, widths, aligns, null);
                        try { printerService.setFontSize(useInfoFont, null); } catch (Throwable ignore) {}
                    }

                    // 選項：每行一個、明顯縮排（用較小字級，廚房單用 fKInfo）
                    if (fItemSel && !selections.isEmpty()) {
                        // selections 可能是用 / 或 , 或 ; 分隔的多個，逐一拆開各印一行
                        String[] parts = selections.split("\\s*[/,;、]\\s*");
                        for (String p : parts) {
                            String t = p == null ? "" : p.trim();
                            if (t.isEmpty()) continue;
                            printerService.printTextWithFont("    - " + t + "\n", null, useInfoFont, null);
                        }
                    }
                    if (fItemNote && !note.isEmpty()) {
                        printerService.printTextWithFont("    備註: " + note + "\n", null, useInfoFont, null);
                    }
                }
                printerService.printTextWithFont("--------------------------------\n", null, useInfoFont, null);
            }

            // ===== 整單備註 =====
            if (fOrderNote && !orderNote.isEmpty()) {
                printerService.printTextWithFont("整單備註 " + orderNote + "\n", null, useInfoFont, null);
            }

            // ===== 金額（顧客單才印）=====
            if (!isKitchen) {
                if (fSubtotal && !subtotalStr.isEmpty()) {
                    printerService.setAlignment(2, null);
                    printerService.printTextWithFont("小計 $" + subtotalStr + "\n", null, useInfoFont, null);
                    printerService.setAlignment(0, null);
                }
                if (fDiscount && !discountStr.isEmpty() && !"0".equals(discountStr)) {
                    printerService.setAlignment(2, null);
                    printerService.printTextWithFont("折扣 -$" + discountStr + "\n", null, useInfoFont, null);
                    printerService.setAlignment(0, null);
                }
                if (fTotalLine && !totalStr.isEmpty()) {
                    printerService.setAlignment(2, null);
                    printerService.printTextWithFont("總計 $" + totalStr + "\n", null, fTotal, null);
                    printerService.setAlignment(0, null);
                }
                // ===== 頁尾 =====
                if (fFooterOn && !footer.isEmpty()) {
                    printerService.setAlignment(1, null);
                    printerService.printTextWithFont(footer + "\n", null, fFooter, null);
                    printerService.setAlignment(0, null);
                }
            }

            feedAndCut();
            if (openDrawer && !isKitchen) openCashDrawer();
            if (isKitchen) buzzer(); // 廚房單響鈴提醒

            settings.recordPrintResult(true, "");
            LogManager.i(TAG, "printPosReceipt ok, orderNo=" + orderNo + " mode=" + mode);
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
        if (!ensureConnected()) {
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
        if (!ensureConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.setAlignment(1, null);
            printerService.printTextWithFont("** 測試列印 **\n", null, 32, null);
            printerService.setAlignment(0, null);
            printerService.printText("Time: " + new java.util.Date().toString() + "\n", null);
            printerService.printText("APK: Sunmi POS Bridge\n", null);
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
