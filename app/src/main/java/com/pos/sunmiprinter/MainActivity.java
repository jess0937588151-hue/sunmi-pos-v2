package com.pos.sunmiprinter;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
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

        // 初始化内建印表机
        sunmiPrinter = new SunmiPrinterManager(this);
        sunmiPrinter.bind();

        // 初始化 WebView
        setupWebView();

        // 设定按钮
        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivityForResult(intent, REQUEST_SETTINGS);
        });

        // 重新载入按钮
        ImageButton btnReload = findViewById(R.id.btn_reload);
        btnReload.setOnClickListener(v -> {
            webView.clearCache(true);
            webView.loadUrl(settings.getUrl());
            Toast.makeText(this, "重新载入", Toast.LENGTH_SHORT).show();
        });

        // 载入网址
        webView.loadUrl(settings.getUrl());

        // 自动连线蓝牙
        autoConnectBluetooth();

        // 自动连线网路印表机
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

        // JS Bridge
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
        // 注入 inject-bridge.js
        try {
            java.io.InputStream is = getAssets().open("inject-bridge.js");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            String js = new String(buf, "UTF-8");
            webView.evaluateJavascript(js
