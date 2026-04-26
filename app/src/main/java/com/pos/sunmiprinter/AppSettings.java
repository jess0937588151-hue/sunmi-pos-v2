package com.pos.sunmiprinter;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {

    private static final String PREF_NAME = "pos_printer_settings";

    // 网址
    private static final String KEY_URL = "pos_url";
    private static final String DEFAULT_URL = "https://jess0937588151-hue.github.io/2234/";

    // 内建印表机
    private static final String KEY_SUNMI_ENABLED = "sunmi_enabled";
    private static final String KEY_SUNMI_AUTO_CUT = "sunmi_auto_cut";
    private static final String KEY_SUNMI_AUTO_DRAWER = "sunmi_auto_drawer";
    private static final String KEY_SUNMI_BUZZER = "sunmi_buzzer";
    private static final String KEY_SUNMI_ROLE = "sunmi_role"; // customer / kitchen / both

    // 蓝牙印表机
    private static final String KEY_BT_ENABLED = "bt_enabled";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_BT_NAME = "bt_name";
    private static final String KEY_BT_AUTO_CONNECT = "bt_auto_connect";
    private static final String KEY_BT_AUTO_CUT = "bt_auto_cut";
    private static final String KEY_BT_BUZZER = "bt_buzzer";
    private static final String KEY_BT_ROLE = "bt_role";

    // 网路印表机
    private static final String KEY_NET_ENABLED = "net_enabled";
    private static final String KEY_NET_IP = "net_ip";
    private static final String KEY_NET_PORT = "net_port";
    private static final String KEY_NET_AUTO_CONNECT = "net_auto_connect";
    private static final String KEY_NET_AUTO_CUT = "net_auto_cut";
    private static final String KEY_NET_BUZZER = "net_buzzer";
    private static final String KEY_NET_ROLE = "net_role";

    // 收据设定
    private static final String KEY_STORE_NAME = "store_name";
    private static final String KEY_STORE_PHONE = "store_phone";
    private static final String KEY_STORE_ADDRESS = "store_address";
    private static final String KEY_RECEIPT_FOOTER = "receipt_footer";
    private static final String KEY_PRINT_COPIES = "print_copies";

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 网址 ====================

    public String getUrl() {
        return prefs.getString(KEY_URL, DEFAULT_URL);
    }

    public void setUrl(String url) {
        prefs.edit().putString(KEY_URL, url).apply();
    }

    public String getDefaultUrl() {
        return DEFAULT_URL;
    }

    // ==================== 内建印表机 ====================

    public boolean isSunmiEnabled() {
        return prefs.getBoolean(KEY_SUNMI_ENABLED, true);
    }

    public void setSunmiEnabled(boolean v) {
        prefs.edit().putBoolean(KEY_SUNMI_ENABLED, v).apply();
    }

    public boolean isSunmiAutoCut() {
        return prefs.getBoolean(KEY_SUNMI_AUTO_CUT, true);
    }

    public void setSunmiAutoCut(boolean v) {
        prefs.edit().putBoolean(KEY_SUNMI_AUTO_CUT, v).apply();
    }

    public boolean isSunmiAutoDrawer() {
        return prefs.getBoolean(KEY_SUNMI_AUTO_DRAWER, true);
    }

    public void setSunmiAutoDrawer(boolean v) {
        prefs.edit().putBoolean(KEY_SUNMI_AUTO_DRAWER, v).apply();
    }

    public boolean isSunmiBuzzer() {
        return prefs.getBoolean(KEY_SUNMI_BUZZER, false);
    }

    public void setSunmiBuzzer(boolean v) {
        prefs.edit().putBoolean(KEY_SUNMI_BUZZER, v).apply();
    }

    public String getSunmiRole() {
        return prefs.getString(KEY_SUNMI_ROLE, "customer");
    }

    public void setSunmiRole(String v) {
        prefs.edit().putString(KEY_SUNMI_ROLE, v).apply();
    }

    // ==================== 蓝牙印表机 ====================

    public boolean isBtEnabled() {
        return prefs.getBoolean(KEY_BT_ENABLED, false);
    }

    public void setBtEnabled(boolean v) {
        prefs.edit().putBoolean(KEY_BT_ENABLED, v).apply();
    }

    public String getBtAddress() {
        return prefs.getString(KEY_BT_ADDRESS, "");
    }

    public void setBtAddress(String v) {
        prefs.edit().putString(KEY_BT_ADDRESS, v).apply();
    }

    public String getBtName() {
        return prefs.getString(KEY_BT_NAME, "");
    }

    public void setBtName(String v) {
        prefs.edit().putString(KEY_BT_NAME, v).apply();
    }

    public boolean isBtAutoConnect() {
        return prefs.getBoolean(KEY_BT_AUTO_CONNECT, true);
    }

    public void setBtAutoConnect(boolean v) {
        prefs.edit().putBoolean(KEY_BT_AUTO_CONNECT, v).apply();
    }

    public boolean isBtAutoCut() {
        return prefs.getBoolean(KEY_BT_AUTO_CUT, true);
    }

    public void setBtAutoCut(boolean v) {
        prefs.edit().putBoolean(KEY_BT_AUTO_CUT, v).apply();
    }

    public boolean isBtBuzzer() {
        return prefs.getBoolean(KEY_BT_BUZZER, true);
    }

    public void setBtBuzzer(boolean v) {
        prefs.edit().putBoolean(KEY_BT_BUZZER, v).apply();
    }

    public String getBtRole() {
        return prefs.getString(KEY_BT_ROLE, "kitchen");
    }

    public void setBtRole(String v) {
        prefs.edit().putString(KEY_BT_ROLE, v).apply();
    }

    // ==================== 网路印表机 ====================

    public boolean isNetEnabled() {
        return prefs.getBoolean(KEY_NET_ENABLED, false);
    }

    public void setNetEnabled(boolean v) {
        prefs.edit().putBoolean(KEY_NET_ENABLED, v).apply();
    }

    public String getNetIp() {
        return prefs.getString(KEY_NET_IP, "");
    }

    public void setNetIp(String v) {
        prefs.edit().putString(KEY_NET_IP, v).apply();
    }

    public int getNetPort() {
        return prefs.getInt(KEY_NET_PORT, 9100);
    }

    public void setNetPort(int v) {
        prefs.edit().putInt(KEY_NET_PORT, v).apply();
    }

    public boolean isNetAutoConnect() {
        return prefs.getBoolean(KEY_NET_AUTO_CONNECT, true);
    }

    public void setNetAutoConnect(boolean v) {
        prefs.edit().putBoolean(KEY_NET_AUTO_CONNECT, v).apply();
    }

    public boolean isNetAutoCut() {
        return prefs.getBoolean(KEY_NET_AUTO_CUT, true);
    }

    public void setNetAutoCut(boolean v) {
        prefs.edit().putBoolean(KEY_NET_AUTO_CUT, v).apply();
    }

    public boolean isNetBuzzer() {
        return prefs.getBoolean(KEY_NET_BUZZER, true);
    }

    public void setNetBuzzer(boolean v) {
        prefs.edit().putBoolean(KEY_NET_BUZZER, v).apply();
    }

    public String getNetRole() {
        return prefs.getString(KEY_NET_ROLE, "kitchen");
    }

    public void setNetRole(String v) {
        prefs.edit().putString(KEY_NET_ROLE, v).apply();
    }

    // ==================== 收据设定 ====================

    public String getStoreName() {
        return prefs.getString(KEY_STORE_NAME, "");
    }

    public void setStoreName(String v) {
        prefs.edit().putString(KEY_STORE_NAME, v).apply();
    }

    public String getStorePhone() {
        return prefs.getString(KEY_STORE_PHONE, "");
    }

    public void setStorePhone(String v) {
        prefs.edit().putString(KEY_STORE_PHONE, v).apply();
    }

    public String getStoreAddress() {
        return prefs.getString(KEY_STORE_ADDRESS, "");
    }

    public void setStoreAddress(String v) {
        prefs.edit().putString(KEY_STORE_ADDRESS, v).apply();
    }

    public String getReceiptFooter() {
        return prefs.getString(KEY_RECEIPT_FOOTER, "谢谢光临");
    }

    public void setReceiptFooter(String v) {
        prefs.edit().putString(KEY_RECEIPT_FOOTER, v).apply();
    }

    public int getPrintCopies() {
        return prefs.getInt(KEY_PRINT_COPIES, 1);
    }

    public void setPrintCopies(int v) {
        prefs.edit().putInt(KEY_PRINT_COPIES, v).apply();
    }

    // ==================== 汇出全部设定（给JS用）====================

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"url\":\"").append(escape(getUrl())).append("\",");
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

    public void fromJson(String jsonStr) {
        try {
            org.json.JSONObject j = new org.json.JSONObject(jsonStr);
            if (j.has("url")) setUrl(j.getString("url"));
            if (j.has("sunmiEnabled")) setSunmiEnabled(j.getBoolean("sunmiEnabled"));
            if (j.has("sunmiAutoCut")) setSunmiAutoCut(j.getBoolean("sunmiAutoCut"));
            if (j.has("sunmiAutoDrawer")) setSunmiAutoDrawer(j.getBoolean("sunmiAutoDrawer"));
            if (j.has("sunmiBuzzer")) setSunmiBuzzer(j.getBoolean("sunmiBuzzer"));
            if (j.has("sunmiRole")) setSunmiRole(j.getString("sunmiRole"));
            if (j.has("btEnabled")) setBtEnabled(j.getBoolean("btEnabled"));
            if (j.has("btAddress")) setBtAddress(j.getString("btAddress"));
            if (j.has("btName")) setBtName(j.getString("btName"));
            if (j.has("btAutoConnect")) setBtAutoConnect(j.getBoolean("btAutoConnect"));
            if (j.has("btAutoCut")) setBtAutoCut(j.getBoolean("btAutoCut"));
            if (j.has("btBuzzer")) setBtBuzzer(j.getBoolean("btBuzzer"));
            if (j.has("btRole")) setBtRole(j.getString("btRole"));
            if (j.has("netEnabled")) setNetEnabled(j.getBoolean("netEnabled"));
            if (j.has("netIp")) setNetIp(j.getString("netIp"));
            if (j.has("netPort")) setNetPort(j.getInt("netPort"));
            if (j.has("netAutoConnect")) setNetAutoConnect(j.getBoolean("netAutoConnect"));
            if (j.has("netAutoCut")) setNetAutoCut(j.getBoolean("netAutoCut"));
            if (j.has("netBuzzer")) setNetBuzzer(j.getBoolean("netBuzzer"));
            if (j.has("netRole")) setNetRole(j.getString("netRole"));
            if (j.has("storeName")) setStoreName(j.getString("storeName"));
            if (j.has("storePhone")) setStorePhone(j.getString("storePhone"));
            if (j.has("storeAddress")) setStoreAddress(j.getString("storeAddress"));
            if (j.has("receiptFooter")) setReceiptFooter(j.getString("receiptFooter"));
            if (j.has("printCopies")) setPrintCopies(j.getInt("printCopies"));
        } catch (Exception e) {
            android.util.Log.e("AppSettings", "fromJson error", e);
        }
    }

    // ==================== 重置 ====================

    public void resetAll() {
        prefs.edit().clear().apply();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
