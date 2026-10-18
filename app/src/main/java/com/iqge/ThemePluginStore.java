package com.iqge;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主题插件：声明式 JSON 调色板，不含任何可执行代码。
 *
 * 安装 = 把 <code>*.json</code> 放进 <code>&lt;app home&gt;/.iq/themes/</code>（或用设置里的导入），
 * Agent 也可以直接用文件工具写进去。文件格式：
 * <pre>
 * {
 *   "name":  "暖墨绿",
 *   "dark":  true,
 *   "colors": {
 *     "bg": "#121110", "surface": "#191816", "text": "#ECE8E1",
 *     "accent": "#7EB288", "green": "#7EB288", "red": "#D6786F"
 *   }
 * }
 * </pre>
 * 颜色支持 #RGB / #RRGGBB / #AARRGGBB。未声明的颜色按默认派生规则补齐，
 * 所以只写 bg/surface/text/accent 四个也能得到完整主题。
 */
public final class ThemePluginStore {
    /** palette JSON key -> MainActivity field. All keys optional; missing ones are derived. */
    public static final String[] COLOR_KEYS = {
        "bg", "top", "sidebar", "surface", "surface2", "surface3",
        "border", "borderSoft", "text", "muted", "muted2",
        "accent", "green", "red", "userBg", "terminalBg"
    };
    /** style JSON keys: global UI knobs a plugin may tune (values are clamped). */
    public static final String[] STYLE_KEYS = {"cornerScale", "fontScale", "gradientAccent"};
    private static final int MAX_THEME_BYTES = 64 * 1024;

    public static final class Theme {
        public final String id;
        public final String name;
        public final boolean dark;
        public final JSONObject colors;
        public final JSONObject style;

        Theme(String id, String name, boolean dark, JSONObject colors, JSONObject style) {
            this.id = id; this.name = name; this.dark = dark; this.colors = colors; this.style = style;
        }
    }

    private ThemePluginStore() {}

    public static File dir(Context context) {
        return new File(TermuxConstants.TERMUX_HOME_DIR_PATH, ".iq/themes");
    }

    public static List<Theme> list(Context context) {
        List<Theme> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files == null) return out;
        for (File file : files) {
            Theme theme = parse(file);
            if (theme != null) out.add(theme);
        }
        Collections.sort(out, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    public static Theme load(Context context, String id) {
        if (id == null || !isSafeId(id)) return null;
        return parse(new File(dir(context), id));
    }

    /** Imports a SAF-picked JSON document; returns the new theme id. */
    public static String importFrom(Context context, Uri uri) throws Exception {
        StringBuilder body = new StringBuilder();
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                body.append(buffer, 0, read);
                if (body.length() > MAX_THEME_BYTES) throw new IllegalArgumentException("主题文件超过 64 KB");
            }
        }
        JSONObject document = new JSONObject(body.toString());
        String name = document.optString("name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("主题缺少 name 字段");
        JSONObject colors = document.optJSONObject("colors");
        if (colors == null || colors.length() == 0) throw new IllegalArgumentException("主题缺少 colors 字段");
        JSONObject normalized = new JSONObject();
        int parsed = 0;
        for (String key : COLOR_KEYS) {
            int color = parseColor(colors.optString(key, ""));
            if (color != Integer.MIN_VALUE) { normalized.put(key, color); parsed++; }
        }
        if (parsed == 0) throw new IllegalArgumentException("colors 里没有任何可识别的 #RRGGBB 颜色");
        JSONObject style = normalizeStyle(document.optJSONObject("style"));
        String base = name.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        File target = new File(dir(context), base + ".json");
        int suffix = 2;
        while (target.exists()) target = new File(dir(context), base + "-" + (suffix++) + ".json");
        JSONObject output = new JSONObject();
        output.put("name", name);
        output.put("dark", document.optBoolean("dark", true));
        output.put("colors", normalized);
        output.put("style", style);
        File directory = target.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("无法创建主题目录");
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(output.toString().getBytes(StandardCharsets.UTF_8));
        }
        return target.getName();
    }

    public static boolean delete(Context context, String id) {
        if (!isSafeId(id)) return false;
        File file = new File(dir(context), id);
        return file.isFile() && file.delete();
    }

    /** true when the id is a plain plugin file name; blocks path traversal. */
    public static boolean isSafeId(String id) {
        if (id == null || id.isEmpty() || !id.endsWith(".json")) return false;
        if (id.contains("/") || id.contains("\\") || id.contains("..")) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c != '.' && c != '_' && c != '-' && !Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    private static Theme parse(File file) {
        if (file == null || !file.isFile() || file.length() > MAX_THEME_BYTES) return null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) body.append(buffer, 0, read);
            JSONObject document = new JSONObject(body.toString());
            String name = document.optString("name").trim();
            JSONObject raw = document.optJSONObject("colors");
            if (name.isEmpty() || raw == null) return null;
            JSONObject colors = new JSONObject();
            int parsed = 0;
            for (String key : COLOR_KEYS) {
                int color = parseColor(raw.optString(key, ""));
                if (color != Integer.MIN_VALUE) { colors.put(key, color); parsed++; }
            }
            if (parsed == 0) return null;
            return new Theme(file.getName(), name, document.optBoolean("dark", true), colors,
                normalizeStyle(document.optJSONObject("style")));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Keeps only known style knobs, clamped to safe ranges; missing entries stay absent (= defaults). */
    private static JSONObject normalizeStyle(JSONObject raw) {
        JSONObject style = new JSONObject();
        if (raw == null) return style;
        clampInto(style, raw, "cornerScale", 0.6, 2.0, false);
        clampInto(style, raw, "fontScale", 0.7, 1.6, false);
        if (raw.optBoolean("gradientAccent", false)) {
            try { style.put("gradientAccent", true); } catch (Exception ignored) { }
        }
        return style;
    }

    private static void clampInto(JSONObject out, JSONObject raw, String key, double min, double max, boolean intOnly) {
        if (!raw.has(key)) return;
        try {
            double value = raw.getDouble(key);
            if (Double.isNaN(value) || Double.isInfinite(value)) return;
            value = Math.max(min, Math.min(max, value));
            out.put(key, intOnly ? (int) Math.round(value) : value);
        } catch (Exception ignored) { }
    }

    /** Accepts #RGB / #RRGGBB / #AARRGGBB; returns Integer.MIN_VALUE when unparseable. */
    private static int parseColor(String value) {
        if (value == null) return Integer.MIN_VALUE;
        String s = value.trim();
        if (s.isEmpty() || s.charAt(0) != '#') return Integer.MIN_VALUE;
        try {
            if (s.length() == 4) { // #RGB -> #FFRRGGBB
                int r = Character.digit(s.charAt(1), 16), g = Character.digit(s.charAt(2), 16), b = Character.digit(s.charAt(3), 16);
                if (r < 0 || g < 0 || b < 0) return Integer.MIN_VALUE;
                return Color.rgb(r * 17, g * 17, b * 17);
            }
            return Color.parseColor(s);
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }
}
