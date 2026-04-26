package com.pos.sunmiprinter;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.pos.sunmiprinter.printer.SunmiPrinterManager;
import com.pos.sunmiprinter.web.PrintJsBridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MainActivity extends AppCompatActivity {

    private static final String POS_URL = "https://jess0937588151-hue.github.io/2234/";
    private static final String JS_INTERFACE_NAME = "SunmiPrinter";

    private WebView webView;
    private SunmiPrinterManager printerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);

        printerManager = new SunmiPrinterManager(this);
        printerManager.bind();

        setupWebView();
        webView.loadUrl(POS_URL);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);

        webView.addJavascriptInterface(new PrintJsBridge(this, printerManager, webView), JS_INTERFACE_NAME);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAssetScript("inject-bridge.js");
                injectAssetScript("site-autoprint-adapter.js");
            }
        });
    }

    private void injectAssetScript(String fileName) {
        String js = loadAssetText(fileName);
        if (js != null && !js.trim().isEmpty()) {
            webView.evaluateJavascript(js, null);
        }
    }

    private String loadAssetText(String fileName) {
        InputStream inputStream = null;
        BufferedReader reader = null;
        try {
            inputStream = getAssets().open(fileName);
            reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            return null;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) {}
            if (inputStream != null) try { inputStream.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (printerManager != null) printerManager.unbind();
        if (webView != null) {
            webView.removeJavascriptInterface(JS_INTERFACE_NAME);
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
