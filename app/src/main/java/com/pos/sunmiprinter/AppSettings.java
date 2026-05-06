package com.pos.sunmiprinter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.UUID;

/**
 * 應用程式設定（SharedPreferences）
 * v20260603 新增:
 *   - apiToken: HTTP Server 驗證用 token，首次啟動自動產生 UUID
 *   - lastPrintAt / lastPrintOk / lastPrintError: 最近一次列印狀態，給 /ping 與健康檢查 UI 使用
 */
public class AppSettings {

    private static final String PREF_NAME = "pos_settings";

    // ===== HTTP Server =====
    public static final int DEFAULT_HTTP_PORT = 8080;
    private static final String KEY_HTTP_PORT = "http_port";

    // ===== API Token (v20260603) =====
    private static final String KEY_API_TOKEN = "api_token";

    // ===== 最近列印狀態 (v20260603) =====
    private static final String KEY_LAST_PRINT_AT = "last_print_at";
    private static final String KEY_LAST_PRINT_OK = "last_print_ok";
    private static final String KEY_LAST_PRINT_ERROR = "last_print_error";

    // ===== Sunmi 內建印表機 =====
    private static final String KEY_SUNMI_ENABLED = "sunmi_enabled";
    private static final String KEY_SUNMI_AUTO_CUT = "sunmi_auto_cut";
    private static final String KEY_SUNMI_AUTO_DRAWER = "sunmi_auto_drawer";
    private static final String KEY_SUNMI_BUZZER = "sunmi_buzzer";
    private static final String KEY_SUNMI_ROLE = "sunmi_role";

    // ===== 藍牙印表機 =====
    private static final String KEY_BT_ENABLED = "bt_enabled";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_BT_NAME = "bt_name";
    private static final String KEY_BT_AUTO_CONNECT = "bt_auto_connect";
    private static final String KEY_BT_AUTO_CUT = "bt_auto_cut";
    private static final String KEY_BT_BUZZER = "bt_buzzer";
    private static final String KEY_BT_ROLE = "bt_role";

    // ===== 網路印表機 =====
    private static final String KEY_NET_ENABLED = "net_enabled";
    private static final String KEY_NET_IP = "net_ip";
    private static final String KEY_NET_PORT = "net_port";
    private static final String KEY_NET_AUTO_CONNECT = "net_auto_connect";
    private static final String KEY_NET_AUTO_CUT = "net_auto_cut";
    private static final String KEY_NET_BUZZER = "net_buzzer";
    private static final String KEY_NET_ROLE = "net_role";

    // ===== 收據設定 =====
    private static final String KEY_STORE_NAME = "store_name";
    private static final String KEY_STORE_PHONE = "store_phone";
    private static final String KEY_STORE_ADDRESS = "store_address";
    private static final String KEY_RECEIPT_FOOTER = "receipt_footer";
    private static final String KEY_PRINT_COPIES = "print_copies";

    private final SharedPreferences sp;

    public AppSettings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 第一次啟動自動產生 token
        if (!sp.contains(KEY_API_TOKEN)) {
            String token = UUID.randomUUID().toString().replace("-", "");
            sp.edit().putString(KEY_API_TOKEN, token).apply();
        }
    }

    // ===== HTTP =====
    public int getHttpPort() { return sp.getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT); }
    public void setHttpPort(int v) { sp.edit().putInt(KEY_HTTP_PORT, v).apply(); }

    // ===== API Token =====
    public String getApiToken() { return sp.getString(KEY_API_TOKEN, ""); }
    public void setApiToken(String v) { sp.edit().putString(KEY_API_TOKEN, v == null ? "" : v).apply(); }
    public void regenerateApiToken() {
        String token = UUID.randomUUID().toString().replace("-", "");
        sp.edit().putString(KEY_API_TOKEN, token).apply();
    }

    // ===== 最近列印狀態 =====
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

    // ===== Sunmi =====
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

    // ===== Bluetooth =====
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

    // ===== Network =====
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

    // ===== Receipt =====
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

    // ===== JSON =====
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
        sb.append("\"printCopies\":").append(getPrintCopies());
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
        } catch (Exception ignored) {}
    }

    public void resetAll() {
        sp.edit().clear().apply();
        // 重新產生 token
        regenerateApiToken();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
