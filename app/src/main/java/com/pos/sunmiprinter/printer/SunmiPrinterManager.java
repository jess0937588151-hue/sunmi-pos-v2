package com.pos.sunmiprinter.printer;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.RemoteException;
import android.util.Log;

import com.pos.sunmiprinter.AppSettings;
import com.pos.sunmiprinter.LogManager;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Sunmi 內建印表機管理（保留舊方法簽名給 PrintJsBridge / SettingsActivity 呼叫）
 * v20260603 新增:
 *   - getPrinterStatusInfo() / PrinterStatusInfo 詳細狀態
 *   - 列印結果寫入 AppSettings.recordPrintResult
 *   - 所有 catch 改用 LogManager 紀錄
 */
public class SunmiPrinterManager {

    private static final String TAG = "SunmiPrinterManager";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY = 300;

    private SunmiPrinterService printerService;
    private final Context context;
    private final AppSettings settings;
    private boolean bound = false;

    private final InnerPrinterCallback callback = new InnerPrinterCallback() {
        @Override
        protected void onConnected(SunmiPrinterService service) {
            printerService = service;
            bound = true;
            LogManager.i(TAG, "SunmiPrinterService connected");
        }
        @Override
        protected void onDisconnected() {
            printerService = null;
            bound = false;
            LogManager.w(TAG, "SunmiPrinterService disconnected");
        }
    };

    public SunmiPrinterManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.settings = new AppSettings(this.context);
    }

    // ===== Connection =====

    public void bind() {
        try {
            InnerPrinterManager.getInstance().bindService(context, callback);
        } catch (InnerPrinterException e) {
            LogManager.e(TAG, "bindService failed", e);
        }
    }

    public void unbind() {
        try {
            if (bound) {
                InnerPrinterManager.getInstance().unBindService(context, callback);
                bound = false;
            }
        } catch (InnerPrinterException e) {
            LogManager.e(TAG, "unBindService failed", e);
        }
    }

    public boolean isConnected() {
        return bound && printerService != null;
    }

    public int getPrinterStatus() {
        if (!isConnected()) return -1;
        try {
            return printerService.updatePrinterState();
        } catch (RemoteException e) {
            LogManager.e(TAG, "getPrinterStatus failed", e);
            return -1;
        }
    }

    /**
     * Sunmi updatePrinterState() 回傳值參考:
     *   1: 正常工作    2: 印表機準備中   3: 通訊異常
     *   4: 缺紙        5: 過熱          6: 開蓋
     *   7: 切刀異常    8: 切刀復位       9: 黑標未找到
     *   505: 沒有檢測到印表機
     */
    public PrinterStatusInfo getPrinterStatusInfo() {
        PrinterStatusInfo info = new PrinterStatusInfo();
        info.connected = isConnected();
        info.raw = getPrinterStatus();
        switch (info.raw) {
            case 4: info.paperOut = true; break;
            case 5: info.overheat = true; break;
            case 6: info.coverOpen = true; break;
            case 7: case 8: info.cutterError = true; break;
            default: break;
        }
        return info;
    }

    public static class PrinterStatusInfo {
        public boolean connected;
        public boolean paperOut;
        public boolean coverOpen;
        public boolean overheat;
        public boolean cutterError;
        public int raw = -1;

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"connected\":").append(connected).append(",");
            sb.append("\"paperOut\":").append(paperOut).append(",");
            sb.append("\"coverOpen\":").append(coverOpen).append(",");
            sb.append("\"overheat\":").append(overheat).append(",");
            sb.append("\"cutterError\":").append(cutterError).append(",");
            sb.append("\"raw\":").append(raw);
            sb.append("}");
            return sb.toString();
        }
    }

    // ===== Basic Print（保留舊簽名：回傳 boolean）=====

    public boolean printText(String text) {
        if (!isConnected()) {
            settings.recordPrintResult(false, "printer not connected");
            return false;
        }
        try {
            printerService.printText(text, null);
            settings.recordPrintResult(true, "");
            return true;
        } catch (Exception e) {
            settings.recordPrintResult(false, e.getMessage());
            LogManager.e(TAG, "printText failed", e);
            return false;
        }
    }

    public boolean printTextWithFont(String text, String typeface, float
