package com.pos.sunmiprinter;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity — 服務狀態頁
 * 顯示 HTTP Server 是否運行、本機 IP、埠號
 * 提供啟動/停止服務 與 進入設定頁 按鈕
 * WebView 已完全移除
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int COLOR_TEXT    = Color.parseColor("#212121");
    private static final int COLOR_HINT    = Color.parseColor("#757575");
    private static final int COLOR_SECTION = Color.parseColor("#1976D2");
    private static final int COLOR_GREEN   = Color.parseColor("#4CAF50");
    private static final int COLOR_RED     = Color.parseColor("#F44336");
    private static final int COLOR_BG      = Color.WHITE;

    // ── UI ──
    private TextView tvServiceStatus;
    private TextView tvIpPort;
    private TextView tvSunmiStatus;
    private TextView tvBtStatus;
    private TextView tvNetStatus;
    private Button   btnToggleService;

    // ── Service 綁定 ──
    private PrintService printService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PrintService.LocalBinder lb = (PrintService.LocalBinder) binder;
            printService = lb.getService();
            serviceBound = true;
            refreshUi();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            printService = null;
            serviceBound = false;
            refreshUi();
        }
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshUi();
            handler.postDelayed(this, 2000);
        }
    };

    // ==================== 生命週期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        // 確保服務已啟動
        ensureServiceRunning();
        // 綁定服務以查詢狀態
        bindToService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    // ==================== 建立 UI ====================

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setPadding(dp(16), dp(24), dp(16), dp(24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 標題 ──
        TextView tvTitle = new TextView(this);
        tvTitle.setText("POS 列印服務");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        tvTitle.setTextColor(COLOR_SECTION);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(8));
        root.addView(tvTitle);

        root.addView(divider());

        // ── 服務狀態 ──
        root.addView(sectionLabel("服務狀態"));

        tvServiceStatus = new TextView(this);
        tvServiceStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvServiceStatus.setPadding(dp(8), dp(6), dp(8), dp(6));
        root.addView(tvServiceStatus);

        tvIpPort = new TextView(this);
        tvIpPort.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvIpPort.setTextColor(COLOR_HINT);
        tvIpPort.setPadding(dp(8), dp(2), dp(8), dp(8));
        root.addView(tvIpPort);

        // ── 服務控制按鈕 ──
        LinearLayout btnRow1 = horizontal();

        btnToggleService = makeButton("停止服務", COLOR_RED);
        btnToggleService.setOnClickListener(v -> toggleService());
        btnRow1.addView(btnToggleService);

        Button btnSettings = makeButton("⚙ 設定", COLOR_SECTION);
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
        btnRow1.addView(btnSettings);

        root.addView(btnRow1);
        root.addView(divider());

        // ── 印表機狀態 ──
        root.addView(sectionLabel("印表機狀態"));

        tvSunmiStatus = statusLine("內建印表機（Sunmi）：偵測中...");
        root.addView(tvSunmiStatus);

        tvBtStatus = statusLine("藍牙印表機：未連線");
        root.addView(tvBtStatus);

        tvNetStatus = statusLine("網路印表機：未連線");
        root.addView(tvNetStatus);

        root.addView(divider());

        // ── 使用說明 ──
        root.addView(sectionLabel("使用說明"));

        TextView tvHelp = new TextView(this);
        tvHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvHelp.setTextColor(COLOR_HINT);
        tvHelp.setLineSpacing(0, 1.4f);
        tvHelp.setPadding(dp(8), dp(4), dp(8), dp(4));
        tvHelp.setText(
            "1. 服務啟動後，APK 可最小化或關閉視窗\n" +
            "2. 開機後服務會自動啟動，無需手動開啟\n" +
            "3. 網頁透過 http://127.0.0.1:埠號 呼叫列印\n" +
            "4. iPad / 其他裝置請使用瀏覽器列印功能\n" +
            "5. 如服務異常，點「停止服務」再「啟動服務」"
        );
        root.addView(tvHelp);

        scroll.addView(root);
        setContentView(scroll);
    }

    // ==================== 服務操作 ====================

    private void ensureServiceRunning() {
        Intent intent = new Intent(this, PrintService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void bindToService() {
        Intent intent = new Intent(this, PrintService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void toggleService() {
        if (!serviceBound || printService == null) {
            // 嘗試啟動
            ensureServiceRunning();
            bindToService();
            return;
        }
        if (printService.isServerRunning()) {
            // 停止服務
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
                printService = null;
            }
            stopService(new Intent(this, PrintService.class));
        } else {
            // 啟動服務
            ensureServiceRunning();
            bindToService();
        }
        handler.postDelayed(this::refreshUi, 800);
    }

    // ==================== UI 更新 ====================

    private void refreshUi() {
        if (serviceBound && printService != null) {
            boolean running = printService.isServerRunning();
            int port = printService.getHttpPort();
            String ip = printService.getLocalIpAddress();

            tvServiceStatus.setText(running ? "● 服務運作中" : "● 服務已停止");
            tvServiceStatus.setTextColor(running ? COLOR_GREEN : COLOR_RED);

            String ipText = "HTTP Server：127.0.0.1:" + port;
            if (!ip.isEmpty()) ipText += "\n本機 IP：" + ip;
            tvIpPort.setText(ipText);

            btnToggleService.setText(running ? "停止服務" : "啟動服務");
            btnToggleService.setBackgroundColor(running ? COLOR_RED : COLOR_GREEN);

            // 印表機狀態
            boolean sunmiOk = printService.isSunmiConnected();
            tvSunmiStatus.setText("內建印表機（Sunmi）：" + (sunmiOk ? "已連線 ✓" : "未連線"));
            tvSunmiStatus.setTextColor(sunmiOk ? COLOR_GREEN : COLOR_HINT);

            boolean btOk = printService.isBluetoothConnected();
            tvBtStatus.setText("藍牙印表機：" + (btOk ? "已連線 ✓" : "未連線"));
            tvBtStatus.setTextColor(btOk ? COLOR_GREEN : COLOR_HINT);

            boolean netOk = printService.isNetworkConnected();
            tvNetStatus.setText("網路印表機：" + (netOk ? "已連線 ✓" : "未連線"));
            tvNetStatus.setTextColor(netOk ? COLOR_GREEN : COLOR_HINT);

        } else {
            tvServiceStatus.setText("● 服務未啟動");
            tvServiceStatus.setTextColor(COLOR_RED);
            tvIpPort.setText("點「啟動服務」開始");
            btnToggleService.setText("啟動服務");
            btnToggleService.setBackgroundColor(COLOR_GREEN);
            tvSunmiStatus.setText("內建印表機（Sunmi）：—");
            tvSunmiStatus.setTextColor(COLOR_HINT);
            tvBtStatus.setText("藍牙印表機：—");
            tvBtStatus.setTextColor(COLOR_HINT);
            tvNetStatus.setText("網路印表機：—");
            tvNetStatus.setTextColor(COLOR_HINT);
        }
    }

    // ==================== UI 工具 ====================

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(COLOR_SECTION);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(dp(4), dp(12), dp(4), dp(4));
        return tv;
    }

    private TextView statusLine(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(COLOR_HINT);
        tv.setPadding(dp(8), dp(4), dp(8), dp(4));
        return tv;
    }

    private Button makeButton(String label, int bgColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setBackgroundColor(bgColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        btn.setPadding(dp(8), dp(10), dp(8), dp(10));
        return btn;
    }

    private LinearLayout horizontal() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.setPadding(0, dp(6), 0, dp(6));
        return ll;
    }

    private android.view.View divider() {
        android.view.View v = new android.view.View(this);
        v.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(8), 0, dp(4));
        v.setLayoutParams(lp);
        return v;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
