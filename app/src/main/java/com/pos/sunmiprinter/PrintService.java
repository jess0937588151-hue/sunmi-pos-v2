package com.pos.sunmiprinter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Looper;
import android.util.Log;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;
import fi.iki.elonen.NanoHTTPD;


import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 後台 Foreground Service
 * - 開機自動啟動（由 BootReceiver 觸發）
 * - 持有 PrintHttpServer、三種 PrinterManager
 * - START_STICKY 保持常駐，被殺掉後自動重啟
 * - v20260531: 新增 reconnectPrinters() 供設定頁儲存後重新連線藍牙/網路
 */
public class PrintService extends Service {

    private static final String TAG = "PrintService";
    private static final String CHANNEL_ID = "print_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    // v20260531: 設定頁儲存藍牙/網路設定後，送這個 action 通知 Service 重連
    public static final String ACTION_RECONNECT_PRINTERS = "RECONNECT_PRINTERS";

    // ── Binder（給 MainActivity 綁定查詢狀態用）──
    public class LocalBinder extends Binder {
        public PrintService getService() { return PrintService.this; }
    }
    private final IBinder binder = new LocalBinder();

    // ── 核心元件 ──
    private PrintHttpServer httpServer;
    private SunmiPrinterManager sunmiPrinter;
    private BluetoothPrinterManager btPrinter;
    private NetworkPrinterManager netPrinter;
    private AppSettings settings;
    private Handler handler;

  private PowerManager.WakeLock wakeLock;

    private boolean serverRunning = false;

    // ==================== 生命週期 ====================

