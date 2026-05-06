package com.pos.sunmiprinter;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本機 HTTP Server（NanoHTTPD），綁定 127.0.0.1
 * v20260603:
 *   - /ping 增加 paperOut/coverOpen/overheat/lastPrintAt/lastPrintOk
 *   - 新增 GET /logs?date=YYYY-MM-DD&lines=200 (需 X-API-Token)
 *   - 新增 GET /test → 內建測試列印頁
 *   - /print/* 與 /drawer/open 與 /logs 需要 X-API-Token (token 為空時不檢查)
 *   - 所有錯誤透過 LogManager.e 紀錄
 */
public class PrintHttpServer extends NanoHTTPD {

    private static final String TAG = "PrintHttpServer";

    private final AppSettings settings;
    private final SunmiPrinterManager sunmi;
    private final BluetoothPrinterManager bluetooth;
    private final NetworkPrinterManager network;

    public PrintHttpServer(int port,
                           SunmiPrinterManager sunmi,
                           BluetoothPrinterManager bluetooth,
                           NetworkPrinterManager network) {
        super("127.0.0.1", port);
        this.sunmi = sunmi;
        this.bluetooth = bluetooth;
        this.network = network;
        // AppSettings 需要 Context；改用靜態無 Context 版本：直接讀 LogManager 中保留的 context
        // 為了相容舊建構子，這裡延後在需要時取得 settings
        this.settings = LogManager.getAppSettings();
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        // CORS preflight
        if (Method.OPTIONS.equals(method)) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
        }

