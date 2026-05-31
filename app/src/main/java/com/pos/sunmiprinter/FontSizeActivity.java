package com.pos.sunmiprinter;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 收據字級設定頁
 * - 修正：輸入框文字色與背景色明確設定，避免「數字與背景同色看不見」
 * - 新增：廚房單品名(fontKitchenItem)、廚房資訊(fontKitchenInfo) 可獨立調整
 * - 字級範圍：AppSettings.FONT_MIN ~ AppSettings.FONT_MAX
 */
public class FontSizeActivity extends Activity {

    private static final int COLOR_BG       = 0xFFF5F5F5; // 整頁背景
    private static final int COLOR_FIELD_BG = 0xFFFFFFFF; // 輸入框白底
    private static final int COLOR_TEXT     = 0xFF212121; // 深灰字（清楚可見）
    private static final int COLOR_HINT     = 0xFF9E9E9E; // 提示字
    private static final int COLOR_LABEL    = 0xFF212121; // 標籤字
    private static final int COLOR_DESC     = 0xFF616161; // 說明字

    private AppSettings settings;

    // 欄位 key 與對應輸入框
    private static class Field {
        String key;
        String label;
        String desc;
        EditText input;
        int defVal;
        Field(String key, String label, String desc, int defVal) {
            this.key = key; this.label = label; this.desc = desc; this.defVal = defVal;
        }
    }

    private final List<Field> fields = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = LogManager.getAppSettings();
        if (settings == null) {
            settings = new AppSettings(this);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(COLOR_BG);
        scroll.addView(root);

        // 標題
        TextView title = new TextView(this);
        title.setText("收據字級設定");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(22);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView tip = new TextView(this);
        tip.setText("數值範圍 " + AppSettings.FONT_MIN + " ~ " + AppSettings.FONT_MAX
                + "。廚房單品名建議 36~42 以利出單清晰辨識。");
        tip.setTextColor(COLOR_DESC);
        tip.setTextSize(14);
        tip.setPadding(0, 0, 0, dp(16));
        root.addView(tip);

        // ===== 收據（顧客單）字級 =====
        addSectionTitle(root, "顧客收據");
        addRow(root, "store",    "店名",   "最上方店名字級",        settings.getFontStore());
        addRow(root, "subtitle", "副標",   "電話/地址等副標題",      settings.getFontSubtitle());
        addRow(root, "info",     "訂單資訊", "單號/時間/桌號",        settings.getFontInfo());
        addRow(root, "item",     "品項",   "品名與金額",            settings.getFontItem());
        addRow(root, "total",    "合計",   "總金額字級",            settings.getFontTotal());
        addRow(root, "footer",   "結尾",   "謝謝光臨等",            settings.getFontFooter());

        // ===== 廚房單字級（可獨立調整）=====
        addSectionTitle(root, "廚房單（出單機）");
        addRow(root, "kitchenItem", "廚房單品名", "出單機的品名字級，數字越大越清楚",
                settings.getFontKitchenItem());
        addRow(root, "kitchenInfo", "廚房資訊",   "桌號/單號/備註等",
                settings.getFontKitchenInfo());

        // ===== 按鈕區 =====
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(24), 0, 0);
        root.addView(btnRow);

        Button saveBtn = makeButton("儲存", 0xFF1976D2);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMargins(0, 0, dp(8), 0);
        btnRow.addView(saveBtn, lp1);

        Button resetBtn = makeButton("回預設", 0xFF757575);
        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetDefaults(); }
        });
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMargins(dp(8), 0, dp(8), 0);
        btnRow.addView(resetBtn, lp2);

        Button closeBtn = makeButton("關閉", 0xFFB71C1C);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp3.setMargins(dp(8), 0, 0, 0);
        btnRow.addView(closeBtn, lp3);

        setContentView(scroll);
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(COLOR_TEXT);
        t.setTextSize(17);
        t.setPadding(0, dp(16), 0, dp(8));
        parent.addView(t);

        View line = new View(this);
        line.setBackgroundColor(0xFFE0E0E0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(line, lp);
    }

    private void addRow(LinearLayout parent, String key, String label, String desc, int value) {
        Field f = new Field(key, label, desc, value);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        // 左側標籤＋說明
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(COLOR_LABEL);
        lab.setTextSize(16);
        textBox.addView(lab);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextColor(COLOR_DESC);
        d.setTextSize(12);
        textBox.addView(d);

        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(textBox, labLp);

        // 右側輸入框（重點：明確設定文字色與背景色）
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(value));
        input.setTextColor(COLOR_TEXT);            // 深灰字，避免看不見
        input.setBackgroundColor(COLOR_FIELD_BG);  // 白底
        input.setHintTextColor(COLOR_HINT);
        input.setTextSize(18);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setSingleLine(true);

        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(dp(90),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(input, inLp);

        f.input = input;
        fields.add(f);

        parent.addView(row);
    }

    private Button makeButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(color);
        b.setAllCaps(false);
        b.setTextSize(16);
        return b;
    }

    private int clamp(int v) {
        if (v < AppSettings.FONT_MIN) return AppSettings.FONT_MIN;
        if (v > AppSettings.FONT_MAX) return AppSettings.FONT_MAX;
        return v;
    }

    private int parseField(Field f) {
        try {
            int v = Integer.parseInt(f.input.getText().toString().trim());
            return clamp(v);
        } catch (Exception e) {
            return clamp(f.defVal);
        }
    }

    private void save() {
        try {
            for (Field f : fields) {
                int v = parseField(f);
                switch (f.key) {
                    case "store":       settings.setFontStore(v);       break;
                    case "subtitle":    settings.setFontSubtitle(v);    break;
                    case "info":        settings.setFontInfo(v);        break;
                    case "item":        settings.setFontItem(v);        break;
                    case "total":       settings.setFontTotal(v);       break;
                    case "footer":      settings.setFontFooter(v);      break;
                    case "kitchenItem": settings.setFontKitchenItem(v); break;
                    case "kitchenInfo": settings.setFontKitchenInfo(v); break;
                }
                // 同步回填顯示（夾值後的實際值）
                f.input.setText(String.valueOf(v));
            }
            LogManager.i("FontSize", "字級已儲存");
            Toast.makeText(this, "字級已儲存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            LogManager.e("FontSize", "儲存失敗: " + e.getMessage());
            Toast.makeText(this, "儲存失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetDefaults() {
        try {
            settings.resetFontDefaults();
            // 重新讀取並填回各欄位
            for (Field f : fields) {
                int v;
                switch (f.key) {
                    case "store":       v = settings.getFontStore();       break;
                    case "subtitle":    v = settings.getFontSubtitle();    break;
                    case "info":        v = settings.getFontInfo();        break;
                    case "item":        v = settings.getFontItem();        break;
                    case "total":       v = settings.getFontTotal();       break;
                    case "footer":      v = settings.getFontFooter();      break;
                    case "kitchenItem": v = settings.getFontKitchenItem(); break;
                    case "kitchenInfo": v = settings.getFontKitchenInfo(); break;
                    default:            v = f.defVal;                      break;
                }
                f.input.setText(String.valueOf(v));
            }
            Toast.makeText(this, "已回復預設字級", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "回復失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return Math.round(v * d);
    }
}
