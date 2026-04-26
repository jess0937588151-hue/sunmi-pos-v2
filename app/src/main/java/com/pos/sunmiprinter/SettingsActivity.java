package com.pos.sunmiprinter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
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

    private AppSettings settings;
    private SunmiPrinterManager sunmiPrinter;
    private BluetoothPrinterManager btPrinter;
    private NetworkPrinterManager netPrinter;

    private boolean btChanged = false;
    private boolean netChanged = false;

    // UI
    private EditText edtUrl;
    private CheckBox chkSunmiEnabled, chkSunmiAutoCut, chkSunmiAutoDrawer, chkSunmiBuzzer;
    private Spinner spnSunmiRole;
    private CheckBox chkBtEnabled, chkBtAutoConnect, chkBtAutoCut, chkBtBuzzer;
    private Spinner spnBtDevice, spnBtRole;
    private CheckBox chkNetEnabled, chkNetAutoConnect, chkNetAutoCut, chkNetBuzzer;
    private EditText edtNetIp, edtNetPort;
    private Spinner spnNetRole;
    private EditText edtStoreName, edtStorePhone, edtStoreAddress, edtReceiptFooter, edtPrintCopies;

    private List<String> btDeviceAddresses = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = new AppSettings(this);
        sunmiPrinter = new SunmiPrinterManager(this);
        sunmiPrinter.bind();
        btPrinter = new BluetoothPrinterManager();
        netPrinter = new NetworkPrinterManager();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // ===== 网址设定 =====
        root.addView(sectionTitle("网站设定"));
        root.addView(label("POS 网址："));
        edtUrl = editText(settings.getUrl(), InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(edtUrl);

        Button btnResetUrl = button("恢复预设网址");
        btnResetUrl.setOnClickListener(v -> edtUrl.setText(settings.getDefaultUrl()));
        root.addView(btnResetUrl);

        root.addView(divider());

        // ===== 内建印表机 =====
        root.addView(sectionTitle("内建印表机（Sunmi）"));
        chkSunmiEnabled = checkBox("启用内建印表机", settings.isSunmiEnabled());
        root.addView(chkSunmiEnabled);
        chkSunmiAutoCut = checkBox("自动切纸", settings.isSunmiAutoCut());
        root.addView(chkSunmiAutoCut);
        chkSunmiAutoDrawer = checkBox("自动开钱箱", settings.isSunmiAutoDrawer());
        root.addView(chkSunmiAutoDrawer);
        chkSunmiBuzzer = checkBox("蜂鸣器提醒", settings.isSunmiBuzzer());
        root.addView(chkSunmiBuzzer);
        root.addView(label("列印角色："));
        spnSunmiRole = roleSpinner(settings.getSunmiRole());
        root.addView(spnSunmiRole);

        LinearLayout sunmiBtnRow = horizontal();
        Button btnSunmiStatus = button("查询状态");
        btnSunmiStatus.setOnClickListener(v -> {
            int s = sunmiPrinter.getPrinterStatus();
            String msg;
            switch (s) {
                case 1: msg = "正常"; break;
                case 2: msg = "准备中"; break;
                case 3: msg = "通讯异常"; break;
                case 4: msg = "缺纸"; break;
                case 5: msg = "过热"; break;
                case 6: msg = "开盖"; break;
                case 7: msg = "切刀异常"; break;
                case 8: msg = "切刀恢复"; break;
                case 9: msg = "未检测到黑标"; break;
                case 505: msg = "未连线"; break;
                default: msg = "未知 (" + s + ")"; break;
            }
            toast("内建印表机状态：" + msg);
        });
        sunmiBtnRow.addView(btnSunmiStatus);

        Button btnSunmiTest = button("测试列印");
        btnSunmiTest.setOnClickListener(v -> {
            boolean ok = sunmiPrinter.printTestReceipt();
            toast(ok ? "测试列印成功" : "测试列印失败");
        });
        sunmiBtnRow.addView(btnSunmiTest);

        Button btnSunmiCut = button("测试切纸");
        btnSunmiCut.setOnClickListener(v -> {
            boolean ok = sunmiPrinter.cutPaper();
            toast(ok ? "切纸成功" : "切纸失败");
        });
        sunmiBtnRow.addView(btnSunmiCut);

        Button btnSunmiDrawer = button("测试钱箱");
        btnSunmiDrawer.setOnClickListener(v -> {
            boolean ok = sunmiPrinter.openCashDrawer();
            toast(ok ? "钱箱已开" : "开钱箱失败");
        });
        sunmiBtnRow.addView(btnSunmiDrawer);

        Button btnSunmiBuzz = button("测试蜂鸣");
        btnSunmiBuzz.setOnClickListener(v -> {
            boolean ok = sunmiPrinter.buzzer();
            toast(ok ? "蜂鸣成功" : "蜂鸣失败");
        });
        sunmiBtnRow.addView(btnSunmiBuzz);
        root.addView(sunmiBtnRow);

        root.addView(divider());

        // ===== 蓝牙印表机 =====
        root.addView(sectionTitle("蓝牙印表机"));
        chkBtEnabled = checkBox("启用蓝牙印表机", settings.isBtEnabled());
        root.addView(chkBtEnabled);
        chkBtAutoConnect = checkBox("开机自动连线", settings.isBtAutoConnect());
        root.addView(chkBtAutoConnect);
        chkBtAutoCut = checkBox("自动切纸", settings.isBtAutoCut());
        root.addView(chkBtAutoCut);
        chkBtBuzzer = checkBox("蜂鸣器提醒", settings.isBtBuzzer());
        root.addView(chkBtBuzzer);
        root.addView(label("列印角色："));
        spnBtRole = roleSpinner(settings.getBtRole());
        root.addView(spnBtRole);

        root.addView(label("选择蓝牙装置："));
        spnBtDevice = new Spinner(this);
        loadBtDevices();
        root.addView(spnBtDevice);

        LinearLayout btBtnRow = horizontal();
        Button btnBtRefresh = button("刷新装置");
        btnBtRefresh.setOnClickListener(v -> loadBtDevices());
        btBtnRow.addView(btnBtRefresh);

        Button btnBtConnect = button("连线");
        btnBtConnect.setOnClickListener(v -> {
            int pos = spnBtDevice.getSelectedItemPosition();
            if (pos < 0 || pos >= btDeviceAddresses.size()) {
                toast("请选择装置");
                return;
            }
            String addr = btDeviceAddresses.get(pos);
            toast("连线中...");
            new Thread(() -> {
                boolean ok = btPrinter.connect(addr);
                runOnUiThread(() -> {
                    if (ok) {
                        btChanged = true;
                        settings.setBtAddress(addr);
                        String name = (String) spnBtDevice.getSelectedItem();
                        settings.setBtName(name);
                        toast("蓝牙已连线：" + name);
                    } else {
                        toast("蓝牙连线失败");
                    }
                });
            }).start();
        });
        btBtnRow.addView(btnBtConnect);

        Button btnBtDisconnect = button("断线");
        btnBtDisconnect.setOnClickListener(v -> {
            btPrinter.disconnect();
            btChanged = true;
            toast("蓝牙已断线");
        });
        btBtnRow.addView(btnBtDisconnect);

        Button btnBtTest = button("测试列印");
        btnBtTest.setOnClickListener(v -> {
            boolean ok = btPrinter.printText("蓝牙测试列印\n" +
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n");
            toast(ok ? "蓝牙测试成功" : "蓝牙测试失败（未连线？）");
        });
        btBtnRow.addView(btnBtTest);
        root.addView(btBtnRow);

        root.addView(divider());

        // ===== 网路印表机 =====
        root.addView(sectionTitle("网路印表机（WiFi / LAN）"));
        chkNetEnabled = checkBox("启用网路印表机", settings.isNetEnabled());
        root.addView(chkNetEnabled);
        chkNetAutoConnect = checkBox("开机自动连线", settings.isNetAutoConnect());
        root.addView(chkNetAutoConnect);
        chkNetAutoCut = checkBox("自动切纸", settings.isNetAutoCut());
        root.addView(chkNetAutoCut);
        chkNetBuzzer = checkBox("蜂鸣器提醒", settings.isNetBuzzer());
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
        Button btnNetConnect = button("连线");
        btnNetConnect.setOnClickListener(v -> {
            String ip = edtNetIp.getText().toString().trim();
            int port;
            try { port = Integer.parseInt(edtNetPort.getText().toString().trim()); }
            catch (Exception e) { port = 9100; }
            if (ip.isEmpty()) { toast("请输入 IP"); return; }
            toast("连线中...");
            final int finalPort = port;
            new Thread(() -> {
                boolean ok = netPrinter.connect(ip, finalPort);
                runOnUiThread(() -> {
                    if (ok) {
                        netChanged = true;
                        settings.setNetIp(ip);
                        settings.setNetPort(finalPort);
                        toast("网路已连线：" + ip + ":" + finalPort);
                    } else {
                        toast("网路连线失败");
                    }
                });
            }).start();
        });
        netBtnRow.addView(btnNetConnect);

        Button btnNetDisconnect = button("断线");
        btnNetDisconnect.setOnClickListener(v -> {
            netPrinter.disconnect();
            netChanged = true;
            toast("网路已断线");
        });
        netBtnRow.addView(btnNetDisconnect);

        Button btnNetTest = button("测试列印");
        btnNetTest.setOnClickListener(v -> {
            boolean ok = netPrinter.printText("网路测试列印\n" +
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + "\n");
            toast(ok ? "网路测试成功" : "网路测试失败（未连线？）");
        });
        netBtnRow.addView(btnNetTest);
        root.addView(netBtnRow);

        root.addView(divider());

        // ===== 收据设定 =====
        root.addView(sectionTitle("收据设定"));
        root.addView(label("店名："));
        edtStoreName = editText(settings.getStoreName(), InputType.TYPE_CLASS_TEXT);
        edtStoreName.setHint("例如 我的餐厅");
        root.addView(edtStoreName);
        root.addView(label("电话："));
        edtStorePhone = editText(settings.getStorePhone(), InputType.TYPE_CLASS_PHONE);
        root.addView(edtStorePhone);
        root.addView(label("地址："));
        edtStoreAddress = editText(settings.getStoreAddress(), InputType.TYPE_CLASS_TEXT);
        root.addView(edtStoreAddress);
        root.addView(label("收据底部文字："));
        edtReceiptFooter = editText(settings.getReceiptFooter(), InputType.TYPE_CLASS_TEXT);
        root.addView(edtReceiptFooter);
        root.addView(label("列印份数："));
        edtPrintCopies = editText(String.valueOf(settings.getPrintCopies()), InputType.TYPE_CLASS_NUMBER);
        root.addView(edtPrintCopies);

        root.addView(divider());

        // ===== 储存 / 取消 =====
        LinearLayout btnRow = horizontal();

        Button btnSave = button("储存设定");
        btnSave.setBackgroundColor(Color.parseColor("#4CAF50"));
        btnSave.setTextColor(Color.WHITE);
        btnSave.setOnClickListener(v -> saveAll());
        btnRow.addView(btnSave);

        Button btnCancel = button("取消");
        btnCancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnRow.addView(btnCancel);

        Button btnReset = button("恢复预设");
        btnReset.setBackgroundColor(Color.parseColor("#F44336"));
        btnReset.setTextColor(Color.WHITE);
        btnReset.setOnClickListener(v -> {
            settings.resetAll();
            toast("已恢复预设，请重新开启设定页面");
            setResult(RESULT_OK);
            finish();
        });
        btnRow.addView(btnReset);

        root.addView(btnRow);

        // 加入底部间距
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        root.addView(spacer);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void saveAll() {
        // 网址
        String url = edtUrl.getText().toString().trim();
        if (url.isEmpty()) url = settings.getDefaultUrl();
        settings.setUrl(url);

        // 内建印表机
        settings.setSunmiEnabled(chkSunmiEnabled.isChecked());
        settings.setSunmiAutoCut(chkSunmiAutoCut.isChecked());
        settings.setSunmiAutoDrawer(chkSunmiAutoDrawer.isChecked());
        settings.setSunmiBuzzer(chkSunmiBuzzer.isChecked());
        settings.setSunmiRole(getRoleValue(spnSunmiRole));

        // 蓝牙
        settings.setBtEnabled(chkBtEnabled.isChecked());
        settings.setBtAutoConnect(chkBtAutoConnect.isChecked());
        settings.setBtAutoCut(chkBtAutoCut.isChecked());
        settings.setBtBuzzer(chkBtBuzzer.isChecked());
        settings.setBtRole(getRoleValue(spnBtRole));

        // 网路
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

        // 收据
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

        toast("设定已储存");
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
                        String name = (d.getName() != null ? d.getName() : "Unknown") + "\n" + d.getAddress();
                        names.add(name);
                        btDeviceAddresses.add(d.getAddress());
                        if (d.getAddress().equals(savedAddr)) selectedIdx = idx;
                        idx++;
                    }
                    ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, names);
                    adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spnBtDevice.setAdapter(adapter2);
                    if (!names.isEmpty()) spnBtDevice.setSelection(selectedIdx);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Bluetooth permission error", e);
                toast("需要蓝牙权限");
            }
        }

        if (names.isEmpty()) {
            names.add("无已配对装置");
            btDeviceAddresses.add("");
            ArrayAdapter<String> adapter2 = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            spnBtDevice.setAdapter(adapter2);
        }
    }

    // ==================== UI 工具 ====================

    private TextView sectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Color.parseColor("#1976D2"));
        tv.setPadding(0, dp(16), 0, dp(8));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    private EditText editText(String value, int inputType) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setInputType(inputType);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setPadding(dp(8), dp(8), dp(8), dp(8));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private CheckBox checkBox(String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setChecked(checked);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cb.setPadding(0, dp(4), 0, dp(4));
        return cb;
    }

    private Button button(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        btn.setLayoutParams(lp);
        return btn;
    }

    private Spinner roleSpinner(String currentRole) {
        Spinner spn = new Spinner(this);
        String[] roles = {"customer", "kitchen", "both"};
        String[] labels = {"顾客单", "厨房单", "两者都印"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
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
        if (btPrinter != null) btPrinter.disconnect();
        if (netPrinter != null) netPrinter.disconnect();
    }
}
