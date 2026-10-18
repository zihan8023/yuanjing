package com.termux.app.iqcode.local;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

/**
 * 设备能力画像：探测这台手机"能跑多大的本地模型"，并给出运行档位。
 *
 * 设计要点：
 * - 所有探测都用 Android 原生 API / 标准系统文件，不引入额外依赖，minSdk 24 可编译；
 * - 每项探测都 try/catch 兜底，任何一项失败都不影响整体画像（降级为默认值）；
 * - 跑分结果（benchmarkTokS）由 LocalModelManager 在启动后台跑一次后写入，
 *   本类只负责缓存读写，不自己跑推理。
 *
 * 典型用法：
 *   DeviceProfile dp = DeviceProfile.detect(context);
 *   DeviceProfile.Tier t = dp.tier();          // 决定选哪个模型档
 *   dp.saveBenchmark(tokS); dp.loadBenchmark(); // 跑分缓存
 */
public final class DeviceProfile {

    /** 设备档位：LOW < MID < HIGH < FLAGSHIP，对应模型选择阶梯。 */
    public enum Tier { LOW, MID, HIGH, FLAGSHIP }

    // ---- 探测结果（全部为不可变字段）----
    public final long totalRamMb;      // 物理内存（MB）
    public final long availRamMb;      // 当前可用内存（MB）
    public final long freeStorageMb;   // 数据分区可用空间（MB）
    public final String soc;           // SoC 名称（尽力而为，未知则为 "unknown"）
    public final int cpuCores;         // 逻辑核心数
    public final long maxCpuFreqKhz;   // cpu0 最大频率（kHz），读不到为 0
    public final int batteryPercent;   // 当前电量 0-100，读不到为 -1
    public final float batteryTempC;   // 电池温度（摄氏度），读不到为 -1
    public final boolean charging;     // 是否正在充电
    public final boolean networkOnline;// 当前是否有可用网络（云端可用性判断用）

    /** 跑分缓存（tok/s），由 LocalModelManager 后台写入；-1 表示还没跑过。 */
    private float benchmarkTokS = -1f;

    private DeviceProfile(long totalRamMb, long availRamMb, long freeStorageMb,
                          String soc, int cpuCores, long maxCpuFreqKhz,
                          int batteryPercent, float batteryTempC, boolean charging,
                          boolean networkOnline) {
        this.totalRamMb = totalRamMb;
        this.availRamMb = availRamMb;
        this.freeStorageMb = freeStorageMb;
        this.soc = soc == null ? "unknown" : soc;
        this.cpuCores = cpuCores;
        this.maxCpuFreqKhz = maxCpuFreqKhz;
        this.batteryPercent = batteryPercent;
        this.batteryTempC = batteryTempC;
        this.charging = charging;
        this.networkOnline = networkOnline;
    }

    // ==================== 探测入口 ====================

    public static DeviceProfile detect(Context context) {
        Context app = context != null ? context.getApplicationContext() : null;
        return new DeviceProfile(
            detectTotalRamMb(app),
            detectAvailRamMb(app),
            detectFreeStorageMb(app),
            detectSoc(),
            detectCpuCores(),
            detectMaxCpuFreqKhz(),
            detectBatteryPercent(app),
            detectBatteryTempC(app),
            detectCharging(app),
            detectNetworkOnline(app)
        );
    }

    // ==================== 分项探测（各自独立兜底） ====================

