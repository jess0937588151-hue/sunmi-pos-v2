package com.pos.sunmiprinter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 內建 HTTP Server（NanoHTTPD）
 * 監聽 127.0.0.1:port，提供列印 REST API
 * 所有回應加 CORS header，允許本機網頁 fetch 呼叫
 */
public class PrintHttpServer extends NanoHTTPD {

    private static final String TAG = "PrintHttpServer";
    private static final String VERSION = "5.0";

    private final SunmiPrinterManager sunmi;
    private final BluetoothPrinterManager bt;
    private final NetworkPrinterManager net;

    public PrintHttpServer(int port,
                           SunmiPrinterManager sunmi,
                           BluetoothPrinterManager bt,
                           NetworkPrinterManager net) {
        // 只綁 127.0.0.1，不對外開放
        super("127.0.0.1", port);
        this.sunmi = sunmi;
        this.bt = bt;
        this.net = net;
    }

    // ==================== 路由分派 ====================

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, method + " " + uri);

        // 統一加 CORS header（OPTIONS preflight 直接回 200）
        if (method == Method.OPTIONS) {
            return corsOk("");
        }

        try {
            // GET /ping
            if (method == Method.GET && uri.equals("/ping")) {
                return corsOk(jsonOk(new JSONObject()
                        .put("version", VERSION)
                        .put("sunmi", sunmi.isConnected())
                        .put("bluetooth", bt.isConnected())
                        .put("network", net.isConnected())));
            }

            // GET /printer/status
            if (method == Method.GET && uri.equals("/printer/status")) {
                return handleStatus();
            }

            // POST 端點需要讀 body
            if (method == Method.POST) {
                String body = readBody(session);
                JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);

                switch (uri) {
                    case "/print/sunmi":
                        return handlePrintSunmi(json);
                    case "/print/bluetooth":
                        return handlePrintBluetooth(json);
                    case "/print/network":
                        return handlePrintNetwork(json);
                    case "/drawer/open":
                        return handleDrawerOpen(json);
                    default:
                        break;
                }
            }

            return corsOk(jsonError("not found: " + uri), Response.Status.NOT_FOUND);

        } catch (Exception e) {
            Log.e(TAG, "serve error: " + uri, e);
            return corsOk(jsonError(e.getMessage()), Response.Status.INTERNAL_ERROR);
        }
    }

    // ==================== 各端點處理 ====================

    /** GET /printer/status */
    private Response handleStatus() throws Exception {
        JSONObject data = new JSONObject();
        data.put("sunmi_connected", sunmi.isConnected());
        data.put("sunmi_status", sunmi.getPrinterStatus());
        data.put("bluetooth_connected", bt.isConnected());
        data.put("bluetooth_address", bt.getConnectedAddress());
        data.put("network_connected", net.isConnected());
        data.put("network_info", net.getConnectedInfo());
        return corsOk(jsonOk(data));
    }

    /**
     * POST /print/sunmi
     * body: {
     *   "type": "receipt" | "kitchen" | "text" | "raw",  // 預設 "receipt"
     *   "payload": { ... }   // buildBridgePayload 產生的完整 JSON
     *   -- 或舊式簡單格式 --
     *   "text": "...",
     *   "fontSize": 24,
     *   "cut": true,
     *   "openDrawer": false
     * }
     */
    private Response handlePrintSunmi(JSONObject json) throws Exception {
        if (!sunmi.isConnected()) {
            return corsOk(jsonError("Sunmi printer not connected"));
        }

        // 支援新式 payload 格式（來自 print-service.js buildBridgePayload）
        if (json.has("payload")) {
            JSONObject payload = json.getJSONObject("payload");
            boolean ok = sunmi.printPosReceipt(payload.toString());
            return corsOk(ok ? jsonOk(null) : jsonError("Sunmi print failed"));
        }

        // 直接傳 buildBridgePayload 的內容（payload 就是 root）
        if (json.has("shopName") || json.has("items") || json.has("orderNumber")) {
            boolean ok = sunmi.printPosReceipt(json.toString());
            if (json.optBoolean("openDrawer", false)) sunmi.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Sunmi print failed"));
        }

        // 簡單文字列印
        String text = json.optString("text", "");
        if (!text.isEmpty()) {
            float fontSize = (float) json.optDouble("fontSize", 24);
            boolean ok = sunmi.printTextWithFont(text, "", fontSize);
            if (json.optBoolean("cut", true)) sunmi.cutPaper();
            if (json.optBoolean("openDrawer", false)) sunmi.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Sunmi print failed"));
        }

        // base64 raw data
        String rawBase64 = json.optString("rawBase64", "");
        if (!rawBase64.isEmpty()) {
            byte[] data = Base64.decode(rawBase64, Base64.DEFAULT);
            boolean ok = sunmi.sendRawData(data);
            return corsOk(ok ? jsonOk(null) : jsonError("Sunmi raw send failed"));
        }

        // base64 bitmap
        String bitmapBase64 = json.optString("bitmapBase64", "");
        if (!bitmapBase64.isEmpty()) {
            byte[] bytes = Base64.decode(bitmapBase64, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp == null) return corsOk(jsonError("Invalid bitmap"));
            boolean ok = sunmi.printBitmap(bmp);
            return corsOk(ok ? jsonOk(null) : jsonError("Sunmi bitmap print failed"));
        }

        return corsOk(jsonError("No printable content in request body"));
    }

    /**
     * POST /print/bluetooth
     * body: {
     *   "payload": { ... }   // buildBridgePayload 產生的完整 JSON
     *   -- 或 --
     *   "text": "...",
     *   "address": "AA:BB:CC:DD:EE:FF"  // 選填，若已連線則不需要
     * }
     */
    private Response handlePrintBluetooth(JSONObject json) throws Exception {
        // 若指定 address 且未連線，先連線
        String address = json.optString("address", "");
        if (!address.isEmpty() && !bt.isConnected()) {
            boolean connected = bt.connect(address);
            if (!connected) {
                return corsOk(jsonError("Bluetooth connect failed: " + address));
            }
        }

        if (!bt.isConnected()) {
            return corsOk(jsonError("Bluetooth printer not connected"));
        }

        // 新式 payload
        if (json.has("payload")) {
            JSONObject payload = json.getJSONObject("payload");
            String mode = payload.optString("mode", "receipt");
            boolean ok = "kitchen".equals(mode)
                    ? bt.printKitchenReceipt(payload.toString())
                    : bt.printPosReceipt(payload.toString());
            return corsOk(ok ? jsonOk(null) : jsonError("Bluetooth print failed"));
        }

        // buildBridgePayload root
        if (json.has("shopName") || json.has("items") || json.has("orderNumber")) {
            String mode = json.optString("mode", "receipt");
            boolean ok = "kitchen".equals(mode)
                    ? bt.printKitchenReceipt(json.toString())
                    : bt.printPosReceipt(json.toString());
            if (json.optBoolean("openDrawer", false)) bt.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Bluetooth print failed"));
        }

        // 簡單文字
        String text = json.optString("text", "");
        if (!text.isEmpty()) {
            boolean ok = bt.printText(text);
            if (json.optBoolean("cut", true)) bt.cutPaper();
            if (json.optBoolean("openDrawer", false)) bt.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Bluetooth print failed"));
        }

        // raw
        String rawBase64 = json.optString("rawBase64", "");
        if (!rawBase64.isEmpty()) {
            byte[] data = Base64.decode(rawBase64, Base64.DEFAULT);
            boolean ok = bt.sendRawData(data);
            return corsOk(ok ? jsonOk(null) : jsonError("Bluetooth raw send failed"));
        }

        return corsOk(jsonError("No printable content in request body"));
    }

    /**
     * POST /print/network
     * body: {
     *   "payload": { ... }
     *   -- 或 --
     *   "text": "...",
     *   "ip": "192.168.1.100",   // 選填，若已連線則不需要
     *   "port": 9100
     * }
     */
    private Response handlePrintNetwork(JSONObject json) throws Exception {
        // 若指定 ip 且未連線，先連線
        String ip = json.optString("ip", "");
        if (!ip.isEmpty() && !net.isConnected()) {
            int port = json.optInt("port", 9100);
            boolean connected = net.connect(ip, port);
            if (!connected) {
                return corsOk(jsonError("Network connect failed: " + ip + ":" + port));
            }
        }

        if (!net.isConnected()) {
            return corsOk(jsonError("Network printer not connected"));
        }

        // 新式 payload
        if (json.has("payload")) {
            JSONObject payload = json.getJSONObject("payload");
            String mode = payload.optString("mode", "receipt");
            boolean ok = "kitchen".equals(mode)
                    ? net.printKitchenReceipt(payload.toString())
                    : net.printPosReceipt(payload.toString());
            return corsOk(ok ? jsonOk(null) : jsonError("Network print failed"));
        }

        // buildBridgePayload root
        if (json.has("shopName") || json.has("items") || json.has("orderNumber")) {
            String mode = json.optString("mode", "receipt");
            boolean ok = "kitchen".equals(mode)
                    ? net.printKitchenReceipt(json.toString())
                    : net.printPosReceipt(json.toString());
            if (json.optBoolean("openDrawer", false)) net.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Network print failed"));
        }

        // 簡單文字
        String text = json.optString("text", "");
        if (!text.isEmpty()) {
            boolean ok = net.printText(text);
            if (json.optBoolean("cut", true)) net.cutPaper();
            if (json.optBoolean("openDrawer", false)) net.openCashDrawer();
            return corsOk(ok ? jsonOk(null) : jsonError("Network print failed"));
        }

        // raw
        String rawBase64 = json.optString("rawBase64", "");
        if (!rawBase64.isEmpty()) {
            byte[] data = Base64.decode(rawBase64, Base64.DEFAULT);
            boolean ok = net.sendRawData(data);
            return corsOk(ok ? jsonOk(null) : jsonError("Network raw send failed"));
        }

        return corsOk(jsonError("No printable content in request body"));
    }

    /**
     * POST /drawer/open
     * 依優先順序：Sunmi > 藍牙 > 網路
     */
    private Response handleDrawerOpen(JSONObject json) throws Exception {
        boolean ok = false;
        String via = "";

        if (sunmi.isConnected()) {
            ok = sunmi.openCashDrawer();
            via = "sunmi";
        } else if (bt.isConnected()) {
            ok = bt.openCashDrawer();
            via = "bluetooth";
        } else if (net.isConnected()) {
            ok = net.openCashDrawer();
            via = "network";
        } else {
            return corsOk(jsonError("No printer connected for drawer open"));
        }

        if (ok) {
            return corsOk(jsonOk(new JSONObject().put("via", via)));
        } else {
            return corsOk(jsonError("Open drawer failed via " + via));
        }
    }

    // ==================== 工具 ====================

    /** 讀取 POST body */
    private String readBody(IHTTPSession session) {
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String body = files.get("postData");
            return body != null ? body.trim() : "";
        } catch (IOException | ResponseException e) {
            Log.w(TAG, "readBody error", e);
            return "";
        }
    }

    /** 包成 { "ok": true, "data": ... } */
    private String jsonOk(JSONObject data) {
        try {
            JSONObject r = new JSONObject();
            r.put("ok", true);
            if (data != null) r.put("data", data);
            return r.toString();
        } catch (Exception e) {
            return "{\"ok\":true}";
        }
    }

    /** 包成 { "ok": false, "error": "..." } */
    private String jsonError(String msg) {
        try {
            JSONObject r = new JSONObject();
            r.put("ok", false);
            r.put("error", msg != null ? msg : "unknown error");
            return r.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"unknown\"}";
        }
    }

    /** 回傳 JSON Response 並加 CORS header */
    private Response corsOk(String json) {
        return corsOk(json, Response.Status.OK);
    }

    private Response corsOk(String json, Response.Status status) {
        Response resp = newFixedLengthResponse(status, "application/json; charset=utf-8", json);
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        resp.addHeader("Access-Control-Max-Age", "3600");
        return resp;
    }
}
