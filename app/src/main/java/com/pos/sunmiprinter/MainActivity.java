package com.pos.sunmiprinter;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pos.sunmiprinter.printer.SunmiPrinterManager;
import com.pos.sunmiprinter.web.PrintJsBridge;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_SETTINGS = 1001;

    private WebView webView;
    private SunmiPrinterManager sunmiPrinter;
    private AppSettings settings;
    private PrintJsBridge jsBridge;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());
        settings = new AppSettings(this);

        sunmiPrinter = new SunmiPrinterManager(this);
        sunmiPrinter.bind();

        setupWebView();

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivityForResult(intent, REQUEST_SETTINGS);
        });

        ImageButton btnReload = findViewById(R.id.btn_reload);
        btnReload.setOnClickListener(v -> {
            webView.clearCache(true);
            webView.loadUrl(settings.getUrl());
            Toast.makeText(this, "重新载入", Toast.LENGTH_SHORT).show();
        });

        webView.loadUrl(settings.getUrl());

        autoConnectBluetooth();
        autoConnectNetwork();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView = findViewById(R.id.webview);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setBuiltInZoomControls(false);
        ws.setSupportZoom(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        jsBridge = new PrintJsBridge(this, webView, sunmiPrinter);
        webView.addJavascriptInterface(jsBridge, "SunmiPrinter");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectBridge();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());
    }

    private void injectBridge() {
        loadAssetScript("inject-bridge.js");
        loadAssetScript("site-autoprint-adapter.js");
        injectSettings();
    }

    private void loadAssetScript(String filename) {
        try {
            java.io.InputStream is = getAssets().open(filename);
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String js = new String(buf, "UTF-8");
            webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            Log.w(TAG, filename + " not found, skipping");
        }
    }

    private void injectSettings() {
        String json = settings.toJson();
        String js = "javascript:try{window.__POS_SETTINGS__=" + json + ";}catch(e){}";
        webView.evaluateJavascript(js, null);
    }

    private void autoConnectBluetooth() {
        if (settings.isBtEnabled() && settings.isBtAutoConnect()) {
            String addr = settings.getBtAddress();
            if (!addr.isEmpty()) {
                new Thread(() -> {
                    boolean ok = jsBridge.connectBtPrinterInternal(addr);
                    handler.post(() -> Log.d(TAG, "BT auto-connect: " + (ok ? "success" : "failed")));
                }).start();
            }
        }
    }

    private void autoConnectNetwork() {
        if (settings.isNetEnabled() && settings.isNetAutoConnect()) {
            String ip = settings.getNetIp();
            int port = settings.getNetPort();
            if (!ip.isEmpty()) {
                new Thread(() -> {
                    boolean ok = jsBridge.connectNetPrinterInternal(ip, port);
                    handler.post(() -> Log.d(TAG, "NET auto-connect: " + (ok ? "success" : "failed")));
                }).start();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SETTINGS && resultCode == RESULT_OK) {
            String newUrl = settings.getUrl();
            webView.loadUrl(newUrl);
            injectSettings();

            if (data != null && data.getBooleanExtra("bt_changed", false)) {
                jsBridge.disconnectBtPrinter();
                autoConnectBluetooth();
            }

            if (data != null && data.getBooleanExtra("net_changed", false)) {
                jsBridge.disconnectNetPrinter();
                autoConnectNetwork();
            }

            Toast.makeText(this, "设定已更新", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sunmiPrinter != null) sunmiPrinter.unbind();
        if (webView != null) {
            webView.removeJavascriptInterface("SunmiPrinter");
            webView.destroy();
        }
    }
}
