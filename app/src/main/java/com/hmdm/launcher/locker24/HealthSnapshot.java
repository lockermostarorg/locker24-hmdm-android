package com.hmdm.launcher.locker24;

import java.util.Map;

public final class HealthSnapshot {
    public Long ts;
    public String trigger;

    public Integer batteryPct;
    public Integer plugged;
    public Integer batteryTemperatureTenths;
    public Integer batteryHealth;

    public String manufacturer;
    public String model;
    public String androidBuild;
    public String androidVersion;
    public Integer sdkInt;
    public String securityPatch;
    public Long freeStorageMb;
    public Long freeRamMb;
    public Long uptimeS;

    public String wifiSsid;
    public Integer rssiDbm;
    public String ip;
    public Boolean mqttConnected;
    public Long lastConfigSyncAt;

    public Boolean kioskRequired;
    public Boolean kioskRunning;
    public Boolean adminUnlocked;
    public String foregroundPackage;
    public String appPackage;
    public String appVersionName;
    public Long appVersionCode;
    public String agentVersionName;
    public Long agentVersionCode;
    public Integer watchdogRestarts24h;

    public Map<String, Object> appValues;
    public Long appPublishedAt;
}
