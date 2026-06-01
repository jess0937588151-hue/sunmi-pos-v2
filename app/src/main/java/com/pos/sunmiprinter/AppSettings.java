package com.pos.sunmiprinter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.UUID;

/**
 * 應用程式設定（SharedPreferences）
 * v20260603 新增: apiToken / lastPrint*
 * v20260608 新增: 6 組字體大小（14~60 夾值）
 * v20260531 新增: 廚房單獨立字級 fontKitchenItem / fontKitchenInfo（廚房單品名放大、師傅遠看清楚）
 * v20260620 新增: 廚房單子選項獨立字級 fontKitchenOption（品名與子選項可分開設定大小）
 */
public class AppSettings {

    private static final String PREF_NAME = "pos_settings";

    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final String KEY_HTTP_PORT = "http_port";

    private static final String KEY_API_TOKEN = "api_token";

    private static final String KEY_LAST_PRINT_AT = "last_print_at";
    private static final String KEY_LAST_PRINT_OK = "last_print_ok";
    private static final String KEY_LAST_PRINT_ERROR = "last_print_error";

    private static final String KEY_SUNMI_ENABLED = "sunmi_enabled";
    private static final String KEY_SUNMI_AUTO_CUT = "sunmi_auto_cut";
    private static final String KEY_SUNMI_AUTO_DRAWER = "sunmi_auto_drawer";
    private static final String KEY_SUNMI_BUZZER = "sunmi_buzzer";
    private static final String KEY_SUNMI_ROLE = "sunmi_role";

    private static final String KEY_BT_ENABLED = "bt_enabled";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_BT_NAME = "bt_name";
    private static final String KEY_BT_AUTO_CONNECT = "bt_auto_connect";
    private static final String KEY_BT_AUTO_CUT = "bt_auto_cut";
    private static final String KEY_BT_BUZZER = "bt_buzzer";
    private static final String KEY_BT_ROLE = "bt_role";

    private static final String KEY_NET_ENABLED = "net_enabled";
    private static final String KEY_NET_IP = "net_ip";
    private static final String KEY_NET_PORT = "net_port";
    private static final String KEY_NET_AUTO_CONNECT = "net_auto_connect";
    private static final String KEY_NET_AUTO_CUT = "net_auto_cut";
    private static final String KEY_NET_BUZZER = "net_buzzer";
    private static final String KEY_NET_ROLE = "net_role";

    private static final String KEY_STORE_NAME = "store_name";
    private static final String KEY_STORE_PHONE = "store_phone";
    private static final String KEY_STORE_ADDRESS = "store_address";
    private static final String KEY_RECEIPT_FOOTER = "receipt_footer";
    private static final String KEY_PRINT_COPIES = "print_copies";

    // ===== v20260608: 字體大小（6 組，顧客單/收據用） =====
    private static final String KEY_FONT_STORE    = "font_store";    // 店名
    private static final String KEY_FONT_SUBTITLE = "font_subtitle"; // 副標
    private static final String KEY_FONT_INFO     = "font_info";     // 訂單資訊（單號/時間/類型/付款/分隔線）
    private static final String KEY_FONT_ITEM     = "font_item";     // 品項+選項
    private static final String KEY_FONT_TOTAL    = "font_total";    // 總額
    private static final String KEY_FONT_FOOTER   = "font_footer";   // 頁尾

    // ===== v20260531: 廚房單獨立字級（3 組） =====
    private static final String KEY_FONT_KITCHEN_ITEM = "font_kitchen_item"; // 廚房單品名（最大、師傅遠看）
    private static final String KEY_FONT_KITCHEN_INFO = "font_kitchen_info"; // 廚房單資訊（單號/時間/桌號等）
    private static final String KEY_FONT_KITCHEN_OPTION = "font_kitchen_option"; // v20260620 廚房單子選項（灑粉/加料等）

    public static final int FONT_MIN = 14;
    public static final int FONT_MAX = 120;
    public static final int DEFAULT_FONT_STORE    = 30;
    public static final int DEFAULT_FONT_SUBTITLE = 24;
    public static final int DEFAULT_FONT_INFO     = 22;
    public static final int DEFAULT_FONT_ITEM     = 26;
    public static final int DEFAULT_FONT_TOTAL    = 28;
    public static final int DEFAULT_FONT_FOOTER   = 22;
    // 廚房單預設：品名 38（師傅遠看清楚）、資訊 26、子選項 30（v20260620）
    public static final int DEFAULT_FONT_KITCHEN_ITEM = 38;
    public static final int DEFAULT_FONT_KITCHEN_INFO = 26;
    public static final int DEFAULT_FONT_KITCHEN_OPTION = 30;

