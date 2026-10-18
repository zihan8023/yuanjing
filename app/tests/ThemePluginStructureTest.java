import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Theme-plugin system must stay declarative JSON: no executable plugin code, no path traversal. */
public final class ThemePluginStructureTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String base = "app/src/main/";
        String store = read(root, base + "java/com/iqge/ThemePluginStore.java");
        String main = read(root, base + "java/com/iqge/MainActivity.java");

        require(store.contains("public static final String[] COLOR_KEYS")
                && store.contains("\"bg\"") && store.contains("\"accent\"") && store.contains("\"terminalBg\""),
            "store must declare the full palette key set");

        require(store.contains(".iq/themes") && store.contains("isSafeId")
                && store.contains("!id.endsWith(\".json\")") && store.contains("id.contains(\"..\")"),
            "themes live under home/.iq/themes and ids are sanitized against traversal");

        require(store.contains("STYLE_KEYS") && store.contains("cornerScale") && store.contains("fontScale")
                && store.contains("gradientAccent") && store.contains("normalizeStyle"),
            "plugin style knobs (corner/font scale, gradient accent) must be known, clamped values");

        require(store.contains("ACTION") == false && !store.contains("javax.script")
                && !store.contains("loadDex") && !store.contains("eval("),
            "plugins are declarative JSON only: no script engine or dex loading");

        require(store.contains("importFrom(Context context, Uri uri)") && store.contains("64 KB"),
            "SAF import is wired and size-capped");

        require(main.contains("applyPluginTheme(String id)")
                && main.contains("requested.startsWith(\"plugin:\")")
                && main.contains("uiTheme=\"plugin:\"+id"),
            "applyUiPalette must resolve plugin:<id> and record it as the active theme");

        require(main.contains("uiFontScale") && main.contains("uiCornerScale") && main.contains("uiGradientAccent")
                && main.contains("t.setTextSize(sp*uiFontScale)")
                && main.contains("radiusDp*uiCornerScale*getResources().getDisplayMetrics().density"),
            "plugin style must drive global text size, corner radii and accent gradients through the shared helpers");

        require(main.contains("ThemePluginStore.load(this,selectedTheme.substring(7).trim())")
                && main.contains("themePlugins=ThemePluginStore.list(this)"),
            "settings must validate plugin ids and list installed themes in the picker");

        require(main.contains("launchThemeImport()") && main.contains("REQ_IMPORT_THEME")
                && main.contains("ThemePluginStore.importFrom(this,data.getData())"),
            "settings must offer SAF import and apply the imported theme");

        System.out.println("ThemePluginStructureTest PASS");
    }
}
