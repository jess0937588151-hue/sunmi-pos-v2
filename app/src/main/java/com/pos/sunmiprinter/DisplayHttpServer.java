package com.pos.sunmiprinter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * DisplayHttpServer — 客顯 HTTP Server
 * 版本：v20260525
 *
 * 職責：
 *   - 監聽 127.0.0.1:8081（與列印 Server 8080 分開，互不影響）
 *   - 接收 Web POS 傳來的客顯資料（購物車、付款完成、待機畫面）
 *   - 保存最新狀態到 DisplayStateManager，供客顯頁面 polling 取得
 *
 * API 端點：
 *   POST /display/update  — Web POS 推送最新客顯資料（需 X-API-Token）
 *   GET  /display/state   — 客顯頁面輪詢取得最新狀態（需 X-API-Token）
 *   GET  /display/ping    — 心跳（無需 Token）
 *   OPTIONS *             — CORS preflight
 *
 * 資料格式（POST /display/update body）：
 * {
 *   "type": "cart" | "paid" | "idle",
 *   "storeName": "店家名稱",
 *   "items": [{ "name":"雞排", "qty":1, "price":70, "options":"加辣" }],
 *   "subtotal": 100,
 *   "total": 100,
 *   "paidAmount": 120,   // 僅 type=paid 時有
 *   "change": 20,        // 僅 type=paid 時有
 *   "paymentMethod": "現金",  // 僅 type=paid 時有
 *   "message": ""        // 自訂訊息，type=idle 時可設廣告語
 * }
 *
 * 設計原則：
 *   - 與 PrintHttpServer(8080) 完全獨立，互不干擾
 *   - 使用與 PrintHttpServer 相同的 ApiToken 驗證機制
 *   - 採用 DisplayStateManager 作為共用狀態容器（純記憶體，無需 DB）
 *   - 所有 JSON 手工序列化（不引入外部 JSON 套件）
 *   - CORS header 完整，允許 Web POS 同源 fetch
 */
public class DisplayHttpServer extends NanoHTTPD {

    private static final String TAG = "DisplayHttpServer";
    public static final int DEFAULT_PORT = 8081;

    private final AppSettings settings;

    public DisplayHttpServer(int port) {
        super("127.0.0.1", port);
        this.settings = LogManager.getAppSettings();
        LogManager.i(TAG, "DisplayHttpServer created on port " + port);
    }

    // ==================== 路由 ====================

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        // CORS preflight
        if (Method.OPTIONS.equals(method)) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
        }

        try {
            // ── 無需 Token 的端點 ──
            if (Method.GET.equals(method) && "/display/ping".equals(uri)) {
                return handlePing();
            }

            // ── 需要 Token 的端點 ──
            if (!checkToken(session)) {
                LogManager.w(TAG, "display unauthorized: " + method + " " + uri);
                return cors(json(false, null, "unauthorized"));
            }

            if (Method.POST.equals(method) && "/display/update".equals(uri)) {
                return handleUpdate(session);
            }
            if (Method.GET.equals(method) && "/display/state".equals(uri)) {
                return handleGetState();
            }

            LogManager.w(TAG, "display not found: " + method + " " + uri);
            return cors(json(false, null, "not found: " + uri));

        } catch (Throwable t) {
            LogManager.e(TAG, "display serve error: " + uri, t);
            return cors(json(false, null, "server error: " + t.getMessage()));
        }
    }

    // ==================== Handler：心跳 ====================

    private Response handlePing() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true,");
        sb.append("\"service\":\"display\",");
        sb.append("\"port\":").append(DEFAULT_PORT).append(",");
        sb.append("\"version\":\"v20260525\",");
        sb.append("\"stateType\":\"").append(escape(DisplayStateManager.getType())).append("\",");
        sb.append("\"updatedAt\":").append(DisplayStateManager.getUpdatedAt());
        sb.append("}");
        Response r = newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
        return cors(r);
    }

    // ==================== Handler：接收 Web POS 推送 ====================

    private Response handleUpdate(IHTTPSession session) {
        try {
            String body = readBody(session);
            if (body == null || body.trim().isEmpty()) {
                LogManager.w(TAG, "handleUpdate: empty body");
                return cors(json(false, null, "empty body"));
            }
            LogManager.i(TAG, "handleUpdate body=" +
                    (body.length() > 200 ? body.substring(0, 200) + "..." : body));

            // 把 JSON body 存入 DisplayStateManager
            DisplayStateManager.update(body);

            return cors(json(true, "{\"saved\":true}", null));
        } catch (Throwable t) {
            LogManager.e(TAG, "handleUpdate failed", t);
            return cors(json(false, null, t.getMessage()));
        }
    }

    // ==================== Handler：客顯頁面 Polling ====================

    private Response handleGetState() {
        String json = DisplayStateManager.getStateJson();
        // 直接回傳 DisplayStateManager 中已儲存的完整 JSON 字串
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":true,");
        sb.append("\"data\":").append(json).append(",");
        sb.append("\"updatedAt\":").append(DisplayStateManager.getUpdatedAt());
        sb.append("}");
        Response r = newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
        return cors(r);
    }

    // ==================== Token 驗證（與 PrintHttpServer 一致） ====================

    private boolean checkToken(IHTTPSession session) {
        if (settings == null) return true;
        String expected = settings.getApiToken();
        if (expected == null || expected.isEmpty()) return true;

        Map<String, String> headers = session.getHeaders();
        String got = headers.get("x-api-token");
        if (got == null) got = headers.get("X-API-Token");
        if (got != null && expected.equals(got)) return true;

        Map<String, List<String>> params = session.getParameters();
        if (params != null) {
            List<String> qs = params.get("token");
            if (qs != null && !qs.isEmpty() && expected.equals(qs.get(0))) return true;
        }
        return false;
    }

    // ==================== 讀取 POST Body ====================

    private String readBody(IHTTPSession session) {
        try {
            Map<String, String> headers = session.getHeaders();
            String cl = headers.get("content-length");
            if (cl == null) cl = headers.get("Content-Length");
            int contentLength = 0;
            if (cl != null) {
                try { contentLength = Integer.parseInt(cl.trim()); } catch (Exception ignored) {}
            }

            java.io.InputStream is = session.getInputStream();
            if (is != null && contentLength > 0) {
                byte[] buf = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int n = is.read(buf, read, contentLength - read);
                    if (n <= 0) break;
                    read += n;
                }
                return new String(buf, 0, read, "UTF-8");
            }

            // fallback: parseBody
            Map<String, String> files = new HashMap<>();
            try { session.parseBody(files); } catch (Exception ignored) {}
            String body = files.get("postData");
            return body == null ? "" : body;

        } catch (Throwable t) {
            LogManager.w(TAG, "readBody failed: " + t.getMessage());
            return "";
        }
    }

    // ==================== 回應工具 ====================

    private Response json(boolean ok, String dataJson, String error) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ok\":").append(ok).append(",");
        if (dataJson == null || dataJson.isEmpty()) {
            sb.append("\"data\":null,");
        } else {
            sb.append("\"data\":").append(dataJson).append(",");
        }
        sb.append("\"error\":").append(error == null ? "null" : ("\"" + escape(error) + "\""));
        sb.append("}");
        return newFixedLengthResponse(Response.Status.OK,
                "application/json; charset=utf-8", sb.toString());
    }

    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Token");
        r.addHeader("Access-Control-Allow-Private-Network", "true");
        r.addHeader("Access-Control-Max-Age", "86400");
        return r;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
