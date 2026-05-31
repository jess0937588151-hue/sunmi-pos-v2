package com.pos.sunmiprinter;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * 統一日誌管理：寫檔 + 記憶體環形緩衝
 * - 檔案位置: /sdcard/Android/data/com.pos.sunmiprinter/files/logs/app-YYYY-MM-DD.txt
 * - 錯誤另外寫: errors.txt
 * - 保留 7 天，超過自動刪除
 * - 記憶體保留最近 200 筆，提供 /logs endpoint 立即查詢
 *
 * 用法:
 *   LogManager.init(context);                 // Application 或 Service onCreate 呼叫一次
 *   LogManager.i("TAG", "啟動完成");
 *   LogManager.w("TAG", "警告訊息");
 *   LogManager.e("TAG", "失敗", throwable);
 */
public class LogManager {

    private static final String TAG = "LogManager";
    // ── v20260525 新增：客顯相關 log 統一使用此 Tag，便於 /logs 過濾 ──
    public static final String TAG_DISPLAY = "DisplayHttpServer";
    private static final int MAX_MEMORY_LINES = 200;
    private static final int KEEP_DAYS = 7;

    private static Context appContext;
    private static File logDir;
    private static final LinkedList<String> memoryBuffer = new LinkedList<>();
    private static final SimpleDateFormat tsFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public static synchronized void init(Context ctx) {
        if (appContext != null) return;
        appContext = ctx.getApplicationContext();
        try {
            File base = appContext.getExternalFilesDir(null);
            if (base == null) base = appContext.getFilesDir();
            logDir = new File(base, "logs");
            if (!logDir.exists()) logDir.mkdirs();
            cleanupOldLogs();
            i(TAG, "LogManager initialized at " + logDir.getAbsolutePath());
            i(TAG_DISPLAY, "DisplayHttpServer log channel ready"); // ← v20260525 新增
        } catch (Throwable t) {
            Log.e(TAG, "init failed", t);
        }
    }

    public static void d(String tag, String msg) { write("D", tag, msg, null); }
    public static void i(String tag, String msg) { write("I", tag, msg, null); }
    public static void w(String tag, String msg) { write("W", tag, msg, null); }
    public static void w(String tag, String msg, Throwable t) { write("W", tag, msg, t); }
    public static void e(String tag, String msg) { write("E", tag, msg, null); }
    public static void e(String tag, String msg, Throwable t) { write("E", tag, msg, t); }

    private static synchronized void write(String level, String tag, String msg, Throwable t) {
        String time = tsFormat.format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(time).append(" ").append(level).append("/").append(tag).append(": ").append(msg);
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append("\n").append(sw.toString());
        }
        String line = sb.toString();

        // logcat
        switch (level) {
            case "E": Log.e(tag, msg, t); break;
            case "W": Log.w(tag, msg, t); break;
            case "I": Log.i(tag, msg); break;
            default:  Log.d(tag, msg); break;
        }

        // memory
        memoryBuffer.add(line);
        while (memoryBuffer.size() > MAX_MEMORY_LINES) memoryBuffer.removeFirst();

        // file
        if (logDir != null) {
            String today = dateFormat.format(new Date());
            File f = new File(logDir, "app-" + today + ".txt");
            try (FileWriter fw = new FileWriter(f, true)) {
                fw.write(line);
                fw.write("\n");
            } catch (Throwable ex) {
                Log.e(TAG, "write log file failed", ex);
            }
            if ("E".equals(level)) {
                File err = new File(logDir, "errors.txt");
                try (FileWriter fw = new FileWriter(err, true)) {
                    fw.write(line);
                    fw.write("\n");
                } catch (Throwable ex) {
                    Log.e(TAG, "write errors file failed", ex);
                }
            }
        }
    }

    /** 取得記憶體中最近 N 筆日誌 */
    public static synchronized List<String> getRecent(int lines) {
        if (lines <= 0 || lines > memoryBuffer.size()) lines = memoryBuffer.size();
        List<String> out = new ArrayList<>(lines);
        int start = memoryBuffer.size() - lines;
        for (int i = start; i < memoryBuffer.size(); i++) out.add(memoryBuffer.get(i));
        return out;
    }

    /** 讀取指定日期的檔案內容（最後 N 行） */
    public static synchronized List<String> readFile(String date, int lines) {
        List<String> out = new ArrayList<>();
        if (logDir == null) return out;
        try {
            String d = (date == null || date.isEmpty()) ? dateFormat.format(new Date()) : date;
            File f = new File(logDir, "app-" + d + ".txt");
            if (!f.exists()) return out;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f));
            String s;
            LinkedList<String> tail = new LinkedList<>();
            while ((s = br.readLine()) != null) {
                tail.add(s);
                if (lines > 0 && tail.size() > lines) tail.removeFirst();
            }
            br.close();
            out.addAll(tail);
        } catch (Throwable t) {
            Log.e(TAG, "readFile failed", t);
        }
        return out;
    }

    /** 取得日誌資料夾路徑（給 UI 顯示用） */
    public static String getLogDirPath() {
        return logDir == null ? "(not initialized)" : logDir.getAbsolutePath();
    }
    /** 提供 PrintHttpServer 取得 AppSettings（需先 init 才能拿到 context） */
    public static AppSettings getAppSettings() {
        if (appContext == null) return null;
        return new AppSettings(appContext);
    }

    /** 提供 PrintHttpServer 取得 Context */
    public static Context getAppContext() {
        return appContext;
    }

    /** 取得最近錯誤（errors.txt 最後 N 行） */
    public static synchronized List<String> getRecentErrors(int lines) {
        List<String> out = new ArrayList<>();
        if (logDir == null) return out;
        File err = new File(logDir, "errors.txt");
        if (!err.exists()) return out;
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(err));
            String s;
            LinkedList<String> tail = new LinkedList<>();
            while ((s = br.readLine()) != null) {
                tail.add(s);
                if (lines > 0 && tail.size() > lines) tail.removeFirst();
            }
            br.close();
            Collections.reverse(tail); // 最新在前
            out.addAll(tail);
        } catch (Throwable t) {
            Log.e(TAG, "getRecentErrors failed", t);
        }
        return out;
    }

    /** 清除超過 KEEP_DAYS 的舊日誌 */
    private static void cleanupOldLogs() {
        if (logDir == null || !logDir.exists()) return;
        try {
            long cutoff = System.currentTimeMillis() - (long) KEEP_DAYS * 24 * 60 * 60 * 1000;
            File[] files = logDir.listFiles();
            if (files == null) return;
            for (File f : files) {
                if (f.getName().startsWith("app-") && f.lastModified() < cutoff) {
                    boolean ok = f.delete();
                    Log.i(TAG, "cleanup old log " + f.getName() + " -> " + ok);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "cleanupOldLogs failed", t);
        }
    }
}
