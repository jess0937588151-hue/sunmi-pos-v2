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
        this.settings = LogManager.getAppSettings();
    }

    @Override
    public Response serve(IHTTPSession session) {
        Method method = session.getMethod();
        String uri = session.getUri();

        if (Method.OPTIONS.equals(method)) {
            return cors(newFixedLengthResponse(Response.Status.OK, "text/plain", ""));
        }

        try {
            if (Method.GET.equals(method) && "/ping".equals(uri)) {
                return handlePing();
            }
            if (Method.GET.equals(method) && "/printer/status".equals(uri)) {
                return handleStatus();
            }
            if (Method.GET.equals(method) && "/test".equals(uri)) {
                return handleTestPage();
            }

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

    private boolean checkToken(IHTTPSession session) {
        if (settings == null) return true;
        String expected = settings.getApiToken();
        if (expected == null || expected.isEmpty()) {
            return true;
        }
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
        data.append("\"token\":\"").append(escape(settings != null ? settings.getApiToken() : "")).append("\",");
        data.append("\"now\":").append(System.currentTimeMillis());
        data.append("}");
        return cors(json(true, data.toString(), null));
    }

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
                + "<button onclick=\"call('/logs?lines=80','GET')\">最近日誌</button>"
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

    private Response handlePrintSunmi(IHTTPSession session) {
        try {
            LogManager.i(TAG, "handlePrintSunmi ENTRY");
            final String body = readBody(session);
            String head = body == null ? "(null)" : (body.length() > 200 ? body.substring(0, 200) : body);
            LogManager.i(TAG, "handlePrintSunmi body len=" + (body == null ? -1 : body.length()) + " head=" + head);
            boolean ok = PrintQueue.submitAndWait("print/sunmi", () -> sunmi.printPosReceipt(body));
            String err = (settings != null) ? settings.getLastPrintError() : "";
            LogManager.i(TAG, "handlePrintSunmi DONE ok=" + ok + " err=" + err);
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

    private Response handlePrintBluetooth(IHTTPSession session) {
        try {
            LogManager.i(TAG, "handlePrintBluetooth ENTRY");
            final String body = readBody(session);
            String head = body == null ? "(null)" : (body.length() > 200 ? body.substring(0, 200) : body);
            LogManager.i(TAG, "handlePrintBluetooth body len=" + (body == null ? -1 : body.length()) + " head=" + head);
            if (bluetooth == null) {
                LogManager.w(TAG, "handlePrintBluetooth: bluetooth manager null");
                return cors(json(false, null, "bluetooth manager not ready"));
            }
            boolean ok = PrintQueue.submitAndWait("print/bluetooth", () -> bluetooth.printPosReceipt(body));
            if (settings != null) settings.recordPrintResult(ok, ok ? "" : "bluetooth print failed");
            LogManager.i(TAG, "handlePrintBluetooth DONE ok=" + ok);
            return cors(json(ok, "{}", ok ? null : "bluetooth print failed"));
        } catch (Exception e) {
            if (settings != null) settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "handlePrintBluetooth failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    private Response handlePrintNetwork(IHTTPSession session) {
        try {
            LogManager.i(TAG, "handlePrintNetwork ENTRY");
            final String body = readBody(session);
            String head = body == null ? "(null)" : (body.length() > 200 ? body.substring(0, 200) : body);
            LogManager.i(TAG, "handlePrintNetwork body len=" + (body == null ? -1 : body.length()) + " head=" + head);
            if (network == null) {
                LogManager.w(TAG, "handlePrintNetwork: network manager null");
                return cors(json(false, null, "network manager not ready"));
            }
            boolean ok = PrintQueue.submitAndWait("print/network", () -> network.printPosReceipt(body));
            if (settings != null) settings.recordPrintResult(ok, ok ? "" : "network print failed");
            LogManager.i(TAG, "handlePrintNetwork DONE ok=" + ok);
            return cors(json(ok, "{}", ok ? null : "network print failed"));
        } catch (Exception e) {
            if (settings != null) settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "handlePrintNetwork failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

    private Response handleDrawerOpen(IHTTPSession session) {
        try {
            LogManager.i(TAG, "handleDrawerOpen ENTRY");
            boolean sNotNull = sunmi != null;
            boolean bNotNull = bluetooth != null;
            boolean nNotNull = network != null;
            boolean sConn = sNotNull && sunmi.isConnected();
            boolean bConn = bNotNull && bluetooth.isConnected();
            boolean nConn = nNotNull && network.isConnected();
            LogManager.i(TAG, "handleDrawerOpen status sunmi(notNull=" + sNotNull + ",conn=" + sConn
                    + ") bt(notNull=" + bNotNull + ",conn=" + bConn
                    + ") net(notNull=" + nNotNull + ",conn=" + nConn + ")");

            final boolean[] result = {false};
            final String[] viaArr = {"none"};
            PrintQueue.submitAndWait("drawer/open", () -> {
                if (sunmi != null && sunmi.isConnected()) {
                    result[0] = sunmi.openCashDrawer();
                    viaArr[0] = "sunmi";
                } else if (bluetooth != null && bluetooth.isConnected()) {
                    result[0] = bluetooth.openCashDrawer();
                    viaArr[0] = "bluetooth";
                } else if (network != null && network.isConnected()) {
                    result[0] = network.openCashDrawer();
                    viaArr[0] = "network";
                }
                return result[0];
            });
            LogManager.i(TAG, "handleDrawerOpen DONE via=" + viaArr[0] + " ok=" + result[0]);
            return cors(json(result[0], "{\"via\":\"" + viaArr[0] + "\"}",
                    result[0] ? null : "no available printer"));
        } catch (Exception e) {
            LogManager.e(TAG, "handleDrawerOpen failed", e);
            return cors(json(false, null, e.getMessage()));
        }
    }

           private String readBody(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> headers = session.getHeaders();
        String ctype = headers.get("content-type");
        if (ctype == null) ctype = headers.get("Content-Type");
        LogManager.i(TAG, "readBody content-type=" + ctype);

        // 從 header 取 Content-Length
        int contentLength = 0;
        String cl = headers.get("content-length");
        if (cl == null) cl = headers.get("Content-Length");
        if (cl != null) {
            try { contentLength = Integer.parseInt(cl.trim()); } catch (Exception ignored) {}
        }
        LogManager.i(TAG, "readBody content-length=" + contentLength);

        // 直接從 InputStream 讀原始 byte，完全不碰 parseBody
        // (NanoHTTPD parseBody 對 application/octet-stream / application/json 走 US-ASCII，會把中文 byte 變成 ?)
        InputStream is = session.getInputStream();
        if (is != null && contentLength > 0) {
            byte[] buf = new byte[contentLength];
            int read = 0;
            while (read < contentLength) {
                int n = is.read(buf, read, contentLength - read);
                if (n <= 0) break;
                read += n;
            }
            LogManager.i(TAG, "readBody InputStream read=" + read + "/" + contentLength);

            // log 前 40 byte 的 hex（驗證 byte 層真的有中文 UTF-8）
            StringBuilder hex = new StringBuilder();
            int hLimit = Math.min(read, 40);
            for (int i = 0; i < hLimit; i++) {
                hex.append(String.format("%02x ", buf[i] & 0xFF));
            }
            LogManager.i(TAG, "readBody bytes(first40 hex)=" + hex);

            String s = new String(buf, 0, read, "UTF-8");
            String head = s.length() > 200 ? s.substring(0, 200) : s;
            LogManager.i(TAG, "readBody UTF8 decoded head=" + head);
            StringBuilder cps = new StringBuilder();
            int limit = Math.min(head.length(), 40);
            for (int i = 0; i < limit; i++) {
                cps.append(Integer.toHexString(head.charAt(i))).append(' ');
            }
            LogManager.i(TAG, "readBody UTF8 codepoints(first40)=" + cps);
            return s;
        }

        // 退路：若 Content-Length 拿不到，才走 parseBody
        LogManager.w(TAG, "readBody fallback to parseBody (no content-length)");
        Map<String, String> files = new HashMap<>();
        try {
            session.parseBody(files);
        } catch (Exception e) {
            LogManager.w(TAG, "readBody parseBody warn: " + e.getMessage());
        }
        String body = files.get("postData");
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
        return "v20260606-debug";
    }

    @SuppressWarnings("unused")
    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
