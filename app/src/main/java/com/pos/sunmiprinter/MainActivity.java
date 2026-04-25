package com.pos.sunmiprinter;
;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SunmiPrinter";
    private static final String POS_URL = "https://jess0937588151-hue.github.io/2234/";
    private WebView webView;
    private String serialPort = null;

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

        detectSerialPort();
    }

    private void detectSerialPort() {
        String[] candidates = {"/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3"};
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.canWrite()) {
                serialPort = path;
                Log.d(TAG, "Serial port found: " + path);
                return;
            }
        }
        Log.w(TAG, "No writable serial port found");
    }

    private byte[] toGB18030(String text) {
        try {
            return text.getBytes("GB18030");
        } catch (UnsupportedEncodingException e) {
            return text.getBytes();
        }
    }

    class PrinterBridge {

        @JavascriptInterface
        public boolean isConnected() {
            boolean ok = (serialPort != null);
            Log.d(TAG, "isConnected=" + ok + " serialPort=" + serialPort);
            return ok;
        }

        @JavascriptInterface
        public void printReceipt(final String jsonStr) {
            Log.d(TAG, "printReceipt called, serialPort=" + serialPort);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    OutputStream os = null;
                    try {
                        org.json.JSONObject json = new org.json.JSONObject(jsonStr);

                        if (serialPort == null) {
                            Log.e(TAG, "No serial port available");
                            return;
                        }

                        os = new FileOutputStream(serialPort);

                        // ESC @ - Initialize printer
                        os.write(new byte[]{0x1B, 0x40});

                        // Center alignment
                        os.write(new byte[]{0x1B, 0x61, 0x01});

                        // Shop name (double size)
                        os.write(new byte[]{0x1D, 0x21, 0x11});
                        os.write(toGB18030(json.optString("shopName", "POS") + "\n"));
                        os.write(new byte[]{0x1D, 0x21, 0x00});

                        String subtitle = json.optString("subtitle", "");
                        if (!subtitle.isEmpty()) {
                            os.write(toGB18030(subtitle + "\n"));
                        }

                        os.write(toGB18030("--------------------------------\n"));

                        // Left alignment
                        os.write(new byte[]{0x1B, 0x61, 0x00});

                        String orderNumber = json.optString("orderNumber", "");
                        if (!orderNumber.isEmpty()) os.write(toGB18030("單號：" + orderNumber + "\n"));
                        String dateTime = json.optString("dateTime", "");
                        if (!dateTime.isEmpty()) os.write(toGB18030("時間：" + dateTime + "\n"));
                        String orderType = json.optString("orderType", "");
                        if (!orderType.isEmpty()) os.write(toGB18030("類型：" + orderType + "\n"));
                        String paymentMethod = json.optString("paymentMethod", "");
                        if (!paymentMethod.isEmpty()) os.write(toGB18030("付款：" + paymentMethod + "\n"));

                        os.write(toGB18030("--------------------------------\n"));

                        // Items
                        org.json.JSONArray items = json.optJSONArray("items");
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                org.json.JSONObject item = items.getJSONObject(i);
                                String name = item.optString("name", "");
                                int qty = item.optInt("qty", 0);
                                int price = item.optInt("price", 0);
                                os.write(toGB18030(name + " x" + qty + "  $" + price + "\n"));
                                String options = item.optString("options", "");
                                if (!options.isEmpty()) {
                                    os.write(toGB18030("  " + options + "\n"));
                                }
                            }
                        }

                        os.write(toGB18030("--------------------------------\n"));

                        // Center - Total (double size)
                        os.write(new byte[]{0x1B, 0x61, 0x01});
                        os.write(new byte[]{0x1D, 0x21, 0x11});
                        os.write(toGB18030("合計：$" + json.optString("total", "0") + "\n"));
                        os.write(new byte[]{0x1D, 0x21, 0x00});

                        // Feed and cut
                        os.write(new byte[]{0x1B, 0x64, 0x04});
                        os.write(new byte[]{0x1D, 0x56, 0x01});

                        // Open cash drawer
                        os.write(new byte[]{0x10, 0x14, 0x01, 0x00, 0x05});

                        os.flush();
                        Log.d(TAG, "Print completed via serial port");

                    } catch (Exception e) {
                        Log.e(TAG, "printReceipt error: " + e.getMessage());
                    } finally {
                        if (os != null) {
                            try { os.close(); } catch (Exception ignored) {}
                        }
                    }
                }
            }).start();
        }
    }
}
