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
    private static final String POS_URL = "https://jess0937588151-hue.github.io/2234/";

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
    boolean result = bindService(intent, connPrinter, Context.BIND_AUTO_CREATE);
    Log.d(TAG, "bindService result=" + result);
    if (!result) {
        // Try alternative binding
        Intent intent2 = new Intent();
        intent2.setComponent(new android.content.ComponentName(
            "woyou.aidlservice.jiuiv5",
            "woyou.aidlservice.jiuiv5.MainService"
        ));
        boolean result2 = bindService(intent2, connPrinter, Context.BIND_AUTO_CREATE);
        Log.d(TAG, "bindService attempt2 result=" + result2);
    }
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
        public String debugInfo() {
            return "printerService=" + (printerService != null ? "OK" : "NULL");
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
public void printReceipt(final String jsonStr) {
    Log.d(TAG, "printReceipt called, printerService=" + (printerService != null));
    if (printerService == null) return;
    try {
        org.json.JSONObject json = new org.json.JSONObject(jsonStr);
        
        printerService.printerInit(callback);
        
        String shopName = json.optString("shopName", "POS");
        printerService.setAlignment(1, callback);
        printerService.printTextWithFont(shopName + "\n", "", 28, callback);
        
        String subtitle = json.optString("subtitle", "");
        if (!subtitle.isEmpty()) {
            printerService.printTextWithFont(subtitle + "\n", "", 24, callback);
        }
        
        printerService.printTextWithFont("--------------------------------\n", "", 20, callback);
        
        String orderNumber = json.optString("orderNumber", "");
        if (!orderNumber.isEmpty()) {
            printerService.setAlignment(0, callback);
            printerService.printTextWithFont("單號：" + orderNumber + "\n", "", 22, callback);
        }
        String dateTime = json.optString("dateTime", "");
        if (!dateTime.isEmpty()) {
            printerService.printTextWithFont("時間：" + dateTime + "\n", "", 22, callback);
        }
        String orderType = json.optString("orderType", "");
        if (!orderType.isEmpty()) {
            printerService.printTextWithFont("類型：" + orderType + "\n", "", 22, callback);
        }
        String paymentMethod = json.optString("paymentMethod", "");
        if (!paymentMethod.isEmpty()) {
            printerService.printTextWithFont("付款：" + paymentMethod + "\n", "", 22, callback);
        }
        
        printerService.printTextWithFont("--------------------------------\n", "", 20, callback);
        
        org.json.JSONArray items = json.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                org.json.JSONObject item = items.getJSONObject(i);
                String name = item.optString("name", "");
                int qty = item.optInt("qty", 0);
                int price = item.optInt("price", 0);
                printerService.printTextWithFont(name + " x" + qty + "  $" + price + "\n", "", 22, callback);
                String options = item.optString("options", "");
                if (!options.isEmpty()) {
                    printerService.printTextWithFont("  " + options + "\n", "", 18, callback);
                }
            }
        }
        
        printerService.printTextWithFont("--------------------------------\n", "", 20, callback);
        
        String total = json.optString("total", "0");
        printerService.setAlignment(1, callback);
        printerService.printTextWithFont("合計：$" + total + "\n", "", 28, callback);
        
        printerService.lineWrap(4, callback);
        printerService.cutPaper(callback);
        
    } catch (Exception e) {
        Log.e(TAG, "printReceipt error: " + e.getMessage());
    
}

        }
    }
}
