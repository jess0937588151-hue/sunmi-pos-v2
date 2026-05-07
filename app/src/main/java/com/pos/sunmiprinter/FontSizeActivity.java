package com.pos.sunmiprinter;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 字體大小設定頁（v20260608）
 * 6 組欄位，範圍 14~30，存進 AppSettings，下次列印立即套用。
 */
public class FontSizeActivity extends AppCompatActivity {

    private static final String TAG = "FontSizeActivity";

    private AppSettings settings;

    private EditText etStore;
    private EditText etSubtitle;
    private EditText etInfo;
    private EditText etItem;
    private EditText etTotal;
    private EditText etFooter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(getApplicationContext());
        setContentView(buildUi());
        loadValues();
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setPadding(dp(16), dp(20), dp(16), dp(20));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(this);
        title.setText("收據字體大小設定");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(Color.parseColor("#1976D2"));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("範圍 " + AppSettings.FONT_MIN + "～" + AppSettings.FONT_MAX
                + " 像素，超出範圍會自動夾回。修改後按「儲存」立即套用，下次列印生效。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setTextColor(Color.parseColor("#757575"));
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(hint);

        etStore    = addRow(root, "店名（標題）", AppSettings.DEFAULT_FONT_STORE);
        etSubtitle = addRow(root, "副標", AppSettings.DEFAULT_FONT_SUBTITLE);
        etInfo     = addRow(root, "訂單資訊（單號 / 時間 / 類型 / 付款）", AppSettings.DEFAULT_FONT_INFO);
        etItem     = addRow(root, "品項 + 選項（最重要）", AppSettings.DEFAULT_FONT_ITEM);
        etTotal    = addRow(root, "總額", AppSettings.DEFAULT_FONT_TOTAL);
        etFooter   = addRow(root, "頁尾（謝謝光臨等）", AppSettings.DEFAULT_FONT_FOOTER);

        // 按鈕
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setPadding(0, dp(20), 0, 0);

        Button btnSave = new Button(this);
        btnSave.setText("儲存");
        btnSave.setAllCaps(false);
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnSave.setBackgroundColor(Color.parseColor("#4CAF50"));
        LinearLayout.LayoutParams lpSave = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
        lpSave.setMargins(dp(4), 0, dp(4), 0);
        btnSave.setLayoutParams(lpSave);
        btnSave.setPadding(dp(8), dp(14), dp(8), dp(14));
        btnSave.setOnClickListener(v -> saveValues());
        btnRow.addView(btnSave);

        Button btnReset = new Button(this);
        btnReset.setText("還原預設");
        btnReset.setAllCaps(false);
        btnReset.setTextColor(Color.WHITE);
        btnReset.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnReset.setBackgroundColor(Color.parseColor("#FF9800"));
        LinearLayout.LayoutParams lpReset = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpReset.setMargins(dp(4), 0, dp(4), 0);
        btnReset.setLayoutParams(lpReset);
        btnReset.setPadding(dp(8), dp(14), dp(8), dp(14));
        btnReset.setOnClickListener(v -> resetDefaults());
        btnRow.addView(btnReset);

        Button btnClose = new Button(this);
        btnClose.setText("關閉");
        btnClose.setAllCaps(false);
        btnClose.setTextColor(Color.WHITE);
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btnClose.setBackgroundColor(Color.parseColor("#757575"));
        LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpClose.setMargins(dp(4), 0, dp(4), 0);
        btnClose.setLayoutParams(lpClose);
        btnClose.setPadding(dp(8), dp(14), dp(8), dp(14));
        btnClose.setOnClickListener(v -> finish());
        btnRow.addView(btnClose);

        root.addView(btnRow);

        scroll.addView(root);
        return scroll;
    }

    private EditText addRow(LinearLayout parent, String label, int defVal) {
        TextView tv = new TextView(this);
        tv.setText(label + "（預設 " + defVal + "）");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(Color.parseColor("#212121"));
        tv.setPadding(dp(4), dp(8), dp(4), dp(2));
        parent.addView(tv);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(2), 0, dp(2), dp(4));
        et.setLayoutParams(lp);
        parent.addView(et);
        return et;
    }

    private void loadValues() {
        etStore.setText(String.valueOf(settings.getFontStore()));
        etSubtitle.setText(String.valueOf(settings.getFontSubtitle()));
        etInfo.setText(String.valueOf(settings.getFontInfo()));
        etItem.setText(String.valueOf(settings.getFontItem()));
        etTotal.setText(String.valueOf(settings.getFontTotal()));
        etFooter.setText(String.valueOf(settings.getFontFooter()));
    }

    private int parseSafe(EditText et, int def) {
        try {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) return def;
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private void saveValues() {
        try {
            settings.setFontStore(parseSafe(etStore,       AppSettings.DEFAULT_FONT_STORE));
            settings.setFontSubtitle(parseSafe(etSubtitle, AppSettings.DEFAULT_FONT_SUBTITLE));
            settings.setFontInfo(parseSafe(etInfo,         AppSettings.DEFAULT_FONT_INFO));
            settings.setFontItem(parseSafe(etItem,         AppSettings.DEFAULT_FONT_ITEM));
            settings.setFontTotal(parseSafe(etTotal,       AppSettings.DEFAULT_FONT_TOTAL));
            settings.setFontFooter(parseSafe(etFooter,     AppSettings.DEFAULT_FONT_FOOTER));
            loadValues(); // 重新顯示夾值後的結果
            Toast.makeText(this, "已儲存（超出 14~30 已自動夾回）", Toast.LENGTH_SHORT).show();
            LogManager.i(TAG, "font sizes saved: store=" + settings.getFontStore()
                    + " subtitle=" + settings.getFontSubtitle()
                    + " info=" + settings.getFontInfo()
                    + " item=" + settings.getFontItem()
                    + " total=" + settings.getFontTotal()
                    + " footer=" + settings.getFontFooter());
        } catch (Throwable t) {
            LogManager.e(TAG, "saveValues failed", t);
            Toast.makeText(this, "儲存失敗：" + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void resetDefaults() {
        settings.resetFontDefaults();
        loadValues();
        Toast.makeText(this, "已還原預設值", Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
