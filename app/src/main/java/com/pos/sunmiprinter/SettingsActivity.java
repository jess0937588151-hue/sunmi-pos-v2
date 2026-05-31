package com.pos.sunmiprinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 設定頁
 * 相較原版改動：
 *  - 移除「POS 網址」欄位（已不需要 WebView）
 *  - 新增「HTTP Server 埠號」欄位（預設 8080）
 *  - 儲存後通知 PrintService 重啟 HTTP Server
 *  - v20260531: 藍牙/網路的「連線/斷線/測試」改用 Service 持有的 manager，
 *    儲存後呼叫 printService.reconnectPrinters()，修「保存後主頁顯示無連線」。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int COLOR_TEXT    = Color.parseColor("#212121");
    private static final int COLOR_HINT    = Color.parseColor("#757575");
    private static final int COLOR_SECTION = Color.parseColor("#1976D2");
    private static final int COLOR_BG      = Color.WHITE;

    private AppSettings settings;
    private SunmiPrinterManager sunmiPrinter;
    private Handler handler;

    private boolean btChanged  = false;
    private boolean netChanged = false;
    private boolean portChanged = false;

    // ── HTTP Server ──
    private EditText edtHttpPort;

    // ── 內建印表機 ──
    private CheckBox chkSunmiEnabled, chkSunmiAutoCut, chkSunmiAutoDrawer, chkSunmiBuzzer;
    private Spinner  spnSunmiRole;
    private TextView tvSunmiStatus;

    // ── 藍牙印表機 ──
    private CheckBox chkBtEnabled, chkBtAutoConnect, chkBtAutoCut, chkBtBuzzer;
    private Spinner  spnBtDevice, spnBtRole;
    private List<String> btDeviceAddresses = new ArrayList<>();

    // ── 網路印表機 ──
    private CheckBox chkNetEnabled, chkNetAutoConnect, chkNetAutoCut, chkNetBuzzer;
    private EditText edtNetIp, edtNetPort;
    private Spinner  spnNetRole;

    // ── 收據 ──
    private EditText edtStoreName, edtStorePhone, edtStoreAddress, edtReceiptFooter, edtPrintCopies;

    // ── PrintService 綁定（儲存後通知重啟 / 重連，且操作 Service 持有的 manager）──
    private PrintService printService;
    private boolean serviceBound = false;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            printService = ((PrintService.LocalBinder) b).getService();
            serviceBound = true;
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            printService = null;
            serviceBound = false;
        }
    };

    // v20260531: 取得 Service 持有的 manager；Service 未綁定時回傳 null
    private BluetoothPrinterManager bt() {
        return (serviceBound && printService != null) ? printService.getBtPrinter() : null;
    }
    private NetworkPrinterManager net() {
        return (serviceBound && printService != null) ? printService.getNetPrinter() : null;
    }

    // ==================== 生命週期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler  = new Handler(Looper.getMainLooper());
        settings = new AppSettings(this);

        sunmiPrinter = new SunmiPrinterManager(this);
        sunmiPrinter.bind();

        // 綁定 PrintService（不強制建立）。藍牙/網路操作一律透過 Service 的 manager。
        bindService(new Intent(this, PrintService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);

        buildUi();

        // 2 秒後更新 Sunmi 狀態
        handler.postDelayed(this::refreshSunmiStatus, 2000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sunmiPrinter != null) sunmiPrinter.unbind();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    // ==================== 建立 UI ====================

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== HTTP Server 埠號 =====
        root.addView(sectionTitle("HTTP Server 設定"));
        root.addView(label("監聽埠號（預設 8080）："));
        edtHttpPort = editText(String.valueOf(settings.getHttpPort()), InputType.TYPE_CLASS_NUMBER);
        edtHttpPort.setHint("例如 8080");
        root.addView(edtHttpPort);

        TextView tvPortNote = new TextView(this);
        tvPortNote.setText("網頁端呼叫：http://127.0.0.1:" + settings.getHttpPort() + "/print/sunmi");
        tvPortNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvPortNote.setTextColor(COLOR_HINT);
        tvPortNote.setPadding(0, dp(2), 0, dp(8));
        root.addView(tvPortNote);

        root.addView(divider());
                // ===== API Token (v20260603) =====
        root.addView(sectionTitle("API Token"));

        TextView tvTokenNote = new TextView(this);
        tvTokenNote.setText("網頁端呼叫列印時需在 header 帶 X-API-Token，未填寫則不檢查");
        tvTokenNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTokenNote.setTextColor(COLOR_HINT);
        tvTokenNote.setPadding(0, dp(2), 0, dp(6));
        root.addView(tvTokenNote);

        final TextView tvToken = new TextView(this);
        tvToken.setText(settings.getApiToken());
        tvToken.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvToken.setTextColor(COLOR_TEXT);
        tvToken.setBackgroundColor(Color.parseColor("#F5F5F5"));
        tvToken.setPadding(dp(10), dp(10), dp(10), dp(10));
        tvToken.setTextIsSelectable(true);
        root.addView(tvToken);

        LinearLayout tokenBtnRow = horizontal();
        Button btnTokenCopy = button("複製 Token");
        btnTokenCopy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("api_token", settings.getApiToken()));
                toast("Token 已複製");
            }
        });
        tokenBtnRow.addView(btnTokenCopy);

        Button btnTokenRegen = dangerButton("重新生成");
        btnTokenRegen.setOnClickListener(v -> {
            settings.regenerateApiToken();
            tvToken.setText(settings.getApiToken());
            toast("已重新生成，網頁端需更新 token");
        });
        tokenBtnRow.addView(btnTokenRegen);
        root.addView(tokenBtnRow);

        root.addView(divider());

        // ===== 最後列印狀態 (v20260603) =====
        root.addView(sectionTitle("最後列印狀態"));

        final TextView tvLastPrint = new TextView(this);
        tvLastPrint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvLastPrint.setPadding(dp(8), dp(6), dp(8), dp(6));
        tvLastPrint.setLineSpacing(0, 1.4f);
        updateLastPrintText(tvLastPrint);
        root.addView(tvLastPrint);

        Button btnRefreshLast = button("刷新");
        btnRefreshLast.setOnClickListener(v -> updateLastPrintText(tvLastPrint));
        root.addView(btnRefreshLast);

        root.addView(divider());


        // ===== 內建印表機 =====
        root.addView(sectionTitle("內建印表機（Sunmi）"));

        tvSunmiStatus = new TextView(this);
        tvSunmiStatus.setText("印表機狀態：偵測中...");
        tvSunmiStatus.setTextColor(COLOR_HINT);
        tvSunmiStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSunmiStatus.setPadding(0, dp(4), 0, dp(8));
        root.addView(tvSunmiStatus);

        chkSunmiEnabled    = checkBox("啟用內建印表機",   settings.isSunmiEnabled());
        chkSunmiAutoCut    = checkBox("自動切紙",         settings.isSunmiAutoCut());
        chkSunmiAutoDrawer = checkBox("自動開錢箱",       settings.isSunmiAutoDrawer());
        chkSunmiBuzzer     = checkBox("蜂鳴器提醒",       settings.isSunmiBuzzer());
        root.addView(chkSunmiEnabled);
        root.addView(chkSunmiAutoCut);
        root.addView(chkSunmiAutoDrawer);
        root.addView(chkSunmiBuzzer);
        root.addView(label("列印角色："));
        spnSunmiRole = roleSpinner(settings.getSunmiRole());
        root.addView(spnSunmiRole);

        LinearLayout sunmiBtnRow = horizontal();
        Button btnSunmiStatus = button("查詢狀態");
        btnSunmiStatus.setOnClickListener(v -> {
            refreshSunmiStatus();
            if (!sunmiPrinter.isConnected()) {
                toast("未連線，嘗試重新綁定...");
                sunmiPrinter.bind();
                handler.postDelayed(this::refreshSunmiStatus, 2000);
                return;
            }
            int s = sunmiPrinter.getPrinterStatus();
            toast("狀態碼：" + s + " / " + statusText(s));
        });
        sunmiBtnRow.addView(btnSunmiStatus);

        Button btnSunmiTest = button("測試列印");
        btnSunmiTest.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("印表機未連線"); return; }
            boolean ok = sunmiPrinter.printTestReceipt();
            toast(ok ? "測試列印已送出" : "列印失敗");
        });
        sunmiBtnRow.addView(btnSunmiTest);

        Button btnSunmiCut = button("測試切紙");
        btnSunmiCut.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("未連線"); return; }
            toast(sunmiPrinter.cutPaper() ? "切紙成功" : "切紙失敗");
        });
        sunmiBtnRow.addView(btnSunmiCut);

        Button btnSunmiDrawer = button("測試錢箱");
        btnSunmiDrawer.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("未連線"); return; }
            toast(sunmiPrinter.openCashDrawer() ? "錢箱已開" : "開錢箱失敗");
        });
        sunmiBtnRow.addView(btnSunmiDrawer);

        root.addView(sunmiBtnRow);
        root.addView(divider());

        // ===== 藍牙印表機 =====
        root.addView(sectionTitle("藍牙印表機"));
        chkBtEnabled     = checkBox("啟用藍牙印表機", settings.isBtEnabled());
        chkBtAutoConnect = checkBox("開機自動連線",   settings.isBtAutoConnect());
        chkBtAutoCut     = checkBox("自動切紙",       settings.isBtAutoCut());
        chkBtBuzzer      = checkBox("蜂鳴器提醒",     settings.isBtBuzzer());
        root.addView(chkBtEnabled);
        root.addView(chkBtAutoConnect);
        root.addView(chkBtAutoCut);
        root.addView(chkBtBuzzer);
        root.addView(label("列印角色："));
        spnBtRole = roleSpinner(settings.getBtRole());
        root.addView(spnBtRole);
        root.addView(label("選擇藍牙裝置："));
        spnBtDevice = new Spinner(this);
        spnBtDevice.setBackgroundColor(Color.parseColor("#F5F5F5"));
        loadBtDevices();
        root.addView(spnBtDevice);

        TextView tvBtNote = new TextView(this);
        tvBtNote.setText("提示：連線成功後請按下方「儲存設定」，系統會把此連線交給背景服務維持，重開機也會自動連回。");
        tvBtNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvBtNote.setTextColor(COLOR_HINT);
        tvBtNote.setPadding(0, dp(4), 0, dp(4));
        root.addView(tvBtNote);

        LinearLayout btBtnRow = horizontal();
        Button btnBtRefresh = button("刷新裝置");
        btnBtRefresh.setOnClickListener(v -> { loadBtDevices(); toast("共 " + btDeviceAddresses.size() + " 個裝置"); });
        btBtnRow.addView(btnBtRefresh);

        Button btnBtConnect = button("連線");
        btnBtConnect.setOnClickListener(v -> {
            int pos = spnBtDevice.getSelectedItemPosition();
            if (pos < 0 || pos >= btDeviceAddresses.size() || btDeviceAddresses.get(pos).isEmpty()) {
                toast("請先選擇裝置"); return;
            }
            final BluetoothPrinterManager m = bt();
            if (m == null) { toast("背景服務尚未就緒，請稍候再試"); return; }
            final String addr = btDeviceAddresses.get(pos);
            final String name = (String) spnBtDevice.getSelectedItem();
            toast("藍牙連線中...");
            new Thread(() -> {
                boolean ok = m.connect(addr);
                runOnUiThread(() -> {
                    if (ok) {
                        btChanged = true;
                        settings.setBtAddress(addr);
                        settings.setBtName(name);
                        settings.setBtEnabled(true);
                        if (chkBtEnabled != null) chkBtEnabled.setChecked(true);
                        toast("已連線：" + addr + "（記得按儲存設定）");
                    } else { toast("連線失敗"); }
                });
            }).start();
        });
        btBtnRow.addView(btnBtConnect);

        Button btnBtDisconnect = dangerButton("斷線");
        btnBtDisconnect.setOnClickListener(v -> {
            BluetoothPrinterManager m = bt();
            if (m != null) m.disconnect();
            btChanged = true;
            toast("藍牙已斷線");
        });
        btBtnRow.addView(btnBtDisconnect);

        Button btnBtTest = button("測試列印");
        btnBtTest.setOnClickListener(v -> {
            final BluetoothPrinterManager m = bt();
            if (m == null || !m.isConnected()) { toast("未連線"); return; }
            new Thread(() -> {
                boolean ok = m.printText("藍牙測試列印\n" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n\n\n");
                runOnUiThread(() -> toast(ok ? "已送出" : "失敗"));
            }).start();
        });
        btBtnRow.addView(btnBtTest);

        root.addView(btBtnRow);
        root.addView(divider());

        // ===== 網路印表機 =====
        root.addView(sectionTitle("網路印表機（WiFi / LAN）"));
        chkNetEnabled     = checkBox("啟用網路印表機", settings.isNetEnabled());
        chkNetAutoConnect = checkBox("開機自動連線",   settings.isNetAutoConnect());
        chkNetAutoCut     = checkBox("自動切紙",       settings.isNetAutoCut());
        chkNetBuzzer      = checkBox("蜂鳴器提醒",     settings.isNetBuzzer());
        root.addView(chkNetEnabled);
        root.addView(chkNetAutoConnect);
        root.addView(chkNetAutoCut);
        root.addView(chkNetBuzzer);
        root.addView(label("列印角色："));
        spnNetRole = roleSpinner(settings.getNetRole());
        root.addView(spnNetRole);
        root.addView(label("IP 位址："));
        edtNetIp = editText(settings.getNetIp(), InputType.TYPE_CLASS_TEXT);
        edtNetIp.setHint("例如 192.168.1.100");
        root.addView(edtNetIp);
        root.addView(label("Port："));
        edtNetPort = editText(String.valueOf(settings.getNetPort()), InputType.TYPE_CLASS_NUMBER);
        root.addView(edtNetPort);

        LinearLayout netBtnRow = horizontal();
        Button btnNetConnect = button("連線");
        btnNetConnect.setOnClickListener(v -> {
            final NetworkPrinterManager m = net();
            if (m == null) { toast("背景服務尚未就緒，請稍候再試"); return; }
            String ip = edtNetIp.getText().toString().trim();
            int port;
            try { port = Integer.parseInt(edtNetPort.getText().toString().trim()); }
            catch (Exception e) { port = 9100; }
            if (ip.isEmpty()) { toast("請輸入 IP"); return; }
            final int fp = port;
            final String fip = ip;
            toast("連線中...");
            new Thread(() -> {
                boolean ok = m.connect(fip, fp);
                runOnUiThread(() -> {
                    if (ok) {
                        netChanged = true;
                        settings.setNetIp(fip); settings.setNetPort(fp);
                        settings.setNetEnabled(true);
                        if (chkNetEnabled != null) chkNetEnabled.setChecked(true);
                        toast("已連線：" + fip + ":" + fp + "（記得按儲存設定）");
                    } else { toast("連線失敗"); }
                });
            }).start();
        });
        netBtnRow.addView(btnNetConnect);

        Button btnNetDisconnect = dangerButton("斷線");
        btnNetDisconnect.setOnClickListener(v -> {
            NetworkPrinterManager m = net();
            if (m != null) m.disconnect();
            netChanged = true;
            toast("網路已斷線");
        });
        netBtnRow.addView(btnNetDisconnect);

        Button btnNetTest = button("測試列印");
        btnNetTest.setOnClickListener(v -> {
            final NetworkPrinterManager m = net();
            if (m == null || !m.isConnected()) { toast("未連線"); return; }
            new Thread(() -> {
                boolean ok = m.printText("網路測試列印\n" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n\n\n");
                runOnUiThread(() -> toast(ok ? "已送出" : "失敗"));
            }).start();
        });
        netBtnRow.addView(btnNetTest);

        root.addView(netBtnRow);
        root.addView(divider());

        // ===== 收據設定 =====
        root.addView(sectionTitle("收據設定"));
        root.addView(label("店名："));
        edtStoreName = editText(settings.getStoreName(), InputType.TYPE_CLASS_TEXT);
        edtStoreName.setHint("例如 我的餐廳");
        root.addView(edtStoreName);
        root.addView(label("電話："));
        edtStorePhone = editText(settings.getStorePhone(), InputType.TYPE_CLASS_PHONE);
        root.addView(edtStorePhone);
        root.addView(label("地址："));
        edtStoreAddress = editText(settings.getStoreAddress(), InputType.TYPE_CLASS_TEXT);
        root.addView(edtStoreAddress);
        root.addView(label("收據底部文字："));
        edtReceiptFooter = editText(settings.getReceiptFooter(), InputType.TYPE_CLASS_TEXT);
        root.addView(edtReceiptFooter);
        root.addView(label("列印份數："));
        edtPrintCopies = editText(String.valueOf(settings.getPrintCopies()), InputType.TYPE_CLASS_NUMBER);
        root.addView(edtPrintCopies);

        root.addView(divider());

        // ===== 儲存 / 取消 =====
        LinearLayout saveBtnRow = horizontal();

        Button btnSave = successButton("儲存設定");
        btnSave.setOnClickListener(v -> saveAll());
        saveBtnRow.addView(btnSave);

        Button btnCancel = neutralButton("取消");
        btnCancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        saveBtnRow.addView(btnCancel);

        Button btnReset = dangerButton("恢復預設");
        btnReset.setOnClickListener(v -> {
            settings.resetAll();
            toast("已恢復預設，請重新開啟設定");
            setResult(RESULT_OK);
            finish();
        });
        saveBtnRow.addView(btnReset);

        root.addView(saveBtnRow);

        // 底部留白
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        root.addView(spacer);

        scroll.addView(root);
        setContentView(scroll);
    }

    // ==================== 儲存 ====================

    private void saveAll() {
        // HTTP 埠號
        int newPort = settings.getHttpPort();
        try { newPort = Integer.parseInt(edtHttpPort.getText().toString().trim()); }
        catch (Exception e) { newPort = 8080; }
        if (newPort != settings.getHttpPort()) portChanged = true;
        settings.setHttpPort(newPort);

        // 內建印表機
        settings.setSunmiEnabled(chkSunmiEnabled.isChecked());
        settings.setSunmiAutoCut(chkSunmiAutoCut.isChecked());
        settings.setSunmiAutoDrawer(chkSunmiAutoDrawer.isChecked());
        settings.setSunmiBuzzer(chkSunmiBuzzer.isChecked());
        settings.setSunmiRole(getRoleValue(spnSunmiRole));

        // 藍牙
        settings.setBtEnabled(chkBtEnabled.isChecked());
        settings.setBtAutoConnect(chkBtAutoConnect.isChecked());
        settings.setBtAutoCut(chkBtAutoCut.isChecked());
        settings.setBtBuzzer(chkBtBuzzer.isChecked());
        settings.setBtRole(getRoleValue(spnBtRole));

        // 網路
        settings.setNetEnabled(chkNetEnabled.isChecked());
        settings.setNetAutoConnect(chkNetAutoConnect.isChecked());
        settings.setNetAutoCut(chkNetAutoCut.isChecked());
        settings.setNetBuzzer(chkNetBuzzer.isChecked());
        settings.setNetRole(getRoleValue(spnNetRole));
        settings.setNetIp(edtNetIp.getText().toString().trim());
        try { settings.setNetPort(Integer.parseInt(edtNetPort.getText().toString().trim())); }
        catch (Exception e) { settings.setNetPort(9100); }

        // 收據
        settings.setStoreName(edtStoreName.getText().toString().trim());
        settings.setStorePhone(edtStorePhone.getText().toString().trim());
        settings.setStoreAddress(edtStoreAddress.getText().toString().trim());
        settings.setReceiptFooter(edtReceiptFooter.getText().toString().trim());
        try { settings.setPrintCopies(Integer.parseInt(edtPrintCopies.getText().toString().trim())); }
        catch (Exception e) { settings.setPrintCopies(1); }

        // 埠號改了 → 通知 Service 重啟 HTTP Server
        if (portChanged && serviceBound && printService != null) {
            printService.restartHttpServer();
        }

        // v20260531: 依最新藍牙/網路設定，讓 Service 重新連線。
        // 這是修「保存後主頁顯示無連線」的關鍵：確保 Service 持有的 manager 連上線。
        if (serviceBound && printService != null) {
            printService.reconnectPrinters();
        } else {
            // Service 未綁定時，改用 startService 帶 action 觸發
            Intent reconnect = new Intent(this, PrintService.class);
            reconnect.setAction(PrintService.ACTION_RECONNECT_PRINTERS);
            try { startService(reconnect); } catch (Throwable ignored) {}
        }

        Intent data = new Intent();
        data.putExtra("bt_changed",   btChanged);
        data.putExtra("net_changed",  netChanged);
        data.putExtra("port_changed", portChanged);
        setResult(RESULT_OK, data);
        toast("設定已儲存");
        finish();
    }

    // ==================== Sunmi 狀態 ====================

    private void refreshSunmiStatus() {
        if (sunmiPrinter.isConnected()) {
            int s = sunmiPrinter.getPrinterStatus();
            tvSunmiStatus.setText("印表機狀態：" + statusText(s));
            tvSunmiStatus.setTextColor(s == 1
                    ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        } else {
            tvSunmiStatus.setText("印表機狀態：未連線（綁定中...）");
            tvSunmiStatus.setTextColor(COLOR_HINT);
        }
    }

    private String statusText(int s) {
        switch (s) {
            case 1:   return "正常";
            case 2:   return "準備中";
            case 3:   return "通訊異常";
            case 4:   return "缺紙";
            case 5:   return "過熱";
            case 6:   return "開蓋";
            case 7:   return "切刀異常";
            case 505: return "未連線";
            default:  return "未知(" + s + ")";
        }
    }

    // ==================== 藍牙裝置 ====================

    private void loadBtDevices() {
        List<String> names = new ArrayList<>();
        btDeviceAddresses.clear();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try {
                Set<BluetoothDevice> devices = adapter.getBondedDevices();
                if (devices != null) {
                    String savedAddr = settings.getBtAddress();
                    int selectedIdx = 0, idx = 0;
                    for (BluetoothDevice d : devices) {
                        String name = (d.getName() != null ? d.getName() : "Unknown")
                                + " [" + d.getAddress() + "]";
                        names.add(name);
                        btDeviceAddresses.add(d.getAddress());
                        if (d.getAddress().equals(savedAddr)) selectedIdx = idx;
                        idx++;
                    }
                    ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, names);
                    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spnBtDevice.setAdapter(ad);
                    if (!names.isEmpty()) spnBtDevice.setSelection(selectedIdx);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "BT permission error", e);
                toast("需要藍牙權限");
            }
        }
        if (names.isEmpty()) {
            names.add("無已配對裝置");
            btDeviceAddresses.add("");
            ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnBtDevice.setAdapter(ad);
        }
    }

    // ==================== UI 工具 ====================

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(COLOR_SECTION);
        tv.setPadding(0, dp(16), 0, dp(8));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(COLOR_TEXT);
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    private EditText editText(String value, int inputType) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setInputType(inputType);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setTextColor(COLOR_TEXT);
        et.setHintTextColor(COLOR_HINT);
        et.setBackgroundColor(Color.parseColor("#F5F5F5"));
        et.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(2), 0, dp(4));
        et.setLayoutParams(lp);
        return et;
    }

    private CheckBox checkBox(String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setChecked(checked);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cb.setTextColor(COLOR_TEXT);
        cb.setPadding(0, dp(4), 0, dp(4));
        return cb;
    }

    private Button button(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#1976D2"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        return btn;
    }

    private Button dangerButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#F44336"));
        return btn;
    }

    private Button successButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#4CAF50"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        return btn;
    }

    private Button neutralButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#E0E0E0"));
        btn.setTextColor(COLOR_TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        return btn;
    }

    private Spinner roleSpinner(String currentRole) {
        Spinner spn = new Spinner(this);
        spn.setBackgroundColor(Color.parseColor("#F5F5F5"));
        String[] roles  = {"customer", "kitchen", "both"};
        String[] labels = {"顧客單", "廚房單", "兩者都印"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spn.setAdapter(adapter);
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(currentRole)) { spn.setSelection(i); break; }
        }
        return spn;
    }

    private String getRoleValue(Spinner spn) {
        String[] roles = {"customer", "kitchen", "both"};
        int pos = spn.getSelectedItemPosition();
        return (pos >= 0 && pos < roles.length) ? roles[pos] : "customer";
    }

    private LinearLayout horizontal() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ll.setPadding(0, dp(6), 0, dp(6));
        return ll;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#E0E0E0"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, dp(12), 0, dp(4));
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
        private void updateLastPrintText(TextView tv) {
        long at = settings.getLastPrintAt();
        if (at <= 0) {
            tv.setText("（尚無列印紀錄）");
            tv.setTextColor(COLOR_HINT);
            return;
        }
        boolean ok = settings.getLastPrintOk();
        String err = settings.getLastPrintError();
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()).format(new java.util.Date(at));
        StringBuilder sb = new StringBuilder();
        sb.append("時間：").append(time).append("\n");
        sb.append("結果：").append(ok ? "✓ 成功" : "✗ 失敗");
        if (!ok && err != null && !err.isEmpty()) {
            sb.append("\n錯誤：").append(err);
        }
        tv.setText(sb.toString());
        tv.setTextColor(ok ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
    }

}
