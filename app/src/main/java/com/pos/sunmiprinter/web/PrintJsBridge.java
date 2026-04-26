package com.pos.sunmiprinter.web;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.widget.Toast;

import com.pos.sunmiprinter.printer.SunmiPrinterManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class PrintJsBridge {

    private final Context context;
    private final SunmiPrinterManager printerManager;
    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PrintJsBridge(Context context, SunmiPrinterManager printerManager, WebView webView) {
        this.context = context;
        this.printerManager = printerManager;
        this.webView = webView;
    }

    @JavascriptInterface
    public boolean isPrinterReady() {
        return printerManager.isConnected();
    }

    @JavascriptInterface
    public int getPrinterStatus() {
        return printerManager.getPrinterStatus();
    }

    @JavascriptInterface
    public void printText(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean ok = printerManager.printText(text);
                toast(ok ? "已送出列印" : "印表機尚未連線");
            }
        });
    }

    @JavascriptInterface
    public void printReceipt(final String title, final String body) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean ok = printerManager.printReceipt(title, body);
                toast(ok ? "已送出收據列印" : "印表機尚未連線");
            }
        });
    }

    @JavascriptInterface
    public void printHtml(final String title, final String html) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String plain;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    plain = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
                } else {
                    plain = Html.fromHtml(html).toString();
                }
                boolean ok = printerManager.printReceipt(title, plain);
                toast(ok ? "已送出 HTML 列印" : "印表機尚未連線");
            }
        });
    }

    /* === POS 專用：接收 JSON 格式收據 === */
    @JavascriptInterface
    public void printPosReceipt(final String jsonStr) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean ok = printerManager.printPosReceipt(jsonStr);
                toast(ok ? "已送出 POS 收據列印" : "印表機尚未連線");
            }
        });
    }

    @JavascriptInterface
    public void printReceiptJson(final String json) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject object = new JSONObject(json);
                    String title = object.optString("title", "Receipt");
                    StringBuilder body = new StringBuilder();
                    body.append(object.optString("subtitle", ""));
                    if (body.length() > 0) body.append("\n");
                    JSONArray lines = object.optJSONArray("lines");
                    if (lines != null) {
                        for (int i = 0; i < lines.length(); i++) {
                            body.append(lines.optString(i)).append("\n");
                        }
                    }
                    String footer = object.optString("footer", "");
                    if (footer != null && footer.length() > 0) body.append(footer).append("\n");
                    boolean ok = printerManager.printReceipt(title, body.toString());
                    toast(ok ? "已送出 JSON 收據列印" : "印表機尚未連線");
                } catch (Exception e) {
                    toast("JSON 列印格式錯誤");
                }
            }
        });
    }

    @JavascriptInterface
    public void printCurrentPage() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                webView.evaluateJavascript(
                    "(function(){var title=document.title||'Web Print';var body=(document.body&&document.body.innerText)||'';return JSON.stringify({title:title,body:body});})()",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            try {
                                if (value == null) { toast("無法讀取頁面內容"); return; }
                                String unwrapped = value;
                                if (unwrapped.startsWith("\"") && unwrapped.endsWith("\"")) {
                                    unwrapped = unwrapped.substring(1, unwrapped.length() - 1)
                                            .replace("\\\\", "\\")
                                            .replace("\\\"", "\"")
                                            .replace("\\n", "\n");
                                }
                                JSONObject object = new JSONObject(unwrapped);
                                boolean ok = printerManager.printReceipt(
                                    object.optString("title", "Web Print"),
                                    object.optString("body", ""));
                                toast(ok ? "已送出目前頁面列印" : "印表機尚未連線");
                            } catch (Exception e) {
                                toast("頁面列印解析失敗");
                            }
                        }
                    });
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
