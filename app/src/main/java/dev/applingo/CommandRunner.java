package dev.applingo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

final class CommandRunner {
    static String run(String[] args, boolean root) throws Exception {
        Process process = new ProcessBuilder(root ? new String[]{"su", "-c", LocaleCommands.shell(args)} : args)
                .redirectErrorStream(true).start();
        ExecutorService reader = Executors.newSingleThreadExecutor();
        Future<String> output = reader.submit(() -> {
            try (InputStream in = process.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 65536) throw new IOException("Command output too large");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name()).trim();
            }
        });
        try {
            if (!process.waitFor(25, TimeUnit.SECONDS)) throw new IOException("Timed out. Check the root permission prompt or reconnect Shizuku.");
            String result = output.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0) throw new IOException(result.isEmpty() ? "Android refused the command" : result);
            return result;
        } finally { process.destroy(); output.cancel(true); reader.shutdownNow(); }
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