    private final SharedPreferences sp;

    public AppSettings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        if (!sp.contains(KEY_API_TOKEN)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            sp.edit().putString(KEY_API_TOKEN, token).apply();
        }
    }

    public int getHttpPort() { return sp.getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT); }
    public void setHttpPort(int v) { sp.edit().putInt(KEY_HTTP_PORT, v).apply(); }

    public String getApiToken() { return sp.getString(KEY_API_TOKEN, ""); }
    public void setApiToken(String v) { sp.edit().putString(KEY_API_TOKEN, v == null ? "" : v).apply(); }
    public void regenerateApiToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sp.edit().putString(KEY_API_TOKEN, token).apply();
    }

    public long getLastPrintAt() { return sp.getLong(KEY_LAST_PRINT_AT, 0L); }
    public boolean getLastPrintOk() { return sp.getBoolean(KEY_LAST_PRINT_OK, true); }
    public String getLastPrintError() { return sp.getString(KEY_LAST_PRINT_ERROR, ""); }
    public void recordPrintResult(boolean ok, String error) {
        sp.edit()
                .putLong(KEY_LAST_PRINT_AT, System.currentTimeMillis())
                .putBoolean(KEY_LAST_PRINT_OK, ok)
                .putString(KEY_LAST_PRINT_ERROR, ok ? "" : (error == null ? "" : error))
                .apply();
    }

    public boolean isSunmiEnabled() { return sp.getBoolean(KEY_SUNMI_ENABLED, true); }
    public void setSunmiEnabled(boolean v) { sp.edit().putBoolean(KEY_SUNMI_ENABLED, v).apply(); }
    public boolean isSunmiAutoCut() { return sp.getBoolean(KEY_SUNMI_AUTO_CUT, true); }
    public void setSunmiAutoCut(boolean v) { sp.edit().putBoolean(KEY_SUNMI_AUTO_CUT, v).apply(); }
    public boolean isSunmiAutoDrawer() { return sp.getBoolean(KEY_SUNMI_AUTO_DRAWER, false); }
    public void setSunmiAutoDrawer(boolean v) { sp.edit().putBoolean(KEY_SUNMI_AUTO_DRAWER, v).apply(); }
    public boolean isSunmiBuzzer() { return sp.getBoolean(KEY_SUNMI_BUZZER, false); }
    public void setSunmiBuzzer(boolean v) { sp.edit().putBoolean(KEY_SUNMI_BUZZER, v).apply(); }
    public String getSunmiRole() { return sp.getString(KEY_SUNMI_ROLE, "receipt"); }
    public void setSunmiRole(String v) { sp.edit().putString(KEY_SUNMI_ROLE, v).apply(); }

    public boolean isBtEnabled() { return sp.getBoolean(KEY_BT_ENABLED, false); }
    public void setBtEnabled(boolean v) { sp.edit().putBoolean(KEY_BT_ENABLED, v).apply(); }
    public String getBtAddress() { return sp.getString(KEY_BT_ADDRESS, ""); }
    public void setBtAddress(String v) { sp.edit().putString(KEY_BT_ADDRESS, v).apply(); }
    public String getBtName() { return sp.getString(KEY_BT_NAME, ""); }
    public void setBtName(String v) { sp.edit().putString(KEY_BT_NAME, v).apply(); }
    public boolean isBtAutoConnect() { return sp.getBoolean(KEY_BT_AUTO_CONNECT, true); }
    public void setBtAutoConnect(boolean v) { sp.edit().putBoolean(KEY_BT_AUTO_CONNECT, v).apply(); }
    public boolean isBtAutoCut() { return sp.getBoolean(KEY_BT_AUTO_CUT, true); }
    public void setBtAutoCut(boolean v) { sp.edit().putBoolean(KEY_BT_AUTO_CUT, v).apply(); }
    public boolean isBtBuzzer() { return sp.getBoolean(KEY_BT_BUZZER, false); }
    public void setBtBuzzer(boolean v) { sp.edit().putBoolean(KEY_BT_BUZZER, v).apply(); }
    public String getBtRole() { return sp.getString(KEY_BT_ROLE, "kitchen"); }
    public void setBtRole(String v) { sp.edit().putString(KEY_BT_ROLE, v).apply(); }

    public boolean isNetEnabled() { return sp.getBoolean(KEY_NET_ENABLED, false); }
    public void setNetEnabled(boolean v) { sp.edit().putBoolean(KEY_NET_ENABLED, v).apply(); }
    public String getNetIp() { return sp.getString(KEY_NET_IP, ""); }
    public void setNetIp(String v) { sp.edit().putString(KEY_NET_IP, v).apply(); }
    public int getNetPort() { return sp.getInt(KEY_NET_PORT, 9100); }
    public void setNetPort(int v) { sp.edit().putInt(KEY_NET_PORT, v).apply(); }
    public boolean isNetAutoConnect() { return sp.getBoolean(KEY_NET_AUTO_CONNECT, true); }
    public void setNetAutoConnect(boolean v) { sp.edit().putBoolean(KEY_NET_AUTO_CONNECT, v).apply(); }
    public boolean isNetAutoCut() { return sp.getBoolean(KEY_NET_AUTO_CUT, true); }
    public void setNetAutoCut(boolean v) { sp.edit().putBoolean(KEY_NET_AUTO_CUT, v).apply(); }
    public boolean isNetBuzzer() { return sp.getBoolean(KEY_NET_BUZZER, false); }
    public void setNetBuzzer(boolean v) { sp.edit().putBoolean(KEY_NET_BUZZER, v).apply(); }
    public String getNetRole() { return sp.getString(KEY_NET_ROLE, "label"); }
    public void setNetRole(String v) { sp.edit().putString(KEY_NET_ROLE, v).apply(); }

    public String getStoreName() { return sp.getString(KEY_STORE_NAME, ""); }
    public void setStoreName(String v) { sp.edit().putString(KEY_STORE_NAME, v).apply(); }
    public String getStorePhone() { return sp.getString(KEY_STORE_PHONE, ""); }
    public void setStorePhone(String v) { sp.edit().putString(KEY_STORE_PHONE, v).apply(); }
    public String getStoreAddress() { return sp.getString(KEY_STORE_ADDRESS, ""); }
    public void setStoreAddress(String v) { sp.edit().putString(KEY_STORE_ADDRESS, v).apply(); }
    public String getReceiptFooter() { return sp.getString(KEY_RECEIPT_FOOTER, "謝謝光臨"); }
    public void setReceiptFooter(String v) { sp.edit().putString(KEY_RECEIPT_FOOTER, v).apply(); }
    public int getPrintCopies() { return sp.getInt(KEY_PRINT_COPIES, 1); }
    public void setPrintCopies(int v) { sp.edit().putInt(KEY_PRINT_COPIES, v).apply(); }

    // ===== v20260608: 字體大小（顧客單/收據） =====
    private int clampFont(int v) {
        if (v < FONT_MIN) return FONT_MIN;
        if (v > FONT_MAX) return FONT_MAX;
        return v;
    }
    public int getFontStore()    { return clampFont(sp.getInt(KEY_FONT_STORE,    DEFAULT_FONT_STORE)); }
    public int getFontSubtitle() { return clampFont(sp.getInt(KEY_FONT_SUBTITLE, DEFAULT_FONT_SUBTITLE)); }
    public int getFontInfo()     { return clampFont(sp.getInt(KEY_FONT_INFO,     DEFAULT_FONT_INFO)); }
    public int getFontItem()     { return clampFont(sp.getInt(KEY_FONT_ITEM,     DEFAULT_FONT_ITEM)); }
    public int getFontTotal()    { return clampFont(sp.getInt(KEY_FONT_TOTAL,    DEFAULT_FONT_TOTAL)); }
    public int getFontFooter()   { return clampFont(sp.getInt(KEY_FONT_FOOTER,   DEFAULT_FONT_FOOTER)); }
    public void setFontStore(int v)    { sp.edit().putInt(KEY_FONT_STORE,    clampFont(v)).apply(); }
    public void setFontSubtitle(int v) { sp.edit().putInt(KEY_FONT_SUBTITLE, clampFont(v)).apply(); }
    public void setFontInfo(int v)     { sp.edit().putInt(KEY_FONT_INFO,     clampFont(v)).apply(); }
    public void setFontItem(int v)     { sp.edit().putInt(KEY_FONT_ITEM,     clampFont(v)).apply(); }
    public void setFontTotal(int v)    { sp.edit().putInt(KEY_FONT_TOTAL,    clampFont(v)).apply(); }
    public void setFontFooter(int v)   { sp.edit().putInt(KEY_FONT_FOOTER,   clampFont(v)).apply(); }

    // ===== v20260531: 廚房單獨立字級 =====
    public int getFontKitchenItem() { return clampFont(sp.getInt(KEY_FONT_KITCHEN_ITEM, DEFAULT_FONT_KITCHEN_ITEM)); }
    public int getFontKitchenInfo() { return clampFont(sp.getInt(KEY_FONT_KITCHEN_INFO, DEFAULT_FONT_KITCHEN_INFO)); }
    public int getFontKitchenOption() { return clampFont(sp.getInt(KEY_FONT_KITCHEN_OPTION, DEFAULT_FONT_KITCHEN_OPTION)); } // v20260620
    public void setFontKitchenItem(int v) { sp.edit().putInt(KEY_FONT_KITCHEN_ITEM, clampFont(v)).apply(); }
    public void setFontKitchenInfo(int v) { sp.edit().putInt(KEY_FONT_KITCHEN_INFO, clampFont(v)).apply(); }
    public void setFontKitchenOption(int v) { sp.edit().putInt(KEY_FONT_KITCHEN_OPTION, clampFont(v)).apply(); } // v20260620

    public void resetFontDefaults() {
        sp.edit()
                .putInt(KEY_FONT_STORE,    DEFAULT_FONT_STORE)
                .putInt(KEY_FONT_SUBTITLE, DEFAULT_FONT_SUBTITLE)
                .putInt(KEY_FONT_INFO,     DEFAULT_FONT_INFO)
                .putInt(KEY_FONT_ITEM,     DEFAULT_FONT_ITEM)
                .putInt(KEY_FONT_TOTAL,    DEFAULT_FONT_TOTAL)
                .putInt(KEY_FONT_FOOTER,   DEFAULT_FONT_FOOTER)
                .putInt(KEY_FONT_KITCHEN_ITEM, DEFAULT_FONT_KITCHEN_ITEM)
                .putInt(KEY_FONT_KITCHEN_INFO, DEFAULT_FONT_KITCHEN_INFO)
                .putInt(KEY_FONT_KITCHEN_OPTION, DEFAULT_FONT_KITCHEN_OPTION)
                .apply();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"httpPort\":").append(getHttpPort()).append(",");
        sb.append("\"apiToken\":\"").append(escape(getApiToken())).append("\",");
        sb.append("\"lastPrintAt\":").append(getLastPrintAt()).append(",");
        sb.append("\"lastPrintOk\":").append(getLastPrintOk()).append(",");
        sb.append("\"lastPrintError\":\"").append(escape(getLastPrintError())).append("\",");
        sb.append("\"sunmiEnabled\":").append(isSunmiEnabled()).append(",");
        sb.append("\"sunmiAutoCut\":").append(isSunmiAutoCut()).append(",");
        sb.append("\"sunmiAutoDrawer\":").append(isSunmiAutoDrawer()).append(",");
        sb.append("\"sunmiBuzzer\":").append(isSunmiBuzzer()).append(",");
        sb.append("\"sunmiRole\":\"").append(escape(getSunmiRole())).append("\",");
        sb.append("\"btEnabled\":").append(isBtEnabled()).append(",");
        sb.append("\"btAddress\":\"").append(escape(getBtAddress())).append("\",");
        sb.append("\"btName\":\"").append(escape(getBtName())).append("\",");
        sb.append("\"btAutoConnect\":").append(isBtAutoConnect()).append(",");
        sb.append("\"btAutoCut\":").append(isBtAutoCut()).append(",");
        sb.append("\"btBuzzer\":").append(isBtBuzzer()).append(",");
        sb.append("\"btRole\":\"").append(escape(getBtRole())).append("\",");
        sb.append("\"netEnabled\":").append(isNetEnabled()).append(",");
        sb.append("\"netIp\":\"").append(escape(getNetIp())).append("\",");
        sb.append("\"netPort\":").append(getNetPort()).append(",");
        sb.append("\"netAutoConnect\":").append(isNetAutoConnect()).append(",");
        sb.append("\"netAutoCut\":").append(isNetAutoCut()).append(",");
        sb.append("\"netBuzzer\":").append(isNetBuzzer()).append(",");
        sb.append("\"netRole\":\"").append(escape(getNetRole())).append("\",");
        sb.append("\"storeName\":\"").append(escape(getStoreName())).append("\",");
        sb.append("\"storePhone\":\"").append(escape(getStorePhone())).append("\",");
        sb.append("\"storeAddress\":\"").append(escape(getStoreAddress())).append("\",");
        sb.append("\"receiptFooter\":\"").append(escape(getReceiptFooter())).append("\",");
        sb.append("\"printCopies\":").append(getPrintCopies()).append(",");
        sb.append("\"fontStore\":").append(getFontStore()).append(",");
        sb.append("\"fontSubtitle\":").append(getFontSubtitle()).append(",");
        sb.append("\"fontInfo\":").append(getFontInfo()).append(",");
        sb.append("\"fontItem\":").append(getFontItem()).append(",");
        sb.append("\"fontTotal\":").append(getFontTotal()).append(",");
        sb.append("\"fontFooter\":").append(getFontFooter()).append(",");
        sb.append("\"fontKitchenItem\":").append(getFontKitchenItem()).append(",");
        sb.append("\"fontKitchenInfo\":").append(getFontKitchenInfo()).append(",");
        sb.append("\"fontKitchenOption\":").append(getFontKitchenOption());
        sb.append("}");
        return sb.toString();
    }

    public void fromJson(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("httpPort")) setHttpPort(o.getInt("httpPort"));
            if (o.has("apiToken")) setApiToken(o.getString("apiToken"));
            if (o.has("sunmiEnabled")) setSunmiEnabled(o.getBoolean("sunmiEnabled"));
            if (o.has("sunmiAutoCut")) setSunmiAutoCut(o.getBoolean("sunmiAutoCut"));
            if (o.has("sunmiAutoDrawer")) setSunmiAutoDrawer(o.getBoolean("sunmiAutoDrawer"));
            if (o.has("sunmiBuzzer")) setSunmiBuzzer(o.getBoolean("sunmiBuzzer"));
            if (o.has("sunmiRole")) setSunmiRole(o.getString("sunmiRole"));
            if (o.has("btEnabled")) setBtEnabled(o.getBoolean("btEnabled"));
            if (o.has("btAddress")) setBtAddress(o.getString("btAddress"));
            if (o.has("btName")) setBtName(o.getString("btName"));
            if (o.has("btAutoConnect")) setBtAutoConnect(o.getBoolean("btAutoConnect"));
            if (o.has("btAutoCut")) setBtAutoCut(o.getBoolean("btAutoCut"));
            if (o.has("btBuzzer")) setBtBuzzer(o.getBoolean("btBuzzer"));
            if (o.has("btRole")) setBtRole(o.getString("btRole"));
            if (o.has("netEnabled")) setNetEnabled(o.getBoolean("netEnabled"));
            if (o.has("netIp")) setNetIp(o.getString("netIp"));
            if (o.has("netPort")) setNetPort(o.getInt("netPort"));
            if (o.has("netAutoConnect")) setNetAutoConnect(o.getBoolean("netAutoConnect"));
            if (o.has("netAutoCut")) setNetAutoCut(o.getBoolean("netAutoCut"));
            if (o.has("netBuzzer")) setNetBuzzer(o.getBoolean("netBuzzer"));
            if (o.has("netRole")) setNetRole(o.getString("netRole"));
            if (o.has("storeName")) setStoreName(o.getString("storeName"));
            if (o.has("storePhone")) setStorePhone(o.getString("storePhone"));
            if (o.has("storeAddress")) setStoreAddress(o.getString("storeAddress"));
            if (o.has("receiptFooter")) setReceiptFooter(o.getString("receiptFooter"));
            if (o.has("printCopies")) setPrintCopies(o.getInt("printCopies"));
            if (o.has("fontStore")) setFontStore(o.getInt("fontStore"));
            if (o.has("fontSubtitle")) setFontSubtitle(o.getInt("fontSubtitle"));
            if (o.has("fontInfo")) setFontInfo(o.getInt("fontInfo"));
            if (o.has("fontItem")) setFontItem(o.getInt("fontItem"));
            if (o.has("fontTotal")) setFontTotal(o.getInt("fontTotal"));
            if (o.has("fontFooter")) setFontFooter(o.getInt("fontFooter"));
            if (o.has("fontKitchenItem")) setFontKitchenItem(o.getInt("fontKitchenItem"));
            if (o.has("fontKitchenInfo")) setFontKitchenInfo(o.getInt("fontKitchenInfo"));
            if (o.has("fontKitchenOption")) setFontKitchenOption(o.getInt("fontKitchenOption"));
        } catch (Exception ignored) {}
    }

    public void resetAll() {
        sp.edit().clear().apply();
        regenerateApiToken();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
