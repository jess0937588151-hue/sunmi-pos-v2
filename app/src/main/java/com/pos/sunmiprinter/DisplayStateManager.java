package com.pos.sunmiprinter;

/**
 * DisplayStateManager — 客顯共用狀態容器（純記憶體，執行緒安全）
 * 版本：v20260525
 *
 * 職責：
 *   - 儲存 Web POS 最後一次推送的客顯 JSON 字串
 *   - 提供 getStateJson() 供 DisplayHttpServer 的 /display/state 輪詢端點使用
 *   - 提供 getType() / getUpdatedAt() 供 /display/ping 顯示簡要狀態
 *   - 採用 synchronized 靜態方法確保多執行緒讀寫安全
 *
 * 設計原則：
 *   - 純記憶體，不寫 DB、不寫檔，APK 重啟後客顯自動回到 idle
 *   - 預設狀態為 type=idle、storeName 空字串（客顯自行顯示待機畫面）
 *   - 不解析 JSON（不引入外部套件），只存原始字串
 *   - 盡量輕量，不持有 Context 引用
 */
public class DisplayStateManager {

    private static final String TAG = "DisplayStateManager";

    // 預設待機狀態 JSON
    private static final String DEFAULT_STATE_JSON =
            "{\"type\":\"idle\",\"storeName\":\"\",\"items\":[]," +
            "\"subtotal\":0,\"total\":0,\"message\":\"\"}";

    // 當前狀態（執行緒安全，用 volatile + synchronized 雙重保護）
    private static volatile String currentStateJson = DEFAULT_STATE_JSON;
    private static volatile String currentType = "idle";
    private static volatile long   updatedAt    = 0L;

    // ── 禁止實例化（純靜態工具類別） ──
    private DisplayStateManager() {}

    /**
     * 更新客顯狀態
     * @param jsonBody Web POS POST /display/update 傳來的原始 JSON 字串
     */
    public static synchronized void update(String jsonBody) {
        if (jsonBody == null || jsonBody.trim().isEmpty()) {
            LogManager.w(TAG, "update: empty jsonBody, ignored");
            return;
        }
        currentStateJson = jsonBody.trim();
        updatedAt = System.currentTimeMillis();

        // 從 JSON 字串中快速提取 type 欄位（避免引入 JSON 解析套件）
        currentType = extractStringField(currentStateJson, "type", "idle");

        LogManager.i(TAG, "display state updated type=" + currentType
                + " len=" + currentStateJson.length()
                + " at=" + updatedAt);
    }

    /**
     * 重置為預設待機狀態（APK 重啟或服務停止時呼叫）
     */
    public static synchronized void reset() {
        currentStateJson = DEFAULT_STATE_JSON;
        currentType = "idle";
        updatedAt = System.currentTimeMillis();
        LogManager.i(TAG, "display state reset to idle");
    }

    /**
     * 取得當前完整 JSON 字串（供 /display/state 端點回傳）
     */
    public static synchronized String getStateJson() {
        return currentStateJson;
    }

    /**
     * 取得當前 type 欄位值（"cart" | "paid" | "idle"）
     */
    public static synchronized String getType() {
        return currentType;
    }

    /**
     * 取得最後更新的 Unix 毫秒時間戳（供 /display/ping 回傳）
     */
    public static synchronized long getUpdatedAt() {
        return updatedAt;
    }

    // ==================== 內部工具 ====================

    /**
     * 從 JSON 字串中提取指定欄位的字串值（簡易實作，僅處理純字串欄位）
     * 例：extractStringField(json, "type", "idle") → "cart"
     * 注意：只適用於值為簡單字串的欄位，不處理巢狀 JSON
     */
    static String extractStringField(String json, String field, String defaultValue) {
        if (json == null) return defaultValue;
        try {
            // 搜尋 "field":"value" 模式（允許空白）
            String pattern = "\"" + field + "\"";
            int idx = json.indexOf(pattern);
            if (idx < 0) return defaultValue;

            int colon = json.indexOf(':', idx + pattern.length());
            if (colon < 0) return defaultValue;

            // 跳過空白
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) {
                start++;
            }
            if (start >= json.length()) return defaultValue;

            if (json.charAt(start) == '"') {
                // 字串值
                int end = json.indexOf('"', start + 1);
                if (end < 0) return defaultValue;
                return json.substring(start + 1, end);
            }
            return defaultValue;
        } catch (Throwable t) {
            return defaultValue;
        }
    }
}
