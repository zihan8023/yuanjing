import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Source-text assertions for the OpenCode Desktop host (runs against the Gradle layout). */
public final class OpenCodeDesktopStructureTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(Path root, String relative) throws Exception {
        return new String(Files.readAllBytes(root.resolve(relative)), StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String base = "app/src/main/";
        String manifest = read(root, base + "AndroidManifest.xml");
        String runtime = read(root, base + "java/com/iqge/opencode/OpenCodeRuntime.java");
        String bridge = read(root, base + "java/com/iqge/opencode/OpenCodeInputBridge.java");
        String session = read(root, base + "java/com/iqge/opencode/OpenCodeSession.java");
        String activity = read(root, base + "java/com/iqge/opencode/OpenCodeActivity.java");
        String main = read(root, base + "java/com/iqge/MainActivity.java");

        require(manifest.contains("com.iqge.opencode.OpenCodeActivity")
                && manifest.contains("android:name=\"com.iqge.opencode.OpenCodeActivity\""),
            "OpenCodeActivity must be a declared, non-exported activity");

        require(runtime.contains("opencode-desktop-linux-arm64.deb")
                && runtime.contains("dpkg-deb -x")
                && runtime.contains("LD_LIBRARY_PATH")
                && runtime.contains("/usr/lib/aarch64-linux-gnu"),
            "OpenCode must install the official linux-arm64 deb and resolve the Debian glibc libraries");

        require(runtime.contains("desktop.bin") && runtime.contains("ai.opencode.desktop")
                && runtime.contains("glibc-runner")
                && runtime.contains("--ozone-platform=x11")
                && runtime.contains("--no-sandbox"),
            "Desktop must launch through glibc-runner on the X11 ozone platform");

        require(runtime.contains("mkfifo") && runtime.contains("input.fifo")
                && runtime.contains("done < ")
                && runtime.contains("inputdaemon.sh"),
            "input pump must read a private FIFO and be daemonized so execute() returns");

        require(runtime.contains("pkill -f 'ai.opencode.desktop'")
                && runtime.contains("kill -TERM"),
            "stop script must tear down the desktop and its process group");

        require(bridge.contains("xdotool type --clearmodifiers --delay 12 --file")
                && bridge.contains("xclip -selection clipboard -i < '")
                && !bridge.contains("typeText(String text) { send(\"xdotool type \""),
            "typed text and clipboard content must travel via files, never inline on the command line");

        require(bridge.contains("xdotool mousemove") && bridge.contains("mousedown")
                && bridge.contains("mouseup") && bridge.contains("(up ? 4 : 5)"),
            "pointer gestures must map to real X11 button events");

        require(session.contains("OpenCodeRuntime.provision(")
                && session.contains("OpenCodeRuntime.startAll(")
                && session.contains("OpenCodeRuntime.stopAll("),
            "session must drive provision -> startAll -> stopAll");

        require(activity.contains("com.termux.x11")
                && activity.contains("setComponent(new ComponentName(X11_PACKAGE, X11_ACTIVITY))"),
            "activity must surface the real desktop by launching the Termux:X11 renderer");

        require(main.contains("new SlashCommand(\"/opencode\"")
                && main.contains("case \"/opencode\": startActivity(new Intent(this, com.iqge.opencode.OpenCodeActivity.class))"),
            "MainActivity must expose the /opencode entry point");

        System.out.println("OpenCodeDesktopStructureTest PASS");
    }
}
