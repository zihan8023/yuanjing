import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Regression: binary-only ZCode packaging, integration, and decoded gateway/catalog. */
public class ZcodeStructureTest {
    static int fail = 0;
    static final String PROVIDER = "com/termux/app/iqcode/api/ZcodePlanProvider";
    static final String VAULT = "com/termux/app/iqcode/api/zcode/ZcodeVault";
    static final String[] SECRETS = {"zcode.z.ai", "/api/v1/zcode-plan", "anthropic/v1/messages"};

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) fail++;
    }

    static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    static void checkPlaintext(String name, String contents) {
        for (String secret : SECRETS) {
            check("no plaintext '" + secret + "' in " + name, !contents.contains(secret));
        }
    }

    static boolean implementationClass(String name) {
        // Allow nested implementation classes without pinning their private names.
        return name.equals(PROVIDER + ".class") || name.startsWith(PROVIDER + "$")
                || name.equals(VAULT + ".class") || name.startsWith(VAULT + "$");
    }

    static void checkJar(Path jarPath) {
        check("binary ZCode jar exists", Files.isRegularFile(jarPath));
        if (!Files.isRegularFile(jarPath)) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            check("jar contains public provider class", jar.getJarEntry(PROVIDER + ".class") != null);
            check("jar contains public vault class", jar.getJarEntry(VAULT + ".class") != null);
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String lower = name.toLowerCase(Locale.ROOT);
                check("no Java/Kotlin source entry: " + name,
                        !lower.endsWith(".java") && !lower.endsWith(".kt")
                                && !lower.endsWith(".kts") && !lower.endsWith(".kotlin"));
                if (!lower.endsWith(".class")) continue;
                check("no bundled dependency class: " + name, implementationClass(name));
                // ASCII constants/attribute names survive this byte-preserving decoding.
                // Do not resolve the provider: Android and app dependencies are not on the test classpath.
                String bytes;
                try (java.io.InputStream in = jar.getInputStream(entry)) {
                    bytes = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
                }
                checkPlaintext(name, bytes);
                check("no source line/local-variable debug metadata: " + name,
                        !bytes.contains("LineNumberTable") && !bytes.contains("LocalVariableTable"));
                check("no Java/Kotlin source filename: " + name,
                        !bytes.contains(".java") && !bytes.contains(".kt"));
            }
        } catch (Exception e) {
            check("jar packaging (" + e + ")", false);
        }
    }

    public static void main(String[] args) throws Exception {
        String catalog = read("app/src/main/java/com/termux/app/iqcode/api/ModelCatalogClient.java");
        String providers = read("app/src/main/java/com/termux/app/iqcode/api/ModelProviders.java");
        String resolver = read("app/src/main/java/com/termux/app/iqcode/api/ApiEndpointResolver.java");
        String activity = read("app/src/main/java/com/iqge/MainActivity.java");
        String gradle = read("app/build.gradle");
        Path jarPath = Paths.get("app/libs/iqcode-zcode.jar").toAbsolutePath().normalize();

        check("Gradle includes binary ZCode dependency", java.util.regex.Pattern.compile(
                "implementation\\s+files\\s*\\(\\s*['\"]libs/iqcode-zcode\\.jar['\"]\\s*\\)")
                .matcher(gradle).find());
        check("provider Java source absent", !Files.exists(Paths.get("app/src/main/java/" + PROVIDER + ".java")));
        check("vault Java source absent", !Files.exists(Paths.get("app/src/main/java/" + VAULT + ".java")));
        checkJar(jarPath);

        String[][] files = {
            {"ModelCatalogClient.java", catalog}, {"ModelProviders.java", providers},
            {"ApiEndpointResolver.java", resolver}, {"MainActivity.java", activity},
        };
        for (String[] file : files) checkPlaintext(file[0], file[1]);

        // Keep integration checks against the source that still belongs to the app.
        check("ModelProviders registers zcode", providers.contains("\"zcode\"") && providers.contains("ZcodePlanProvider"));
        check("catalog zcode branch", catalog.contains("\"zcode\""));
        check("resolver zcode empty endpoint", resolver.contains("\"zcode\""));
        check("activity offers ZCode option", activity.contains("\"zcode\"") && activity.contains("ZCode 授权码"));

        // Only platform classes may come from the parent; a stale app/test classpath vault cannot win.
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jarPath.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> vaultClass = Class.forName(VAULT.replace('/', '.'), true, loader);
            check("vault loaded by isolated jar loader", vaultClass.getClassLoader() == loader);
            check("vault loaded from iqcode-zcode.jar", Files.isSameFile(jarPath, Paths.get(
                    vaultClass.getProtectionDomain().getCodeSource().getLocation().toURI())));
            String base = (String) vaultClass.getMethod("gatewayBase").invoke(null);
            String path = (String) vaultClass.getMethod("messagesPath").invoke(null);
            check("decoded gateway URL exact", ("https://zcode.z.ai/api/v1/zcode-plan/anthropic/v1/messages").equals(base + path));
            String billingPath = (String) vaultClass.getMethod("billingBalancePath").invoke(null);
            check("decoded billing balance path", billingPath.startsWith("/billing/balance?app_version={v}&platform={p}"));
            String[] pairs = (String[]) vaultClass.getMethod("modelPairs").invoke(null);
            check("decoded model catalog starts with glm id", pairs.length >= 2 && pairs[0].startsWith("glm-") && !pairs[1].startsWith("glm-"));
        } catch (Throwable t) {
            check("vault decodes (" + t + ")", false);
        }

        if (fail > 0) { System.out.println("FAILED: " + fail); System.exit(1); }
        System.out.println("ALL PASS");
    }
}
