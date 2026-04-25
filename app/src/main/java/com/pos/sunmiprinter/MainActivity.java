package com.pos.sunmiprinter;

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
import androidx.appcompat.app.AppCompatActivity;
import woyou.aidlservice.jiuiv5.IWoyouService;
import woyou.aidlservice.jiuiv5.ICallback;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SunmiPrinter";
    private static final String POS_URL = "https://jess0937588151-hue.github.io/2234/";
    private WebView webView;
    private IWoyouService printerService;

    private ICallback callback = new ICallback.Stub() {
        @Override
        public void onRunResult(boolean isSuccess) throws RemoteException {}
        @Override
        public void onReturnString(String result) throws RemoteException {}
        @Override
        public void onRaiseException(int code, String msg) throws RemoteException {}
    };

    private ServiceConnection connPrinter = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            printerService = IWoyouService.Stub.asInterface(service);
            Log.d(TAG, "Printer service connected!");
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            printerService = null;
            Log.d(TAG, "Printer service disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PrinterBridge(), "Android");
        webView.loadUrl(POS_URL);

        bindPrinterService();
    }

    private void bindPrinterService() {
    Intent intent = new Intent();
    intent.setPackage("woyou.aidlservice.jiuiv5");
    intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
    boolean r1 = bindService(intent, connPrinter, Context.BIND_AUTO_CREATE);
    Log.d(TAG, "bind attempt1 result=" + r1);

    final boolean result = r1;
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
            webView.loadUrl("javascript:alert('bindService result=" + result + "')");
        }
    });
}



    class PrinterBridge {

        @JavascriptInterface
        public boolean isConnected() {
            boolean ok = (printerService != null);
            Log.d(TAG, "isConnected=" + ok);
            return ok;
        }

        @JavascriptInterface
        public void printReceipt(final String jsonStr) {
            Log.d(TAG, "printReceipt called, service=" + (printerService != null));
            if (printerService == null) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(jsonStr);
                        printerService.printerInit(callback);

                        printerService.setAlignment(1, callback);
                        printerService.printTextWithFont(json.optString("shopName", "POS") + "\n", "", 28, callback);

                        String subtitle = json.optString("subtitle", "");
                        if (!subtitle.isEmpty()) printerService.printTextWithFont(subtitle + "\n", "", 24, callback);

                        printerService.printTextWithFont("--------------------------------\n", "", 20, callback);
                        printerService.setAlignment(0, callback);

                        String orderNumber = json.optString("orderNumber", "");
                        if (!orderNumber.isEmpty()) printerService.printTextWithFont("單號：" + orderNumber + "\n", "", 22, callback);
                        String dateTime = json.optString("dateTime", "");
                        if (!dateTime.isEmpty()) printerService.printTextWithFont("時間：" + dateTime + "\n", "", 22, callback);
                        String orderType = json.optString("orderType", "");
                        if (!orderType.isEmpty()) printerService.printTextWithFont("類型：" + orderType + "\n", "", 22, callback);
                        String paymentMethod = json.optString("paymentMethod", "");
                        if (!paymentMethod.isEmpty()) printerService.printTextWithFont("付款：" + paymentMethod + "\n", "", 22, callback);

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
                                if (!options.isEmpty()) printerService.printTextWithFont("  " + options + "\n", "", 18, callback);
                            }
                        }

                        printerService.printTextWithFont("--------------------------------\n", "", 20, callback);
                        printerService.setAlignment(1, callback);
                        printerService.printTextWithFont("合計：$" + json.optString("total", "0") + "\n", "", 28, callback);
                        printerService.lineWrap(4, callback);
                        printerService.cutPaper(callback);

                    } catch (Exception e) {
                        Log.e(TAG, "printReceipt error: " + e.getMessage());
                    }
                }
            }).start();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unbindService(connPrinter); } catch (Exception ignored) {}
    }
}
