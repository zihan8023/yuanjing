package com.termux.app.iqcode.api.deepseek;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PoW 求解器：DeepSeek 的 DeepSeekHashV1 由官方 sha3 wasm 实现，算法随上游更新而变，
 * 纯 Java 逆向会漂移。这里把同一份 wasm（内置 assets，后台从 CDN 刷新缓存）放进
 * WebView 的 WebAssembly 引擎里执行，与 wasm-bindgen 调用约定逐字节一致。
 */
@SuppressLint("SetJavaScriptEnabled")
public final class DeepSeekPowSolver {
    private static final Object LOCK = new Object();
    private static volatile WebView webView;
    private static volatile boolean wasmReady;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DeepSeekPowSolver() { }

    /** 计算 completion/upload 的 X-Ds-Pow-Response header。阻塞，须在 worker 线程调用。 */
    public static String solveHeader(Context context, DeepSeekRestClient client, String token, String targetPath) throws Exception {
        JSONObject challenge = client.createPowChallenge(token, targetPath);
        if (!"DeepSeekHashV1".equals(challenge.optString("algorithm", ""))) {
            throw new DeepSeekRestClient.ApiError(-1, "", "不支持的 PoW 算法: " + challenge.optString("algorithm"));
        }
        long answer = solve(context, challenge.optString("challenge", ""),
            challenge.optString("salt", ""), challenge.optLong("expire_at", 0L), challenge.optDouble("difficulty", 0));
        JSONObject header = new JSONObject();
        header.put("algorithm", "DeepSeekHashV1");
        header.put("challenge", challenge.optString("challenge"));
        header.put("salt", challenge.optString("salt"));
        header.put("answer", answer);
        header.put("signature", challenge.optString("signature"));
        header.put("target_path", challenge.optString("target_path"));
        return Base64.getEncoder().encodeToString(header.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static long solve(Context context, String challenge, String salt, long expireAt, double difficulty) throws Exception {
        synchronized (LOCK) {
            ensureWebView(context);
            ensureWasm(context);
            String prefix = salt + "_" + expireAt + "_";
            StringBuilder args = new StringBuilder();
            args.append("{\"challenge\":\"").append(challenge).append('"');
            args.append(",\"prefix\":\"").append(prefix).append('"');
            args.append(",\"difficulty\":").append(difficulty).append('}');
            final AtomicReference<String> result = new AtomicReference<>(null);
            final CountDownLatch done = new CountDownLatch(1);
            MAIN.post(() -> webView.evaluateJavascript("window.__dsPowSolve(" + args + ")", v -> {
                result.set(v);
                done.countDown();
            }));
            if (!done.await(30, TimeUnit.SECONDS)) throw new DeepSeekRestClient.ApiError(-1, "", "PoW 计算超时");
            String raw = result.get();
            if (raw == null || "null".equals(raw)) throw new DeepSeekRestClient.ApiError(-1, "", "PoW 求解失败：wasm 未初始化");
            JSONObject out = new JSONObject(raw);
            if (out.optInt("status", 0) != 1) throw new DeepSeekRestClient.ApiError(-1, "", "PoW 求解失败：wasm 无解");
            return (long) Math.round(out.optDouble("answer", Double.NaN));
        }
    }

    private static void ensureWebView(Context context) throws InterruptedException {
        if (webView != null) return;
        final CountDownLatch created = new CountDownLatch(1);
        MAIN.post(() -> {
            if (webView == null) {
                WebView w = new WebView(context.getApplicationContext());
                w.getSettings().setJavaScriptEnabled(true);
                w.getSettings().setAllowFileAccess(false);
                webView = w;
            }
            created.countDown();
        });
        if (!created.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("PoW WebView 初始化失败");
    }

    /** 加载 wasm：优先 files/ds_pow/sha3.wasm（CDN 刷新缓存），回退内置 assets。 */
    private static void ensureWasm(Context context) throws Exception {
        if (wasmReady) return;
        byte[] wasm = null;
        File cached = new File(context.getFilesDir(), "ds_pow/sha3.wasm");
        if (cached.isFile() && cached.length() > 1000) {
            wasm = readAll(new FileInputStream(cached));
        }
        if (wasm == null) {
            try (InputStream in = context.getAssets().open("ds_pow/sha3_wasm_bg.wasm")) {
                wasm = readAll(in);
            }
        }
        if (wasm == null || wasm.length < 1000) throw new IllegalStateException("PoW wasm 缺失");
        String b64 = Base64.getEncoder().encodeToString(wasm);
        // instantiate 是异步 promise：先装 loader，再轮询 __dsPowImpl 就绪
        final CountDownLatch loaded = new CountDownLatch(1);
        MAIN.post(() -> webView.evaluateJavascript(jsLoader(b64), v -> loaded.countDown()));
        loaded.await(30, TimeUnit.SECONDS);
        for (int i = 0; i < 150; i++) {
            final AtomicReference<String> ready = new AtomicReference<>(null);
            final CountDownLatch probed = new CountDownLatch(1);
            MAIN.post(() -> webView.evaluateJavascript("typeof window.__dsPowImpl==='function'", v -> {
                ready.set(v);
                probed.countDown();
            }));
            probed.await(5, TimeUnit.SECONDS);
            if ("true".equals(ready.get())) break;
            if (i == 149) throw new DeepSeekRestClient.ApiError(-1, "", "PoW wasm 初始化失败");
            Thread.sleep(100);
        }
        wasmReady = true;
        refreshFromCdnAsync(context, wasm.length);
    }

    /** CDN 刷新为后台任务：拉到不同长度的新 wasm 则写缓存，下次会话生效。 */
    private static void refreshFromCdnAsync(Context context, int currentLen) {
        Thread refresher = new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(DeepSeekRestClient.DEFAULT_WASM_URL).openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(20_000);
                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    byte[] fresh = readAll(conn.getInputStream());
                    if (fresh.length > 1000 && fresh.length != currentLen) {
                        File target = new File(context.getFilesDir(), "ds_pow/sha3.wasm");
                        target.getParentFile().mkdirs();
                        try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                            out.write(fresh);
                        }
                    }
                }
            } catch (Exception ignored) { }
        }, "ds-pow-refresh");
        refresher.setDaemon(true);
        refresher.start();
    }

    private static String jsLoader(String b64) {
        return "(function(){var raw=Uint8Array.from(atob('" + b64 + "'),function(c){return c.charCodeAt(0);});"
            + "window.__dsPowSolve=function(o){return window.__dsPowImpl?window.__dsPowImpl(o):{status:0,answer:0};};"
            + "WebAssembly.instantiate(raw,{}).then(function(r){var e=r.instance.exports;"
            + "var enc=new TextEncoder();"
            + "function ws(s){var b=enc.encode(s);var p=e.__wbindgen_export_0(b.length,1);new Uint8Array(e.memory.buffer).set(b,p);return [p,b.length];}"
            + "window.__dsPowImpl=function(o){"
            + "var rp=e.__wbindgen_add_to_stack_pointer(-16);"
            + "var pc=ws(o.challenge),pp=ws(o.prefix);"
            + "e.wasm_solve(rp,pc[0],pc[1],pp[0],pp[1],o.difficulty);"
            + "var st=new Int32Array(e.memory.buffer,rp,1)[0];"
            + "var val=new Float64Array(e.memory.buffer,rp+8,1)[0];"
            + "e.__wbindgen_add_to_stack_pointer(16);"
            + "return {status:st,answer:val};};"
            + "}).catch(function(err){window.__dsPowError=String(err);});})();";
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try (InputStream closable = in) {
            while ((n = closable.read(buf)) > 0) out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
