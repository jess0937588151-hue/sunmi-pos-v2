package com.pos.sunmiprinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
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

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";
    private static final int COLOR_TEXT = Color.parseColor("#212121");
    private static final int COLOR_HINT = Color.parseColor("#757575");
    private static final int COLOR_SECTION = Color.parseColor("#1976D2");
    private static final int COLOR_BG = Color.WHITE;

    private AppSettings settings;
    private SunmiPrinterManager sunmiPrinter;
    private BluetoothPrinterManager btPrinter;
    private NetworkPrinterManager netPrinter;
    private Handler handler;

    private boolean btChanged = false;
    private boolean netChanged = false;

    private EditText edtUrl;
    private CheckBox chkSunmiEnabled, chkSunmiAutoCut, chkSunmiAutoDrawer, chkSunmiBuzzer;
    private Spinner spnSunmiRole;
    private CheckBox chkBtEnabled, chkBtAutoConnect, chkBtAutoCut, chkBtBuzzer;
    private Spinner spnBtDevice, spnBtRole;
    private CheckBox chkNetEnabled, chkNetAutoConnect, chkNetAutoCut, chkNetBuzzer;
    private EditText edtNetIp, edtNetPort;
    private Spinner spnNetRole;
    private EditText edtStoreName, edtStorePhone, edtStoreAddress, edtReceiptFooter, edtPrintCopies;
    private TextView tvSunmiStatus;

    private List<String> btDeviceAddresses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        settings = new AppSettings(this);
        sunmiPrinter = new SunmiPrinterManager(this);
        sunmiPrinter.bind();
        btPrinter = new BluetoothPrinterManager();
        netPrinter = new NetworkPrinterManager();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 網址設定 =====
        root.addView(sectionTitle("網站設定"));
        root.addView(label("POS 網址："));
        edtUrl = editText(settings.getUrl(), InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(edtUrl);

        Button btnResetUrl = neutralButton("恢復預設網址");
        btnResetUrl.setOnClickListener(v -> edtUrl.setText(settings.getDefaultUrl()));
        root.addView(btnResetUrl);

        root.addView(divider());

        // ===== 內建印表機 =====
        root.addView(sectionTitle("內建印表機（Sunmi）"));

        tvSunmiStatus = new TextView(this);
        tvSunmiStatus.setText("印表機狀態：偵測中...");
        tvSunmiStatus.setTextColor(COLOR_HINT);
        tvSunmiStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSunmiStatus.setPadding(0, dp(4), 0, dp(8));
        root.addView(tvSunmiStatus);

        handler.postDelayed(this::refreshSunmiStatus, 2000);

        chkSunmiEnabled = checkBox("啟用內建印表機", settings.isSunmiEnabled());
        root.addView(chkSunmiEnabled);
        chkSunmiAutoCut = checkBox("自動切紙", settings.isSunmiAutoCut());
        root.addView(chkSunmiAutoCut);
        chkSunmiAutoDrawer = checkBox("自動開錢箱", settings.isSunmiAutoDrawer());
        root.addView(chkSunmiAutoDrawer);
        chkSunmiBuzzer = checkBox("蜂鳴器提醒", settings.isSunmiBuzzer());
        root.addView(chkSunmiBuzzer);
        root.addView(label("列印角色："));
        spnSunmiRole = roleSpinner(settings.getSunmiRole());
        root.addView(spnSunmiRole);

        LinearLayout sunmiBtnRow = horizontal();

        Button btnSunmiStatus = button("查詢狀態");
        btnSunmiStatus.setOnClickListener(v -> {
            refreshSunmiStatus();
            if (!sunmiPrinter.isConnected()) {
                toast("印表機未連線，嘗試重新綁定...");
                sunmiPrinter.bind();
                handler.postDelayed(this::refreshSunmiStatus, 2000);
                return;
            }
            int s = sunmiPrinter.getPrinterStatus();
            toast("印表機狀態碼：" + s + " / " + statusText(s));
        });
        sunmiBtnRow.addView(btnSunmiStatus);

        Button btnSunmiTest = button("測試列印");
        btnSunmiTest.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) {
                toast("印表機未連線，請先查詢狀態");
                return;
            }
            boolean ok = sunmiPrinter.printTestReceipt();
            toast(ok ? "測試列印已送出" : "測試列印失敗");
        });
        sunmiBtnRow.addView(btnSunmiTest);

        Button btnSunmiCut = button("測試切紙");
        btnSunmiCut.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("印表機未連線"); return; }
            boolean ok = sunmiPrinter.cutPaper();
            toast(ok ? "切紙成功" : "切紙失敗");
        });
        sunmiBtnRow.addView(btnSunmiCut);

        Button btnSunmiDrawer = button("測試錢箱");
        btnSunmiDrawer.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("印表機未連線"); return; }
            boolean ok = sunmiPrinter.openCashDrawer();
            toast(ok ? "錢箱已開" : "開錢箱失敗");
        });
        sunmiBtnRow.addView(btnSunmiDrawer);

        Button btnSunmiBuzz = button("測試蜂鳴");
        btnSunmiBuzz.setOnClickListener(v -> {
            if (!sunmiPrinter.isConnected()) { toast("印表機未連線"); return; }
            boolean ok = sunmiPrinter.buzzer();
            toast(ok ? "蜂鳴成功" : "蜂鳴失敗");
        });
        sunmiBtnRow.addView(btnSunmiBuzz);

        root.addView(sunmiBtnRow);
        root.addView(divider());

        // ===== 藍牙印表機 =====
        root.addView(sectionTitle("藍牙印表機"));
        chkBtEnabled = checkBox("啟用藍牙印表機", settings.isBtEnabled());
        root.addView(chkBtEnabled);
        chkBtAutoConnect = checkBox("開機自動連線", settings.isBtAutoConnect());
        root.addView(chkBtAutoConnect);
        chkBtAutoCut = checkBox("自動切紙", settings.isBtAutoCut());
        root.addView(chkBtAutoCut);
        chkBtBuzzer = checkBox("蜂鳴器提醒", settings.isBtBuzzer());
        root.addView(chkBtBuzzer);
        root.addView(label("列印角色："));
        spnBtRole = roleSpinner(settings.getBtRole());
        root.addView(spnBtRole);

        root.addView(label("選擇藍牙裝置："));
        spnBtDevice = new Spinner(this);
        spnBtDevice.setBackgroundColor(Color.parseColor("#F5F5F5"));
        loadBtDevices();
        root.addView(spnBtDevice);

        LinearLayout btBtnRow = horizontal();

        Button btnBtRefresh = button("刷新裝置");
        btnBtRefresh.setOnClickListener(v -> {
            loadBtDevices();
            toast("已刷新，共 " + btDeviceAddresses.size() + " 個裝置");
        });
        btBtnRow.addView(btnBtRefresh);

        Button btnBtConnect = button("連線");
        btnBtConnect.setOnClickListener(v -> {
            int pos = spnBtDevice.getSelectedItemPosition();
            if (pos < 0 || pos >= btDeviceAddresses.size() || btDeviceAddresses.get(pos).isEmpty()) {
                toast("請先選擇裝置");
                return;
            }
            String addr = btDeviceAddresses.get(pos);
            toast("藍牙連線中...");
            new Thread(() -> {
                boolean ok = btPrinter.connect(addr);
                runOnUiThread(() -> {
                    if (ok) {
                        btChanged = true;
                        settings.setBtAddress(addr);
                        String name = (String) spnBtDevice.getSelectedItem();
                        settings.setBtName(name);
                        toast("藍牙已連線：" + name);
                    } else {
                        toast("藍牙連線失敗");
                    }
                });
            }).start();
        });
        btBtnRow.addView(btnBtConnect);

        Button btnBtDisconnect = dangerButton("斷線");
        btnBtDisconnect.setOnClickListener(v -> {
            btPrinter.disconnect();
            btChanged = true;
            toast("藍牙已斷線");
        });
        btBtnRow.addView(btnBtDisconnect);

        Button btnBtTest = button("測試列印");
        btnBtTest.setOnClickListener(v -> {
            if (!btPrinter.isConnected()) { toast("藍牙未連線"); return; }
            new Thread(() -> {
                boolean ok = btPrinter.printText("藍牙測試列印\n" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n\n\n");
                runOnUiThread(() -> toast(ok ? "藍牙測試列印已送出" : "藍牙測試列印失敗"));
            }).start();
        });
        btBtnRow.addView(btnBtTest);

        root.addView(btBtnRow);
        root.addView(divider());

        // ===== 網路印表機 =====
        root.addView(sectionTitle("網路印表機（WiFi / LAN）"));
        chkNetEnabled = checkBox("啟用網路印表機", settings.isNetEnabled());
        root.addView(chkNetEnabled);
        chkNetAutoConnect = checkBox("開機自動連線", settings.isNetAutoConnect());
        root.addView(chkNetAutoConnect);
        chkNetAutoCut = checkBox("自動切紙", settings.isNetAutoCut());
        root.addView(chkNetAutoCut);
        chkNetBuzzer = checkBox("蜂鳴器提醒", settings.isNetBuzzer());
        root.addView(chkNetBuzzer);
        root.addView(label("列印角色："));
        spnNetRole = roleSpinner(settings.getNetRole());
        root.addView(spnNetRole);
        root.addView(label("IP 位址："));
        edtNetIp = editText(settings.getNetIp(), InputType.TYPE_CLASS_TEXT);
        edtNetIp.setHint("例如 192.168.1.100");
        edtNetIp.setHintTextColor(COLOR_HINT);
        root.addView(edtNetIp);
        root.addView(label("Port："));
        edtNetPort = editText(String.valueOf(settings.getNetPort()), InputType.TYPE_CLASS_NUMBER);
        root.addView(edtNetPort);

        LinearLayout netBtnRow = horizontal();

        Button btnNetConnect = button("連線");
        btnNetConnect.setOnClickListener(v -> {
            String ip = edtNetIp.getText().toString().trim();
            int port;
            try { port = Integer.parseInt(edtNetPort.getText().toString().trim()); }
            catch (Exception e) { port = 9100; }
            if (ip.isEmpty()) { toast("請輸入 IP"); return; }
            toast("網路連線中...");
            final int finalPort = port;
            new Thread(() -> {
                boolean ok = netPrinter.connect(ip, finalPort);
                runOnUiThread(() -> {
                    if (ok) {
                        netChanged = true;
                        settings.setNetIp(ip);
                        settings.setNetPort(finalPort);
                        toast("網路已連線：" + ip + ":" + finalPort);
                    } else {
                        toast("網路連線失敗");
                    }
                });
            }).start();
        });
        netBtnRow.addView(btnNetConnect);

        Button btnNetDisconnect = dangerButton("斷線");
        btnNetDisconnect.setOnClickListener(v -> {
            netPrinter.disconnect();
            netChanged = true;
            toast("網路已斷線");
        });
        netBtnRow.addView(btnNetDisconnect);

        Button btnNetTest = button("測試列印");
        btnNetTest.setOnClickListener(v -> {
            if (!netPrinter.isConnected()) { toast("網路未連線"); return; }
            new Thread(() -> {
                boolean ok = netPrinter.printText("網路測試列印\n" +
                        new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n\n\n");
                runOnUiThread(() -> toast(ok ? "網路測試列印已送出" : "網路測試列印失敗"));
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
        edtStoreName.setHintTextColor(COLOR_HINT);
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
        LinearLayout btnRow = horizontal();

        Button btnSave = successButton("儲存設定");
        btnSave.setOnClickListener(v -> saveAll());
        btnRow.addView(btnSave);

        Button btnCancel = neutralButton("取消");
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnRow.addView(btnCancel);

        Button btnReset = dangerButton("恢復預設");
        btnReset.setOnClickListener(v -> {
            settings.resetAll();
            toast("已恢復預設，請重新開啟設定頁面");
            setResult(RESULT_OK);
            finish();
        });
        btnRow.addView(btnReset);

        root.addView(btnRow);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        root.addView(spacer);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void refreshSunmiStatus() {
        if (sunmiPrinter.isConnected()) {
            int s = sunmiPrinter.getPrinterStatus();
            tvSunmiStatus.setText("印表機狀態：" + statusText(s));
            tvSunmiStatus.setTextColor(s == 1 ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336"));
        } else {
            tvSunmiStatus.setText("印表機狀態：未連線（綁定中...）");
            tvSunmiStatus.setTextColor(COLOR_HINT);
        }
    }

    private String statusText(int s) {
        switch (s) {
            case 1: return "正常";
            case 2: return "準備中";
            case 3: return "通訊異常";
            case 4: return "缺紙";
            case 5: return "過熱";
            case 6: return "開蓋";
            case 7: return "切刀異常";
            case 505: return "未連線";
            default: return "未知(" + s + ")";
        }
    }

    private void saveAll() {
        String url = edtUrl.getText().toString().trim();
        if (url.isEmpty()) url = settings.getDefaultUrl();
        settings.setUrl(url);

        settings.setSunmiEnabled(chkSunmiEnabled.isChecked());
        settings.setSunmiAutoCut(chkSunmiAutoCut.isChecked());
        settings.setSunmiAutoDrawer(chkSunmiAutoDrawer.isChecked());
        settings.setSunmiBuzzer(chkSunmiBuzzer.isChecked());
        settings.setSunmiRole(getRoleValue(spnSunmiRole));

        settings.setBtEnabled(chkBtEnabled.isChecked());
        settings.setBtAutoConnect(chkBtAutoConnect.isChecked());
        settings.setBtAutoCut(chkBtAutoCut.isChecked());
        settings.setBtBuzzer(chkBtBuzzer.isChecked());
        settings.setBtRole(getRoleValue(spnBtRole));

        settings.setNetEnabled(chkNetEnabled.isChecked());
        settings.setNetAutoConnect(chkNetAutoConnect.isChecked());
        settings.setNetAutoCut(chkNetAutoCut.isChecked());
        settings.setNetBuzzer(chkNetBuzzer.isChecked());
        settings.setNetRole(getRoleValue(spnNetRole));
        settings.setNetIp(edtNetIp.getText().toString().trim());
        try {
            settings.setNetPort(Integer.parseInt(edtNetPort.getText().toString().trim()));
        } catch (Exception e) {
            settings.setNetPort(9100);
        }

        settings.setStoreName(edtStoreName.getText().toString().trim());
        settings.setStorePhone(edtStorePhone.getText().toString().trim());
        settings.setStoreAddress(edtStoreAddress.getText().toString().trim());
        settings.setReceiptFooter(edtReceiptFooter.getText().toString().trim());
        try {
            settings.setPrintCopies(Integer.parseInt(edtPrintCopies.getText().toString().trim()));
        } catch (Exception e) {
            settings.setPrintCopies(1);
        }

        Intent data = new Intent();
        data.putExtra("bt_changed", btChanged);
        data.putExtra("net_changed", netChanged);
        setResult(RESULT_OK, data);

        toast("設定已儲存");
        finish();
    }

    private void loadBtDevices() {
        List<String> names = new ArrayList<>();
        btDeviceAddresses.clear();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            try {
                Set<BluetoothDevice> devices = adapter.getBondedDevices();
                if (devices != null) {
                    String savedAddr = settings.getBtAddress();
                    int selectedIdx = 0;
                    int idx = 0;
                    for (BluetoothDevice d : devices) {
                        String name = (d.getName() != null ? d.getName() : "Unknown") + " [" + d.getAddress() + "]";
                        names.add(name);
                        btDeviceAddresses.add(d.getAddress());
                        if (d.getAddress().equals(savedAddr)) selectedIdx = idx;
                        idx++;
                    }
                    ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
                            android.R.layout.simple_spinner_item, names) {
                        @Override
                        public View getView(int position, View convertView, ViewGroup parent) {
                            TextView tv = (TextView) super.getView(position, convertView, parent);
                            tv.setTextColor(COLOR_TEXT);
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                            tv.setPadding(dp(8), dp(10), dp(8), dp(10));
                            return tv;
                        }
                        @Override
                        public View getDropDownView(int position, View convertView, ViewGroup parent) {
                            TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                            tv.setTextColor(COLOR_TEXT);
                            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                            tv.setPadding(dp(12), dp(14), dp(12), dp(14));
                            tv.setBackgroundColor(Color.WHITE);
                            return tv;
                        }
                    };
                    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spnBtDevice.setAdapter(ad);
                    if (!names.isEmpty()) spnBtDevice.setSelection(selectedIdx);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Bluetooth permission error", e);
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
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        btn.setPadding(dp(12), dp(8), dp(12), dp(8));
        return btn;
    }

    private Button dangerButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#F44336"));
        btn.setTextColor(Color.WHITE);
        return btn;
    }

    private Button successButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#4CAF50"));
        btn.setTextColor(Color.WHITE);
        return btn;
    }

    private Button neutralButton(String text) {
        Button btn = button(text);
        btn.setBackgroundColor(Color.parseColor("#E0E0E0"));
        btn.setTextColor(COLOR_TEXT);
        return btn;
    }

    private Spinner roleSpinner(String currentRole) {
        Spinner spn = new Spinner(this);
        spn.setBackgroundColor(Color.parseColor("#F5F5F5"));
        String[] roles = {"customer", "kitchen", "both"};
        String[] labels = {"顧客單", "廚房單", "兩者都印"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(COLOR_TEXT);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(COLOR_TEXT);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                tv.setPadding(dp(12), dp(14), dp(12), dp(14));
                tv.setBackgroundColor(Color.WHITE);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spn.setAdapter(adapter);
        for (int i = 0; i < roles.length; i++) {
            if (roles[i].equals(currentRole)) {
                spn.setSelection(i);
                break;
            }
        }
        return spn;
    }

    private String getRoleValue(Spinner spn) {
        String[] roles = {"customer", "kitchen", "both"};
        int pos = spn.getSelectedItemPosition();
        if (pos >= 0 && pos < roles.length) return roles[pos];
        return "customer";
    }

    private LinearLayout horizontal() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        ll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sunmiPrinter != null) sunmiPrinter.unbind();
    }
}
