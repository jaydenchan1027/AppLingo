package dev.applingo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class CommandRunner {
    private static final String EXIT_MARKER = "APPLINGO_CMD_DONE_";

    static String run(String[] args, boolean root) throws Exception {
        if (!root) {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            return drain(process, 25);
        }
        // Root mode: drive an interactive `su` shell. This works across
        // Magisk, KernelSU, SuperSU and other managers without depending on
        // the `su -c` flag, whose behaviour varies between implementations.
        Process process = new ProcessBuilder("su").redirectErrorStream(true).start();
        String marker = EXIT_MARKER + System.nanoTime();
        try (OutputStream stdin = process.getOutputStream()) {
            String script = LocaleCommands.shell(args) + "\necho " + marker + "$?\nexit 0\n";
            stdin.write(script.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }
        String raw = drain(process, 25);
        return parseInteractiveOutput(raw, marker);
    }

    private static String drain(Process process, int timeoutSeconds) throws Exception {
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<String> output = reader.submit(() -> {
            try (InputStream in = process.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 65536) throw new IOException("Command output too large");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        });
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS))
                throw new IOException("Timed out. Check the root permission prompt or reconnect Shizuku.");
            String result = output.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0)
                throw new IOException(result.trim().isEmpty() ? "Android refused the command" : result.trim());
            return result;
        } finally { process.destroy(); output.cancel(true); reader.shutdownNow(); }
    }

    /**
     * Interactive su output may contain shell banners or prompts. The marker
     * line carries the real exit code of the last command; everything before
     * it is the command's actual stdout/stderr.
     */
    private static String parseInteractiveOutput(String raw, String marker) throws Exception {
        int pos = raw.lastIndexOf(marker);
        if (pos < 0) {
            // Marker absent (e.g. su was denied and the shell never ran).
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) throw new IOException("Root shell produced no output.");
            throw new IOException(trimmed);
        }
        String before = raw.substring(0, pos).trim();
        String tail = raw.substring(pos + marker.length()).trim();
        String codeStr = tail.split("\\s+")[0];
        int code;
        try { code = Integer.parseInt(codeStr); } catch (NumberFormatException e) { code = 0; }
        if (code != 0) throw new IOException(before.isEmpty() ? "Android refused the command" : before);
        return before;
    }

    static String get(String pkg, int user, boolean root) throws Exception {
        return LocaleCommands.parse(run(LocaleCommands.command(false, pkg, user, ""), root));
    }
    static String set(String pkg, int user, String tags, boolean root) throws Exception {
        String desired = LocaleCommands.normalize(tags);
        run(LocaleCommands.command(true, pkg, user, desired), root);
        String actual = get(pkg, user, root);
        if (!desired.equals(actual)) throw new IOException("Android did not retain the requested language. Read back: " + actual);
        return actual;
    }
    /** Raw output of `cmd user list`, used to discover work profiles and secondary users. */
    static String listUsers(boolean root) throws Exception {
        return run(new String[]{"/system/bin/cmd", "user", "list"}, root);
    }
}
