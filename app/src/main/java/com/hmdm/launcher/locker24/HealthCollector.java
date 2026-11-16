package com.hmdm.launcher.locker24;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;

import com.hmdm.launcher.BuildConfig;
import com.hmdm.launcher.helper.SettingsHelper;
import com.hmdm.launcher.json.ServerConfig;
import com.hmdm.launcher.pro.ProUtils;
import com.hmdm.launcher.util.PushNotificationMqttWrapper;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class HealthCollector {
    private static final long MB = 1_048_576L;
    private static final int INVALID_RSSI = -127;
    private static final long MAIN_THREAD_READ_TIMEOUT_MS = 2_000L;

    private HealthCollector() {
    }

    public static HealthSnapshot collect(Context context, String trigger) {
        Context ctx = context.getApplicationContext();
        HealthSnapshot s = new HealthSnapshot();
        s.ts = System.currentTimeMillis();
        s.trigger = trigger;

        ServerConfig config = null;
        try {
            config = SettingsHelper.getInstance(ctx).getConfig();
        } catch (Throwable ignored) {
        }
        String mainApp = config != null ? config.getMainApp() : null;
        if (mainApp != null && mainApp.trim().isEmpty()) {
            mainApp = null;
        }

        collectPower(ctx, s);
        collectSystem(ctx, s);
        collectNetwork(ctx, s, config);
        collectKiosk(ctx, s, mainApp);

        s.agentVersionName = BuildConfig.VERSION_NAME;
        s.agentVersionCode = (long) BuildConfig.VERSION_CODE;
        try {
            s.watchdogRestarts24h = HealthCounters.watchdogRestarts24h(ctx);
        } catch (Throwable ignored) {
        }
        try {
            HealthValues.Snapshot app = HealthValues.snapshot(ctx);
            if (app != null) {
                s.appValues = app.values;
                s.appPublishedAt = app.publishedAt;
            }
        } catch (Throwable ignored) {
        }
        return s;
    }

    private static void collectPower(Context ctx, HealthSnapshot s) {
        try {
            Intent battery = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                return;
            }
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                s.batteryPct = Math.round(level * 100f / scale);
            }
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if (plugged >= 0) {
                s.plugged = plugged;
            }
            if (battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                s.batteryTemperatureTenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            }
            int health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            if (health >= 0) {
                s.batteryHealth = health;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void collectSystem(Context ctx, HealthSnapshot s) {
        try {
            s.manufacturer = Build.MANUFACTURER;
            s.model = Build.MODEL;
            s.androidBuild = Build.DISPLAY;
            s.androidVersion = Build.VERSION.RELEASE;
            s.sdkInt = Build.VERSION.SDK_INT;
            s.securityPatch = Build.VERSION.SECURITY_PATCH;
        } catch (Throwable ignored) {
        }
        try {
            s.freeStorageMb = new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes() / MB;
        } catch (Throwable ignored) {
        }
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(info);
                s.freeRamMb = info.availMem / MB;
            }
        } catch (Throwable ignored) {
        }
        try {
            s.uptimeS = SystemClock.elapsedRealtime() / 1000;
        } catch (Throwable ignored) {
        }
    }

    private static void collectNetwork(Context ctx, HealthSnapshot s, ServerConfig config) {
        ConnectivityManager cm = null;
        Network active = null;
        try {
            cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            active = cm != null ? cm.getActiveNetwork() : null;
        } catch (Throwable ignored) {
        }
        try {
            NetworkCapabilities caps = active != null ? cm.getNetworkCapabilities(active) : null;
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                if (info != null) {
                    int rssi = info.getRssi();
                    if (rssi > INVALID_RSSI && rssi < 0) {
                        s.rssiDbm = rssi;
                    }
                    if (canReadSsid(ctx)) {
                        s.wifiSsid = info.getSSID();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            LinkProperties lp = active != null ? cm.getLinkProperties(active) : null;
            if (lp != null) {
                for (LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress address = la.getAddress();
                    if (address instanceof Inet4Address) {
                        s.ip = address.getHostAddress();
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            String push = config != null ? config.getPushOptions() : null;
            if (push != null && push.startsWith("mqtt")) {
                s.mqttConnected = readMqttConnected();
            }
        } catch (Throwable ignored) {
        }
        try {
            long at = HealthCounters.getLastConfigSyncAt(ctx);
            if (at > 0) {
                s.lastConfigSyncAt = at;
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean canReadSsid(Context ctx) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            return lm != null && lm.isLocationEnabled();
        }
        return true;
    }

    /** The MQTT wrapper is used from the main thread, so its state is read there. */
    private static Boolean readMqttConnected() throws InterruptedException {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return PushNotificationMqttWrapper.getInstance().isConnected();
        }
        final AtomicReference<Boolean> result = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                result.set(PushNotificationMqttWrapper.getInstance().isConnected());
            } catch (Throwable ignored) {
            } finally {
                done.countDown();
            }
        });
        done.await(MAIN_THREAD_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return result.get();
    }

    private static void collectKiosk(Context ctx, HealthSnapshot s, String mainApp) {
        try {
            s.kioskRequired = ProUtils.kioskModeRequired(ctx);
        } catch (Throwable ignored) {
        }
        try {
            s.kioskRunning = ProUtils.isKioskModeRunning(ctx);
        } catch (Throwable ignored) {
        }
        try {
            s.adminUnlocked = KioskState.isAdminUnlocked(ctx);
        } catch (Throwable ignored) {
        }
        try {
            if (KioskState.isLauncherResumed()) {
                s.foregroundPackage = ctx.getPackageName();
            } else if (mainApp != null && Boolean.TRUE.equals(s.kioskRunning)) {
                s.foregroundPackage = mainApp;
            }
        } catch (Throwable ignored) {
        }
        s.appPackage = mainApp;
        if (mainApp == null) {
            return;
        }
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(mainApp, 0);
            s.appVersionName = info.versionName;
            s.appVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode() : (long) info.versionCode;
        } catch (Throwable ignored) {
        }
    }
}