    /** 物理内存。ActivityManager.getMemoryInfo().totalMem，API 16+。 */
    private static long detectTotalRamMb(Context app) {
        try {
            if (app == null) return 0;
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.totalMem / (1024L * 1024L);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 当前可用内存（决定"现在能不能加载模型"）。 */
    private static long detectAvailRamMb(Context app) {
        try {
            if (app == null) return 0;
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return mi.availMem / (1024L * 1024L);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 数据分区可用空间。优先取 filesDir 所在分区（写模型文件的位置）。 */
    private static long detectFreeStorageMb(Context app) {
        try {
            File dir = app != null ? app.getFilesDir() : null;
            if (dir == null) return 0;
            StatFs fs = new StatFs(dir.getAbsolutePath());
            return fs.getAvailableBytes() / (1024L * 1024L);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * SoC 名称。
     * - API 31+：Build.SOC_MANUFACTURER / Build.SOC_MODEL（高通/联发科/华为 等）；
     * - 低版本：先看 Build.HARDWARE，再看 /proc/cpuinfo 的 Hardware 行。
     */
    private static String detectSoc() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                String m = Build.SOC_MANUFACTURER;
                String s = Build.SOC_MODEL;
                if (s != null && !s.isEmpty()) {
                    return (m == null || m.isEmpty() || "unknown".equals(m)) ? s : m + " " + s;
                }
            }
            if (Build.HARDWARE != null && !Build.HARDWARE.isEmpty() && !"unknown".equals(Build.HARDWARE)) {
                return Build.HARDWARE;
            }
            // 兜底：/proc/cpuinfo 的 Hardware 行（很多老设备只能从这里拿）
            String hardware = readFirstMatch("/proc/cpuinfo", "Hardware");
            if (hardware != null && !hardware.isEmpty()) return hardware;
        } catch (Throwable ignored) { }
        return "unknown";
    }

    /** 逻辑核心数。 */
    private static int detectCpuCores() {
        try {
            int cores = Runtime.getRuntime().availableProcessors();
            return cores > 0 ? cores : 4;
        } catch (Throwable t) {
            return 4;
        }
    }

    /** cpu0 最大频率（kHz）。部分设备（如某些平板/定制 ROM）文件不存在，兜底为 0。 */
    private static long detectMaxCpuFreqKhz() {
        try {
            String line = readFirstLine("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            if (line != null && !line.trim().isEmpty()) {
                return Long.parseLong(line.trim());
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** 电量百分比。BatteryManager.BATTERY_PROPERTY_CAPACITY，API 21+。 */
    private static int detectBatteryPercent(Context app) {
        try {
            if (app == null) return -1;
            BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
            int v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            return v >= 0 ? v : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 电池温度（摄氏度）。BATTERY_PROPERTY_TEMPERATURE 是 @SystemApi 不对外编译，
     * 改用公开广播 ACTION_BATTERY_CHANGED 的 EXTRA_TEMPERATURE（所有版本可用）。
     * 部分设备恒为 0 或负数，视为不可用。
     */
    private static float detectBatteryTempC(Context app) {
        try {
            if (app == null) return -1;
            Intent battery = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return -1;
            int tenths = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (tenths <= 0) return -1;
            return tenths / 10f;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** 是否充电中。 */
    private static boolean detectCharging(Context app) {
        try {
            if (app == null) return false;
            BatteryManager bm = (BatteryManager) app.getSystemService(Context.BATTERY_SERVICE);
            return bm.isCharging();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 是否有可用网络。用 ConnectivityManager.getActiveNetworkInfo（API 23+，项目 minSdk 24 安全）。 */
    private static boolean detectNetworkOnline(Context app) {
        try {
            if (app == null) return false;
            android.net.ConnectivityManager cm =
                (android.net.ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 档位计算 ====================

    /**
     * 档位规则（可单独调用，UI 展示"为什么推荐这个档"时用）：
     *   FLAGSHIP : RAM >= 12GB 且识别到旗舰 SoC（或跑分 >= 25 tok/s）
     *   HIGH     : RAM >= 8GB
     *   MID      : RAM >= 4GB
     *   LOW      : 其余
     */
    public Tier tier() {
        long ram = totalRamMb;
        if (ram <= 0) ram = availRamMb; // 极端情况下 totalMem 拿不到，退用可用内存
        if (ram >= 12288 && (isFlagshipSoc() || benchmarkTokS >= 25f)) return Tier.FLAGSHIP;
        if (ram >= 8192) return Tier.HIGH;
        if (ram >= 4096) return Tier.MID;
        return Tier.LOW;
    }

    /** 是否识别为旗舰 SoC（用于 FLAGSHIP 档加分判断；白名单可后续扩充）。 */
    private boolean isFlagshipSoc() {
        String s = soc.toLowerCase(Locale.US);
        if (s.contains("sm8") || s.contains("骁龙8") || s.contains("snapdragon 8")) return true; // 骁龙 8 系
        if (s.contains("mt9200") || s.contains("天玑9200") || s.contains("dimensity 9")) return true; // 天玑 9 系
        if (s.contains("kirin 9000") || s.contains("麒麟9000")) return true; // 麒麟 9 系
        return s.contains("apple m") || s.contains("tensor");
    }

    /** 当前电量/温度是否适合跑本地推理（发热或低电量时不应启用）。 */
    public boolean thermallyFit() {
        if (batteryPercent >= 0 && batteryPercent < 15 && !charging) return false;
        if (batteryTempC > 0 && batteryTempC >= 45f) return false;
        return true;
    }

    /** 可用内存是否足够加载某个模型（按"至少留 40% 内存给系统"保守估算）。 */
    public boolean canLoadModel(long minRamMb) {
        if (minRamMb <= 0) return true;
        long budget = availRamMb > 0 ? availRamMb : totalRamMb;
        return budget > 0 && minRamMb <= budget * 0.6f;
    }

    /** 存储是否足够下载某个模型（模型文件 + 50% 余量）。 */
    public boolean canStoreModel(long sizeMb) {
        return freeStorageMb > 0 && sizeMb > 0 && freeStorageMb >= sizeMb * 1.5f;
    }

    // ==================== 跑分缓存 ====================

    private static final String PREFS = "iq_code_local_device";
    private static final String KEY_BENCH = "benchmark_toks";
    private static final String KEY_BENCH_SOC = "benchmark_soc";

    /** 读取缓存的跑分；SoC 变化时视为失效（换设备了）。 */
    public static float loadBenchmark(Context context, DeviceProfile profile) {
        try {
            if (context == null || profile == null) return -1f;
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String cachedSoc = p.getString(KEY_BENCH_SOC, "");
            if (!cachedSoc.isEmpty() && !cachedSoc.equals(profile.soc)) return -1f;
            return p.getFloat(KEY_BENCH, -1f);
        } catch (Throwable t) {
            return -1f;
        }
    }

    /** 写入跑分结果并同步到本对象。 */
    public void saveBenchmark(Context context, float tokS) {
        this.benchmarkTokS = tokS;
        try {
            if (context == null) return;
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putFloat(KEY_BENCH, tokS)
                .putString(KEY_BENCH_SOC, soc)
                .apply();
        } catch (Throwable ignored) { }
    }

    /** 当前生效的跑分值（未跑过为 -1）。 */
    public float benchmarkTokS() {
        return benchmarkTokS;
    }

    /** 供设置界面展示的一句话画像。 */
    public String describe() {
        return String.format(Locale.US,
            "RAM %dMB / 可用 %dMB / 存储 %dMB / %s / %d核 / 电量 %d%% / %s",
            totalRamMb, availRamMb, freeStorageMb, soc, cpuCores,
            batteryPercent, charging ? "充电中" : "未充电");
    }

    // ==================== 工具方法 ====================

    /** 读 /proc/cpuinfo 里某个键（如 Hardware）的值。 */
    private static String readFirstMatch(String path, String key) {
        try {
            java.io.BufferedReader br =
                new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(path)));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(key)) {
                    int colon = line.indexOf(':');
                    br.close();
                    return colon >= 0 ? line.substring(colon + 1).trim() : line.trim();
                }
            }
            br.close();
        } catch (Throwable ignored) { }
        return null;
    }

    /** 读单个文件的第一个非空行。 */
    private static String readFirstLine(String path) {
        try {
            java.io.BufferedReader br =
                new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(path)));
            String line = br.readLine();
            br.close();
            return line;
        } catch (Throwable ignored) { }
        return null;
    }
}