        try {
            // ----- 不需 token 的端點 -----
            if (Method.GET.equals(method) && "/ping".equals(uri)) {
                return handlePing();
            }
            if (Method.GET.equals(method) && "/printer/status".equals(uri)) {
                return handleStatus();
            }
            if (Method.GET.equals(method) && "/test".equals(uri)) {
                return handleTestPage();
            }

            // ----- 以下端點需驗證 token -----
            if (!checkToken(session)) {
                LogManager.w(TAG, "unauthorized: " + method + " " + uri);
                return cors(json(false, null, "unauthorized"));
            }

            if (Method.GET.equals(method) && "/logs".equals(uri)) {
                return handleLogs(session);
            }
            if (Method.POST.equals(method) && "/print/sunmi".equals(uri)) {
                return handlePrintSunmi(session);
            }
            if (Method.POST.equals(method) && "/print/bluetooth".equals(uri)) {
                return handlePrintBluetooth(session);
            }
            if (Method.POST.equals(method) && "/print/network".equals(uri)) {
                return handlePrintNetwork(session);
            }
            if (Method.POST.equals(method) && "/drawer/open".equals(uri)) {
                return handleDrawerOpen(session);
            }

            return cors(json(false, null, "not found: " + uri));
        } catch (Throwable t) {
            LogManager.e(TAG, "serve error: " + uri, t);
            return cors(json(false, null, "server error: " + t.getMessage()));
        }
    }

    // ===== Token check =====
    private boolean checkToken(IHTTPSession session) {
        if (settings == null) return true; // 沒 settings 就先放行
        String expected = settings.getApiToken();
        if (expected == null || expected.isEmpty()) {
            return true; // 向後相容：未設定 token 時不檢查
        }
        Map<String, String> headers = session.getHeaders();
        String got = headers.get("x-api-token");
        if (got == null) got = headers.get("X-API-Token");
        if (got != null && expected.equals(got)) return true;

        // 也允許 query string token=xxx
        Map<String, List<String>> params = session.getParameters();
        if (params != null) {
            List<String> qs = params.get("token");
            if (qs != null && !qs.isEmpty() && expected.equals(qs.get(0))) return true;
        }
        return false;
    }

    // ===== /ping =====
    private Response handlePing() {
        StringBuilder data = new StringBuilder();
        data.append("{");
        data.append("\"version\":\"").append(getApkVersion()).append("\",");
        int port = settings != null ? settings.getHttpPort() : 8080;
        data.append("\"server\":\"127.0.0.1:").append(port).append("\",");

        SunmiPrinterManager.PrinterStatusInfo info = sunmi != null
                ? sunmi.getPrinterStatusInfo()
                : new SunmiPrinterManager.PrinterStatusInfo();
        data.append("\"sunmiConnected\":").append(info.connected).append(",");
        data.append("\"paperOut\":").append(info.paperOut).append(",");
        data.append("\"coverOpen\":").append(info.coverOpen).append(",");
        data.append("\"overheat\":").append(info.overheat).append(",");
        data.append("\"cutterError\":").append(info.cutterError).append(",");
        data.append("\"sunmiRaw\":").append(info.raw).append(",");

        boolean btConn = bluetooth != null && bluetooth.isConnected();
        boolean netConn = network != null && network.isConnected();
        data.append("\"bluetoothConnected\":").append(btConn).append(",");
        data.append("\"networkConnected\":").append(netConn).append(",");

        long lastAt = settings != null ? settings.getLastPrintAt() : 0L;
        boolean lastOk = settings == null || settings.getLastPrintOk();
        String lastErr = settings != null ? settings.getLastPrintError() : "";
        data.append("\"lastPrintAt\":").append(lastAt).append(",");
        data.append("\"lastPrintOk\":").append(lastOk).append(",");
        data.append("\"lastPrintError\":\"").append(escape(lastErr)).append("\",");
        data.append("\"now\":").append(System.currentTimeMillis());
        data.append("}");
        return cors(json(true, data.toString(), null));
    }

    // ===== /printer/status =====
    private Response handleStatus() {
        StringBuilder data = new StringBuilder();
        data.append("{");
        SunmiPrinterManager.PrinterStatusInfo info = sunmi != null
                ? sunmi.getPrinterStatusInfo()
                : new SunmiPrinterManager.PrinterStatusInfo();
        data.append("\"sunmi\":").append(info.toJson()).append(",");
        String btAddr = settings != null ? settings.getBtAddress() : "";
        String netIp = settings != null ? settings.getNetIp() : "";
        int netPort = settings != null ? settings.getNetPort() : 9100;
        data.append("\"bluetooth\":{\"connected\":")
                .append(bluetooth != null && bluetooth.isConnected())
                .append(",\"address\":\"").append(escape(btAddr)).append("\"},");
        data.append("\"network\":{\"connected\":")
                .append(network != null && network.isConnected())
                .append(",\"ip\":\"").append(escape(netIp))
                .append("\",\"port\":").append(netPort).append("}");
        data.append("}");
        return cors(json(true, data.toString(), null));
    }

    // ===== /logs =====
    private Response handleLogs(IHTTPSession session) {
        Map<String, List<String>> params = session.getParameters();
        String date = "";
        int lines = 200;
        if (params != null) {
            if (params.get("date") != null && !params.get("date").isEmpty()) {
                date = params.get("date").get(0);
            }
            if (params.get("lines") != null && !params.get("lines").isEmpty()) {
                try { lines = Integer.parseInt(params.get("lines").get(0)); } catch (Exception ignored) {}
            }
        }
        List<String> rows;
        if (date == null || date.isEmpty()) {
            rows = LogManager.getRecent(lines);
        } else {
            rows = LogManager.readFile(date, lines);
        }
        StringBuilder data = new StringBuilder();
        data.append("{");
        data.append("\"date\":\"").append(escape(date)).append("\",");
        data.append("\"count\":").append(rows.size()).append(",");
        data.append("\"logDir\":\"").append(escape(LogManager.getLogDirPath())).append("\",");
        data.append("\"lines\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) data.append(",");
            data.append("\"").append(escape(rows.get(i))).append("\"");
        }
        data.append("]}");
        return cors(json(true, data.toString(), null));
    }

    // ===== /test =====
    private Response handleTestPage() {
        String token = settings != null ? settings.getApiToken() : "";
        String html = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<title>POS 列印橋接測試</title>"
                + "<style>body{font-family:sans-serif;padding:20px;font-size:16px}"
                + "button{padding:12px 18px;margin:6px;font-size:16px}"
                + "pre{background:#f4f4f4;padding:10px;white-space:pre-wrap;word-break:break-all}</style>"
                + "</head><body>"
                + "<h2>POS 列印橋接測試</h2>"
                + "<p>APK 版本: <b>" + getApkVersion() + "</b></p>"
                + "<p>API Token: <code>" + escape(token) + "</code></p>"
                + "<button onclick=\"call('/ping','GET')\">Ping</button>"
                + "<button onclick=\"call('/printer/status','GET')\">印表機狀態</button>"
                + "<button onclick=\"testPrint()\">測試列印</button>"
                + "<button onclick=\"openDrawer()\">開錢箱</button>"
                + "<button onclick=\"call('/logs?lines=50','GET')\">最近日誌</button>"
                + "<pre id='out'>(尚未呼叫)</pre>"
                + "<script>"
                + "var TOKEN='" + escape(token) + "';"
                + "function show(o){document.getElementById('out').textContent=typeof o==='string'?o:JSON.stringify(o,null,2);}"
                + "function call(p,m,b){var x=new XMLHttpRequest();x.open(m,p);"
                + "x.setRequestHeader('X-API-Token',TOKEN);"
                + "if(b)x.setRequestHeader('Content-Type','application/json');"
                + "x.onload=function(){show(x.responseText);};"
                + "x.onerror=function(){show('連線失敗');};"
                + "x.send(b||null);}"
                + "function testPrint(){call('/print/sunmi','POST',JSON.stringify({shopName:'測試店',orderNumber:'T001',dateTime:new Date().toLocaleString(),items:[{name:'測試商品',qty:1,price:100}],total:'100'}));}"
                + "function openDrawer(){call('/drawer/open','POST','{}');}"
                + "</script></body></html>";
        Response r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html);
        return cors(r);
    }

    // ===== /print/sunmi =====
    private Response handlePrintSunmi(IHTTPSession session) {
        try {
            String body = readBody(session);
            boolean ok = sunmi.printPosReceipt(body);
            String err = (settings != null) ? settings.getLastPrintError() : "";
            if (ok) {
                return cors(json(true, "{}", null));
            } else {
                return cors(json(false, null, err.isEmpty() ? "print failed" : err));
            }
        } catch (Exception e) {
            LogManager.e(TAG, "handlePrintSunmi failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    // ===== /print/bluetooth =====
    private Response handlePrintBluetooth(IHTTPSession session) {
        try {
            String body = readBody(session);
            if (bluetooth == null) {
                return cors(json(false, null, "bluetooth manager not ready"));
            }
            boolean ok = bluetooth.printPosReceipt(body);
            if (settings != null) settings.recordPrintResult(ok, ok ? "" : "bluetooth print failed");
            return cors(json(ok, "{}", ok ? null : "bluetooth print failed"));
        } catch (Exception e) {
            if (settings != null) settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "handlePrintBluetooth failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    // ===== /print/network =====
    private Response handlePrintNetwork(IHTTPSession session) {
        try {
            String body = readBody(session);
            if (network == null) {
                return cors(json(false, null, "network manager not ready"));
            }
            boolean ok = network.printPosReceipt(body);
            if (settings != null) settings.recordPrintResult(ok, ok ? "" : "network print failed");
            return cors(json(ok, "{}", ok ? null : "network print failed"));
        } catch (Exception e) {
            if (settings != null) settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "handlePrintNetwork failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    // ===== /drawer/open =====
    private Response handleDrawerOpen(IHTTPSession session) {
        try {
            boolean ok = false;
            String via = "none";
            if (sunmi != null && sunmi.isConnected()) {
                ok = sunmi.openCashDrawer();
                via = "sunmi";
            } else if (bluetooth != null && bluetooth.isConnected()) {
                ok = bluetooth.openCashDrawer();
                via = "bluetooth";
            } else if (network != null && network.isConnected()) {
                ok = network.openCashDrawer();
                via = "network";
            }
            LogManager.i(TAG, "drawer open via=" + via + " ok=" + ok);
            return cors(json(ok, "{\"via\":\"" + via + "\"}", ok ? null : "no available printer"));
        } catch (Exception e) {
            LogManager.e(TAG, "handleDrawerOpen failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    // ===== utils =====

    private String readBody(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");
        if (body == null) {
            InputStream is = session.getInputStream();
            if (is != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
                body = out.toString("UTF-8");
            }
        }
        return body == null ? "" : body;
    }

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
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", sb.toString());
    }

    private Response cors(Response r) {
        r.addHeader("Access-Control-Allow-Origin", "*");
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Token");
        r.addHeader("Access-Control-Max-Age", "86400");
        return r;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String getApkVersion() {
        return "v20260603";
    }

    @SuppressWarnings("unused")
    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
