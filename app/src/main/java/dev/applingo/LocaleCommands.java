package dev.applingo;

import java.util.*;

/** Only fixed locale commands; no arbitrary privileged shell interface. */
public final class LocaleCommands {
    public static String normalize(String tags) {
        if (tags == null) throw new IllegalArgumentException("Missing language");
        String cleaned = tags.trim();
        // Tolerate leading/trailing commas and surrounding whitespace that users often paste.
        while (cleaned.startsWith(",")) cleaned = cleaned.substring(1).trim();
        while (cleaned.endsWith(",")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        if (cleaned.isEmpty()) return "";
        if (cleaned.length() > 200) throw new IllegalArgumentException("Language list is too long");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : cleaned.split(",", -1)) {
            String t = tag.trim();
            if (t.isEmpty()) continue; // skip empty segments instead of rejecting
            if (!t.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*"))
                throw new IllegalArgumentException("Use a language tag such as en, zh-Hant-HK or ja");
            Locale locale = new Locale.Builder().setLanguageTag(t).build();
            if (locale.getLanguage().isEmpty() || locale.toLanguageTag().equals("und"))
                throw new IllegalArgumentException("Invalid language tag");
            result.add(locale.toLanguageTag());
        }
        if (result.isEmpty()) return "";
        return String.join(",", result);
    }
    public static String[] command(boolean set, String pkg, int user, String tags) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*") || user < 0)
            throw new IllegalArgumentException("Invalid package or Android user");
        // Options (--user) must come before positional args (package, locale tag).
        // set-app-locales takes the locale as a positional argument; --reset clears the override.
        ArrayList<String> args = new ArrayList<>(Arrays.asList("/system/bin/cmd", "locale",
            set ? "set-app-locales" : "get-app-locales", "--user", Integer.toString(user), pkg));
        if (set) {
            String normalized = normalize(tags);
            args.add(normalized.isEmpty() ? "--reset" : normalized);
        }
        return args.toArray(new String[0]);
    }
    public static String shell(String[] args) {
        StringJoiner join = new StringJoiner(" ");
        for (String arg : args) join.add("'" + arg.replace("'", "'\\''") + "'");
        return join.toString();
    }
    public static String parse(String output) {
        if (output == null) throw new IllegalStateException("Could not verify Android's language setting: empty response");
        // Extract the bracketed locale list without depending on the surrounding English phrasing,
        // which OEMs and non-English ROMs may reword or translate.
        int open = output.indexOf('[');
        int close = output.lastIndexOf(']');
        if (open < 0 || close <= open)
            throw new IllegalStateException("Could not verify Android's language setting: " + output);
        String inside = output.substring(open + 1, close).trim();
        if (inside.isEmpty()) return "";
        return normalize(inside);
    }
}