    @Override
    public void onCreate() {
        super.onCreate();
        LogManager.init(this);
        PrintQueue.init();
        Log.d(TAG, "onCreate");


        handler = new Handler(Looper.getMainLooper());
        settings = new AppSettings(this);

        // 初始化三個印表機 Manager
        sunmiPrinter = new SunmiPrinterManager(this);
        btPrinter = new BluetoothPrinterManager();
        netPrinter = new NetworkPrinterManager();

        // 綁定 Sunmi 內建印表機
        sunmiPrinter.bind();

        // 建立通知 channel（Android 8+ 需要，Android 7 忽略）
        createNotificationChannel();

        // 升級為 Foreground Service
        startForeground(NOTIFICATION_ID, buildNotification());

        // 稍後自動連接藍牙/網路（等 Sunmi bind 完成）
        handler.postDelayed(this::autoConnectPrinters, 2000);

        // 啟動 HTTP Server
        startHttpServer();
            // 取得 PARTIAL_WAKE_LOCK，避免螢幕關掉時 NanoHTTPD 接收延遲
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrintService:HttpServerLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
                LogManager.i(TAG, "WakeLock acquired");
            }
        } catch (Throwable t) {
            LogManager.w(TAG, "WakeLock acquire failed: " + t.getMessage());
        }

}
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flags=" + flags);

        // 處理來自通知 / 設定頁的指令
        if (intent != null) {
            String action = intent.getAction();
            if ("RESTART_SERVER".equals(action)) {
                restartHttpServer();
            } else if (ACTION_RECONNECT_PRINTERS.equals(action)) {
                // v20260531: 設定頁儲存後通知重連藍牙/網路
                reconnectPrinters();
            }
        }

        // START_STICKY：服務被殺後，系統會自動重啟並傳 null intent
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

           @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        stopHttpServer();
        if (sunmiPrinter != null) sunmiPrinter.unbind();
        if (btPrinter != null) btPrinter.disconnect();
        if (netPrinter != null) netPrinter.disconnect();
                try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                LogManager.i(TAG, "WakeLock released");
            }
        } catch (Throwable t) {
            LogManager.w(TAG, "WakeLock release failed: " + t.getMessage());
        }

        PrintQueue.shutdown();
        super.onDestroy();
    }



    // ==================== HTTP Server ====================

        private void startHttpServer() {
        stopHttpServer();
        int port = settings.getHttpPort();
        try {
            httpServer = new PrintHttpServer(port, sunmiPrinter, btPrinter, netPrinter);
            // 關鍵：第二個參數 false = 非 daemon thread，避免 Android 7.1 回收
            httpServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            serverRunning = true;
            Log.d(TAG, "HTTP Server started on 127.0.0.1:" + port);
            LogManager.i(TAG, "HTTP Server started on 127.0.0.1:" + port);
            updateNotification();
        } catch (Exception e) {
            Log.e(TAG, "HTTP Server start failed", e);
            LogManager.e(TAG, "HTTP Server start failed on port " + port, e);
            serverRunning = false;
            httpServer = null;
            updateNotification();
        }
    }


       private void stopHttpServer() {
        if (httpServer != null) {
            try {
                httpServer.stop();
            } catch (Throwable t) {
                LogManager.w(TAG, "stop httpServer error: " + t.getMessage());
            }
            httpServer = null;
            serverRunning = false;
            Log.d(TAG, "HTTP Server stopped");
            LogManager.i(TAG, "HTTP Server stopped");
        }
    }


    /** 埠號設定變更時呼叫 */
    public void restartHttpServer() {
        Log.d(TAG, "Restarting HTTP Server...");
        stopHttpServer();
        handler.postDelayed(this::startHttpServer, 500);
    }

    // ==================== 自動連線 ====================

    private void autoConnectPrinters() {
        // 藍牙自動連線
        if (settings.isBtEnabled() && settings.isBtAutoConnect()) {
            String addr = settings.getBtAddress();
            if (!addr.isEmpty()) {
                new Thread(() -> {
                    boolean ok = btPrinter.connect(addr);
                    Log.d(TAG, "BT auto-connect " + addr + ": " + (ok ? "OK" : "FAIL"));
                    LogManager.i(TAG, "BT auto-connect " + addr + ": " + (ok ? "OK" : "FAIL"));
                }).start();
            }
        }

        // 網路自動連線
        if (settings.isNetEnabled() && settings.isNetAutoConnect()) {
            String ip = settings.getNetIp();
            int port = settings.getNetPort();
            if (!ip.isEmpty()) {
                new Thread(() -> {
                    boolean ok = netPrinter.connect(ip, port);
                    Log.d(TAG, "NET auto-connect " + ip + ":" + port + ": " + (ok ? "OK" : "FAIL"));
                    LogManager.i(TAG, "NET auto-connect " + ip + ":" + port + ": " + (ok ? "OK" : "FAIL"));
                }).start();
            }
        }
    }

    /**
     * v20260531: 設定頁儲存藍牙/網路設定後呼叫。
     * 問題背景：原本藍牙只在 onCreate 自動連一次；設定頁的「測試列印」用的是
     * 設定頁自己 new 出來的 BluetoothPrinterManager，與 Service 持有的 btPrinter 不是同一個。
     * 所以保存後回主畫面，Service 的 btPrinter 仍未連線，/ping 回報 bluetoothConnected=false。
     * 這裡讓 Service 依最新設定，把自己持有的 btPrinter / netPrinter 重新連上。
     */
    public void reconnectPrinters() {
        LogManager.i(TAG, "reconnectPrinters: 依最新設定重新連線藍牙/網路");

        // 藍牙：若啟用且有位址，先斷再依新位址重連
        if (settings.isBtEnabled()) {
            final String addr = settings.getBtAddress();
            if (addr != null && !addr.isEmpty()) {
                new Thread(() -> {
                    try {
                        btPrinter.disconnect();
                        boolean ok = btPrinter.connect(addr);
                        LogManager.i(TAG, "reconnectPrinters BT " + addr + ": " + (ok ? "OK" : "FAIL"));
                    } catch (Throwable t) {
                        LogManager.w(TAG, "reconnectPrinters BT error: " + t.getMessage());
                    }
                    handler.post(this::updateNotification);
                }).start();
            } else {
                LogManager.w(TAG, "reconnectPrinters: 藍牙已啟用但未設定位址");
            }
        } else {
            // 未啟用藍牙則斷線，避免殘留舊連線
            try { btPrinter.disconnect(); } catch (Throwable ignored) {}
        }

        // 網路：若啟用且有 IP，重連
        if (settings.isNetEnabled()) {
            final String ip = settings.getNetIp();
            final int port = settings.getNetPort();
            if (ip != null && !ip.isEmpty()) {
                new Thread(() -> {
                    try {
                        netPrinter.disconnect();
                        boolean ok = netPrinter.connect(ip, port);
                        LogManager.i(TAG, "reconnectPrinters NET " + ip + ":" + port + ": " + (ok ? "OK" : "FAIL"));
                    } catch (Throwable t) {
                        LogManager.w(TAG, "reconnectPrinters NET error: " + t.getMessage());
                    }
                    handler.post(this::updateNotification);
                }).start();
            }
        } else {
            try { netPrinter.disconnect(); } catch (Throwable ignored) {}
        }

        // Sunmi 內建：確保仍綁定（被回收時重新 bind）
        if (sunmiPrinter != null && !sunmiPrinter.isConnected()) {
            sunmiPrinter.bind();
        }
    }

    // ==================== 通知 ====================

    private void createNotificationChannel() {
        // NotificationChannel 只在 API 26+ 存在，但 targetSdk=25 不會自動要求
        // 用反射/版本判斷安全呼叫
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "列印服務",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("POS 列印背景服務");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // 點擊通知 → 開設定頁
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(this, 0, settingsIntent, piFlags);

        int port = settings.getHttpPort();
        String localIp = getLocalIpAddress();
        String contentText = serverRunning
                ? "服務運作中 • 127.0.0.1:" + port
                : "服務已停止";
        if (serverRunning && !localIp.isEmpty()) {
            contentText += " • IP: " + localIp;
        }

        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle("POS 列印服務")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)    // 持久通知，使用者無法滑掉
                .setAutoCancel(false);

        // API 26+ 需設 channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID);
        }

        return builder.build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    // ==================== 公開查詢方法（給 MainActivity 用）====================

    public boolean isServerRunning() { return serverRunning; }

    public int getHttpPort() { return settings.getHttpPort(); }

    public String getLocalIpAddress() {
        try {
            // 優先取 WiFi IP
            WifiManager wm = (WifiManager) getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                int ip = wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ip & 0xff), (ip >> 8 & 0xff),
                            (ip >> 16 & 0xff), (ip >> 24 & 0xff));
                }
            }
            // fallback：掃描網路介面
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getLocalIpAddress error", e);
        }
        return "";
    }

    public boolean isSunmiConnected() { return sunmiPrinter != null && sunmiPrinter.isConnected(); }
    public boolean isBluetoothConnected() { return btPrinter != null && btPrinter.isConnected(); }
    public boolean isNetworkConnected() { return netPrinter != null && netPrinter.isConnected(); }

    public SunmiPrinterManager getSunmiPrinter() { return sunmiPrinter; }
    public BluetoothPrinterManager getBtPrinter() { return btPrinter; }
    public NetworkPrinterManager getNetPrinter() { return netPrinter; }
}
