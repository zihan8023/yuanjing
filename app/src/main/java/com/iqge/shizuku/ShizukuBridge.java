package com.iqge.shizuku;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import rikka.shizuku.Shizuku;

/**
 * Shizuku（https://github.com/RikkaApps/Shizuku）授权申请与状态查询。
 *
 * Shizuku 由用户通过 adb 或 Root 启动一个 shell 身份的进程，再把它的 Binder 交给本应用，应用因此能
 * 以 shell 权限做事而不需要设备 Root。这里只负责“申请授权 + 报告状态”，需要权限的能力由上层按需接入。
 * 所有调用都兜住异常：Binder 没起来、管理端没装、用户拒绝都不能让界面崩。
 */
public final class ShizukuBridge {
    /** Shizuku 管理端包名（Sui 同样由它承载）。 */
    public static final String MANAGER_PACKAGE = "moe.shizuku.privileged.api";
    /** Shizuku 项目地址，未安装时给用户的安装入口。 */
    public static final String PROJECT_URL = "https://github.com/RikkaApps/Shizuku";

    /** 没有安装 Shizuku / Sui。 */
    public static final int UNAVAILABLE = 0;
    /** 装了管理端，但 shell 服务没在运行。 */
    public static final int NOT_RUNNING = 1;
    /** 服务在运行，本应用还没被授权。 */
    public static final int DENIED = 2;
    /** 已授权，可以用 shell 身份。 */
    public static final int GRANTED = 3;

    private ShizukuBridge() { }

    public static int state(Context context) {
        try {
            if (Shizuku.pingBinder()) {
                return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ? GRANTED : DENIED;
            }
        } catch (Throwable ignored) {
        }
        return managerInstalled(context) ? NOT_RUNNING : UNAVAILABLE;
    }

    public static boolean managerInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(MANAGER_PACKAGE, 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 已授权时的服务详情（服务版本 / shell uid / SELinux 上下文），未授权或异常返回 null。 */
    public static String detail() {
        try {
            if (!Shizuku.pingBinder()) return null;
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return null;
            String seLinux = Shizuku.getSELinuxContext();
            String uid = "uid " + Shizuku.getUid();
            return "服务 v" + Shizuku.getVersion() + " · " + uid + (seLinux == null || seLinux.isEmpty() ? "" : " · " + seLinux);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 授权状态的中文说明，直接当设置项当前值用。 */
    public static String stateLabel(Context context) {
        switch (state(context)) {
            case GRANTED: return "已授权（shell 身份）";
            case DENIED: return "未授权，点此申请";
            case NOT_RUNNING: return "服务未运行，先在 Shizuku 里启动";
            default: return "未安装 Shizuku，点此了解";
        }
    }

    /**
     * 弹出 Shizuku 的授权请求。返回 null 表示请求已交给 Shizuku，结果由
     * {@link Shizuku.OnRequestPermissionResultListener} 回调；否则返回不能申请的原因。
     */
    public static String requestPermission(int requestCode) {
        try {
            if (Shizuku.isPreV11()) return "当前 Shizuku 版本过低，请升级到 11 以上或改用 Sui";
            if (!Shizuku.pingBinder()) return "Shizuku 服务未运行：请先打开 Shizuku 并启动服务";
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return null;
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                return "上次选择了“拒绝且不再询问”，请在 Shizuku 的授权应用列表里手动允许 com.iqge";
            }
            Shizuku.requestPermission(requestCode);
            return null;
        } catch (Throwable e) {
            String message = e.getMessage();
            return "授权请求失败：" + (message == null ? e.toString() : message);
        }
    }

    /** 打开 Shizuku 管理端；没装则返回 false。 */
    public static boolean openManager(Context context) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(MANAGER_PACKAGE);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
