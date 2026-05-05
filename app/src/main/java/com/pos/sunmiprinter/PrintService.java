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
import android.os.Looper;
import android.util.Log;

import com.pos.sunmiprinter.printer.BluetoothPrinterManager;
import com.pos.sunmiprinter.printer.NetworkPrinterManager;
import com.pos.sunmiprinter.printer.SunmiPrinterManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 後台 Foreground Service
 * - 開機自動啟動（由 BootReceiver 觸發）
 * - 持有 PrintHttpServer、三種 PrinterManager
 * - START_STICKY 保持常駐，被殺掉後自動重啟
 */
public class PrintService extends Service {

    private static final String TAG = "PrintService";
    private static final String CHANNEL_ID = "print_service_channel";
    private static final int NOTIFICATION_ID = 1001;

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

    private boolean serverRunning = false;

    // ==================== 生命週期 ====================

    @Override
    public void onCreate() {
        super.onCreate();
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flags=" + flags);

        // 處理來自通知的指令
        if (intent != null) {
            String action = intent.getAction();
            if ("RESTART_SERVER".equals(action)) {
                restartHttpServer();
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
        super.onDestroy();
    }

    // ==================== HTTP Server ====================

    private void startHttpServer() {
        stopHttpServer();
        int port = settings.getHttpPort();
        try {
            httpServer = new PrintHttpServer(port, sunmiPrinter, btPrinter, netPrinter);
            httpServer.start();
            serverRunning = true;
            Log.d(TAG, "HTTP Server started on 127.0.0.1:" + port);
            updateNotification();
        } catch (Exception e) {
            Log.e(TAG, "HTTP Server start failed", e);
            serverRunning = false;
        }
    }

    private void stopHttpServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
            serverRunning = false;
            Log.d(TAG, "HTTP Server stopped");
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
                }).start();
            }
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
