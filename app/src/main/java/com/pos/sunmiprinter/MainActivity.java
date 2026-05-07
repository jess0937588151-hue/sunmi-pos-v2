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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;


/**
 * MainActivity — 健康檢查頁（v20260603 項目 8）
 * 四區塊：
 *  1) APK 版本 + Server 狀態 + 本機 IP
 *  2) 三台印表機狀態（每 3 秒刷新）含 paperOut/coverOpen/overheat 警示
 *  3) 最近 10 筆錯誤日誌（從 LogManager.getRecentErrors）
 *  4) 四顆按鈕：測試列印、重啟服務、查看完整日誌、設定
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String APK_VERSION = "v20260603";
    private static final long REFRESH_INTERVAL_MS = 3000;

    private static final int COLOR_TEXT    = Color.parseColor("#212121");
    private static final int COLOR_HINT    = Color.parseColor("#757575");
    private static final int COLOR_SECTION = Color.parseColor("#1976D2");
    private static final int COLOR_GREEN   = Color.parseColor("#4CAF50");
    private static final int COLOR_RED     = Color.parseColor("#F44336");
    private static final int COLOR_ORANGE  = Color.parseColor("#FF9800");
    private static final int COLOR_BG      = Color.WHITE;

    // ── 區塊 1：服務狀態 ──
    private TextView tvVersion;
    private TextView tvServiceStatus;
    private TextView tvIpPort;

    // ── 區塊 2：印表機狀態 ──
    private TextView tvSunmiStatus;
    private TextView tvSunmiAlert;
    private TextView tvBtStatus;
    private TextView tvNetStatus;

    // ── 區塊 3：錯誤日誌 ──
    private TextView tvErrorLog;

    // ── 區塊 4：按鈕 ──
    private Button btnTestPrint;
private Button btnRestartService;
private Button btnViewLogs;
private Button btnSettings;
private Button btnToggleService;
private Button btnBatteryWhitelist;


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
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    // ==================== 生命週期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // LogManager 在 PrintService.onCreate 已 init，這裡再保險呼叫一次
        LogManager.init(this);
        buildUi();
        ensureServiceRunning();
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
        scroll.setPadding(dp(16), dp(20), dp(16), dp(20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 標題 ──
        TextView tvTitle = new TextView(this);
        tvTitle.setText("POS 列印服務 健康檢查");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tvTitle.setTextColor(COLOR_SECTION);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dp(8));
        root.addView(tvTitle);

        root.addView(divider());

        // ===== 區塊 1：服務狀態 =====
        root.addView(sectionLabel("① 服務狀態"));

        tvVersion = new TextView(this);
        tvVersion.setText("APK 版本：" + APK_VERSION);
        tvVersion.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvVersion.setTextColor(COLOR_HINT);
        tvVersion.setPadding(dp(8), dp(2), dp(8), dp(2));
        root.addView(tvVersion);

        tvServiceStatus = new TextView(this);
        tvServiceStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvServiceStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvServiceStatus.setPadding(dp(8), dp(6), dp(8), dp(2));
        root.addView(tvServiceStatus);

        tvIpPort = new TextView(this);
        tvIpPort.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvIpPort.setTextColor(COLOR_HINT);
        tvIpPort.setPadding(dp(8), dp(2), dp(8), dp(8));
        root.addView(tvIpPort);

        btnToggleService = makeButton("停止服務", COLOR_RED);
        btnToggleService.setOnClickListener(v -> toggleService());
        root.addView(btnToggleService);

        root.addView(divider());

        // ===== 區塊 2：印表機狀態 =====
        root.addView(sectionLabel("② 印表機狀態（每 3 秒刷新）"));

        tvSunmiStatus = statusLine("內建印表機（Sunmi）：偵測中...");
        root.addView(tvSunmiStatus);

        tvSunmiAlert = new TextView(this);
        tvSunmiAlert.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSunmiAlert.setTypeface(null, android.graphics.Typeface.BOLD);
        tvSunmiAlert.setPadding(dp(8), dp(0), dp(8), dp(4));
        tvSunmiAlert.setVisibility(android.view.View.GONE);
        root.addView(tvSunmiAlert);

        tvBtStatus = statusLine("藍牙印表機：未連線");
        root.addView(tvBtStatus);

        tvNetStatus = statusLine("網路印表機：未連線");
        root.addView(tvNetStatus);

        root.addView(divider());

        // ===== 區塊 3：最近錯誤日誌 =====
        root.addView(sectionLabel("③ 最近錯誤（最多 10 筆）"));

        tvErrorLog = new TextView(this);
        tvErrorLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvErrorLog.setTextColor(COLOR_TEXT);
        tvErrorLog.setBackgroundColor(Color.parseColor("#FFF3E0"));
        tvErrorLog.setPadding(dp(10), dp(8), dp(10), dp(8));
        tvErrorLog.setLineSpacing(0, 1.3f);
        tvErrorLog.setText("（讀取中...）");
        tvErrorLog.setTextIsSelectable(true);
        root.addView(tvErrorLog);

        root.addView(divider());

        // ===== 區塊 4：操作按鈕 =====
        root.addView(sectionLabel("④ 操作"));

        LinearLayout btnRow1 = horizontal();
        btnTestPrint = makeButton("🖨 測試列印", COLOR_GREEN);
        btnTestPrint.setOnClickListener(v -> doTestPrint());
        btnRow1.addView(btnTestPrint);

        btnRestartService = makeButton("⟳ 重啟服務", COLOR_ORANGE);
        btnRestartService.setOnClickListener(v -> doRestartService());
        btnRow1.addView(btnRestartService);
        root.addView(btnRow1);

        LinearLayout btnRow2 = horizontal();
        btnViewLogs = makeButton("📋 查看完整日誌", COLOR_SECTION);
        btnViewLogs.setOnClickListener(v -> doOpenLogs());
        btnRow2.addView(btnViewLogs);

        btnSettings = makeButton("⚙ 設定", COLOR_SECTION);
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
        btnRow2.addView(btnSettings);
        root.addView(btnRow2);

                // 第三排：電池優化白名單（防止系統殺掉服務）
        LinearLayout btnRow3 = horizontal();
        btnBatteryWhitelist = makeButton("🔋 防止系統關閉服務", COLOR_RED);
        btnBatteryWhitelist.setOnClickListener(v -> requestIgnoreBatteryOptimizations());
        btnRow3.addView(btnBatteryWhitelist);
        root.addView(btnRow3);

                // 第四排：字體大小設定（v20260608）
        LinearLayout btnRow4 = horizontal();
        Button btnFontSize = makeButton("🔤 字體大小設定", COLOR_SECTION);
        btnFontSize.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, FontSizeActivity.class));
        });
        btnRow4.addView(btnFontSize);
        root.addView(btnRow4);



        // 底部留白
        android.view.View spacer = new android.view.View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        root.addView(spacer);

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
            ensureServiceRunning();
            bindToService();
            return;
        }
        if (printService.isServerRunning()) {
            if (serviceBound) {
                unbindService(serviceConnection);
                serviceBound = false;
                printService = null;
            }
            stopService(new Intent(this, PrintService.class));
        } else {
            ensureServiceRunning();
            bindToService();
        }
        handler.postDelayed(this::refreshUi, 800);
    }

    // ==================== 區塊 4：按鈕動作 ====================

    private void doTestPrint() {
        if (!serviceBound || printService == null || !printService.isSunmiConnected()) {
            toast("Sunmi 印表機未連線");
            return;
        }
        new Thread(() -> {
            try {
                String text = "POS 列印服務測試\n"
                        + "版本：" + APK_VERSION + "\n"
                        + "時間：" + new java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.getDefault()).format(new java.util.Date())
                        + "\n"
                        + "--------------------------------\n"
                        + "若您看到此單表示列印橋接運作正常\n\n\n";
                final boolean ok = printService.getSunmiPrinter() != null
                        && printService.getSunmiPrinter().printText(text);
                runOnUiThread(() -> toast(ok ? "測試列印已送出 ✓" : "列印失敗，請看錯誤日誌"));
                handler.postDelayed(this::refreshUi, 500);
            } catch (Throwable t) {
                LogManager.e(TAG, "doTestPrint failed", t);
                runOnUiThread(() -> toast("列印異常：" + t.getMessage()));
            }
        }).start();
    }

    private void doRestartService() {
        if (serviceBound && printService != null) {
            printService.restartHttpServer();
            toast("已要求重啟 HTTP Server");
        } else {
            ensureServiceRunning();
            bindToService();
            toast("正在啟動服務...");
        }
        handler.postDelayed(this::refreshUi, 1200);
    }

    private void doOpenLogs() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 日誌位置 ===\n");
        sb.append(LogManager.getLogDirPath()).append("\n\n");
        sb.append("=== 最近 50 筆 ===\n");
        try {
            List<String> rows = LogManager.getRecent(50);
            if (rows.isEmpty()) {
                sb.append("（無）");
            } else {
                for (String r : rows) sb.append(r).append("\n");
            }
        } catch (Throwable t) {
            sb.append("讀取失敗：").append(t.getMessage());
        }
        showTextDialog("完整日誌", sb.toString());
    }
    private void requestIgnoreBatteryOptimizations() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            String pkg = getPackageName();
            if (pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (pm.isIgnoringBatteryOptimizations(pkg)) {
                    toast("已在電池優化白名單 ✓");
                    return;
                }
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + pkg));
                startActivity(intent);
                toast("請點「允許」加入白名單");
            } else {
                toast("此 Android 版本無需設定");
            }
        } catch (Throwable t) {
            LogManager.e(TAG, "requestIgnoreBatteryOptimizations failed", t);
            toast("開啟設定失敗：" + t.getMessage());
        }
    }

    private void showTextDialog(String title, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        sv.addView(tv);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("關閉", null)
                .show();
    }

    // ==================== UI 刷新 ====================

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

            // 印表機狀態（含警示）
            updateSunmiStatusUi();

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
            tvSunmiAlert.setVisibility(android.view.View.GONE);
            tvBtStatus.setText("藍牙印表機：—");
            tvBtStatus.setTextColor(COLOR_HINT);
            tvNetStatus.setText("網路印表機：—");
            tvNetStatus.setTextColor(COLOR_HINT);
        }

        // 錯誤日誌（每次刷新都更新）
        updateErrorLogUi();
    }

    private void updateSunmiStatusUi() {
        if (printService == null || printService.getSunmiPrinter() == null) {
            tvSunmiStatus.setText("內建印表機（Sunmi）：—");
            tvSunmiStatus.setTextColor(COLOR_HINT);
            tvSunmiAlert.setVisibility(android.view.View.GONE);
            return;
        }
        com.pos.sunmiprinter.printer.SunmiPrinterManager.PrinterStatusInfo info =
                printService.getSunmiPrinter().getPrinterStatusInfo();

        tvSunmiStatus.setText("內建印表機（Sunmi）："
                + (info.connected ? "已連線 ✓" : "未連線") + "（raw=" + info.raw + "）");
        tvSunmiStatus.setTextColor(info.connected ? COLOR_GREEN : COLOR_HINT);

        StringBuilder alert = new StringBuilder();
        if (info.paperOut)    alert.append("⚠ 缺紙  ");
        if (info.coverOpen)   alert.append("⚠ 蓋未關  ");
        if (info.overheat)    alert.append("⚠ 過熱  ");
        if (info.cutterError) alert.append("⚠ 切刀異常  ");

        // 加上 lastPrint 失敗警示
        try {
            AppSettings s = new AppSettings(getApplicationContext());
            if (s.getLastPrintAt() > 0 && !s.getLastPrintOk()) {
                alert.append("❌ 上次列印失敗");
            }
        } catch (Throwable ignored) {}

        if (alert.length() > 0) {
            tvSunmiAlert.setText(alert.toString().trim());
            tvSunmiAlert.setTextColor(COLOR_RED);
            tvSunmiAlert.setVisibility(android.view.View.VISIBLE);
        } else {
            tvSunmiAlert.setVisibility(android.view.View.GONE);
        }
    }

    private void updateErrorLogUi() {
        try {
            List<String> errs = LogManager.getRecentErrors(10);
            if (errs == null || errs.isEmpty()) {
                tvErrorLog.setText("（目前無錯誤紀錄）");
                tvErrorLog.setTextColor(COLOR_HINT);
                tvErrorLog.setBackgroundColor(Color.parseColor("#E8F5E9"));
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < errs.size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append("• ").append(errs.get(i));
                }
                tvErrorLog.setText(sb.toString());
                tvErrorLog.setTextColor(COLOR_TEXT);
                tvErrorLog.setBackgroundColor(Color.parseColor("#FFF3E0"));
            }
        } catch (Throwable t) {
            tvErrorLog.setText("讀取錯誤日誌失敗：" + t.getMessage());
            tvErrorLog.setTextColor(COLOR_RED);
        }
    }

    // ==================== UI 工具 ====================

    private TextView sectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(COLOR_SECTION);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(dp(4), dp(12), dp(4), dp(6));
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
        btn.setPadding(dp(8), dp(12), dp(8), dp(12));
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
