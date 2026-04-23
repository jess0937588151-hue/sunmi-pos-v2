package com.pos.sunmiprinter;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.support.v7.app.AppCompatActivity;

import woyou.aidlservice.jiuiv5.IWoyouService;
import woyou.aidlservice.jiuiv5.ICallback;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private IWoyouService printerService;
    private static final String TAG = "SunmiPOS";
    private static final String POS_URL = "https://your-pos-website.com";

    private ServiceConnection connPrinter = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            Log.d(TAG, "Printer service connected");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
        }
    };

    private ICallback callback = new ICallback.Stub() {
        @Override public void onRunResult(boolean isSuccess) {}
        @Override public void onReturnString(String result) {}
        @Override public void onRaiseException(int code, String msg) {}
        @Override public void onPrintResult(int code, String msg) {}
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PrinterBridge(), "SunmiPrinter");
        webView.loadUrl(POS_URL);

        bindPrinterService();
    }

    private void bindPrinterService() {
        Intent intent = new Intent();
        intent.setPackage("woyou.aidlservice.jiuiv5");
        intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
        bindService(intent, connPrinter, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unbindService(connPrinter); } catch (Exception e) {}
    }

    public class PrinterBridge {

        @JavascriptInterface
        public boolean isConnected() {
            return printerService != null;
        }

        @JavascriptInterface
        public void printText(String text, float fontSize) {
            if (printerService == null) return;
            try {
                printerService.setAlignment(0, callback);
                printerService.printTextWithFont(text + "\n", "", fontSize, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void printTextCenter(String text, float fontSize) {
            if (printerService == null) return;
            try {
                printerService.setAlignment(1, callback);
                printerService.printTextWithFont(text + "\n", "", fontSize, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void printLine() {
            if (printerService == null) return;
            try {
                printerService.setAlignment(0, callback);
                printerService.printTextWithFont("--------------------------------\n", "", 24, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void printRow(String left, String right) {
            if (printerService == null) return;
            try {
                printerService.printColumnsString(
                    new String[]{left, right},
                    new int[]{1, 1},
                    new int[]{0, 2}, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void printThreeColumns(String left, String center, String right) {
            if (printerService == null) return;
            try {
                printerService.printColumnsString(
                    new String[]{left, center, right},
                    new int[]{2, 1, 1},
                    new int[]{0, 1, 2}, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void feedAndCut() {
            if (printerService == null) return;
            try {
                printerService.lineWrap(4, callback);
                printerService.cutPaper(callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void openCashDrawer() {
            if (printerService == null) return;
            try {
                printerService.sendRAWData(
                    new byte[]{0x1B, 0x70, 0x00, 0x19, (byte)0xFA}, callback);
            } catch (RemoteException e) { Log.e(TAG, "err", e); }
        }

        @JavascriptInterface
        public void printReceipt(String jsonData) {
            if (printerService == null) return;
            try {
                org.json.JSONObject data = new org.json.JSONObject(jsonData);

                printerService.setAlignment(1, callback);
                printerService.printTextWithFont(
                    data.optString("shopName", "") + "\n", "", 32, callback);

                if (data.has("subtitle"))
                    printerService.printTextWithFont(
                        data.getString("subtitle") + "\n", "", 24, callback);

                printerService.setAlignment(0, callback);
                printerService.printTextWithFont(
                    "--------------------------------\n", "", 24, callback);

                if (data.has("orderNumber"))
                    printerService.printTextWithFont(
                        "單號: " + data.getString("orderNumber") + "\n", "", 24, callback);
                if (data.has("dateTime"))
                    printerService.printTextWithFont(
                        "時間: " + data.getString("dateTime") + "\n", "", 24, callback);
                if (data.has("orderType"))
                    printerService.printTextWithFont(
                        "類型: " + data.getString("orderType") + "\n", "", 24, callback);

                printerService.printTextWithFont(
                    "--------------------------------\n", "", 24, callback);

                if (data.has("items")) {
                    org.json.JSONArray items = data.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        org.json.JSONObject item = items.getJSONObject(i);
                        printerService.printColumnsString(
                            new String[]{
                                item.optString("name", ""),
                                "x" + item.optInt("qty", 1),
                                "$" + item.optInt("price", 0)
                            },
                            new int[]{2, 1, 1},
                            new int[]{0, 1, 2}, callback);

                        if (item.has("options") && item.getString("options").length() > 0)
                            printerService.printTextWithFont(
                                "  " + item.getString("options") + "\n", "", 20, callback);
                        if (item.has("note") && item.getString("note").length() > 0)
                            printerService.printTextWithFont(
                                "  *" + item.getString("note") + "\n", "", 20, callback);
                    }
                }

                printerService.printTextWithFont(
                    "--------------------------------\n", "", 24, callback);

                if (data.has("total")) {
                    printerService.setAlignment(2, callback);
                    printerService.printTextWithFont(
                        "合計: $" + data.getString("total") + "\n", "", 32, callback);
                }

                if (data.has("paymentMethod")) {
                    printerService.setAlignment(0, callback);
                    printerService.printTextWithFont(
                        "付款: " + data.getString("paymentMethod") + "\n", "", 24, callback);
                }

                printerService.setAlignment(1, callback);
                printerService.printTextWithFont("\n謝謝光臨\n", "", 24, callback);

                printerService.lineWrap(4, callback);
                printerService.cutPaper(callback);

            } catch (Exception e) { Log.e(TAG, "printReceipt err", e); }
        }
    }
}
