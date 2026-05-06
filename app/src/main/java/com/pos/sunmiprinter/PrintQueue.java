package com.pos.sunmiprinter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 列印任務序列化佇列
 * - 單一執行緒，確保同一秒進來的多張線上單不會搶印表機
 * - 所有 PrintHttpServer 列印路由 / D9 自動列印 / 測試列印 都應走這裡
 * - submit 立刻回傳 Future；若需要同步等待結果可呼叫 submitAndWait
 */
public class PrintQueue {

    private static final String TAG = "PrintQueue";
    private static final long WAIT_TIMEOUT_SEC = 30; // 列印任務最長等 30 秒
    private static ExecutorService exec;

    public static synchronized void init() {
        if (exec == null || exec.isShutdown()) {
            exec = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PrintQueue");
                t.setDaemon(true);
                return t;
            });
            LogManager.i(TAG, "PrintQueue initialized");
        }
    }

    /** 非同步提交一個列印任務 */
    public static Future<Boolean> submit(String label, PrintTask task) {
        if (exec == null || exec.isShutdown()) init();
        return exec.submit(() -> {
            long t0 = System.currentTimeMillis();
            LogManager.i(TAG, "▶ start: " + label);
            try {
                boolean ok = task.run();
                long ms = System.currentTimeMillis() - t0;
                LogManager.i(TAG, (ok ? "✓" : "✗") + " done: " + label + " (" + ms + "ms)");
                return ok;
            } catch (Throwable t) {
                LogManager.e(TAG, "✗ error: " + label, t);
                return false;
            }
        });
    }

    /** 同步等待結果（給 HTTP 端點使用，這樣前端能拿到真正的成功/失敗） */
    public static boolean submitAndWait(String label, PrintTask task) {
        try {
            Future<Boolean> f = submit(label, task);
            Boolean ok = f.get(WAIT_TIMEOUT_SEC, TimeUnit.SECONDS);
            return ok != null && ok;
        } catch (Throwable t) {
            LogManager.e(TAG, "submitAndWait failed: " + label, t);
            return false;
        }
    }

    public static synchronized void shutdown() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
            LogManager.i(TAG, "PrintQueue shutdown");
        }
    }

    /** 列印任務介面：回傳 true=成功，false=失敗 */
    public interface PrintTask {
        boolean run() throws Exception;
    }
}
