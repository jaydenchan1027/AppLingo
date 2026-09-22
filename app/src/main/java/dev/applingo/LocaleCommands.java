package dev.applingo;

import java.util.*;
import java.util.regex.*;

/** Only fixed locale commands; no arbitrary privileged shell interface. */
public final class LocaleCommands {
    public static String normalize(String tags) {
        if (tags == null) throw new IllegalArgumentException("Missing language");
        if (tags.isEmpty()) return "";
        if (tags.length() > 200) throw new IllegalArgumentException("Language list is too long");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String tag : tags.split(",", -1)) {
            if (!tag.matches("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*"))
                throw new IllegalArgumentException("Use a language tag such as en, zh-Hant-HK or ja");
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            if (locale.getLanguage().isEmpty() || locale.toLanguageTag().equals("und"))
                throw new IllegalArgumentException("Invalid language tag");
            result.add(locale.toLanguageTag());
        }
        return String.join(",", result);
    }
    public static String[] command(boolean set, String pkg, int user, String tags) {
        if (pkg == null || !pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*") || user < 0)
            throw new IllegalArgumentException("Invalid package or Android user");
        ArrayList<String> args = new ArrayList<>(Arrays.asList("/system/bin/cmd", "locale",
            set ? "set-app-locales" : "get-app-locales", pkg, "--user", Integer.toString(user)));
        if (set && !normalize(tags).isEmpty()) { args.add("--locales"); args.add(normalize(tags)); }
        // Omitting --locales explicitly resets the override to the system default.
        return args.toArray(new String[0]);
    }
    public static String shell(String[] args) {
        StringJoiner join = new StringJoiner(" ");
        for (String arg : args) join.add("'" + arg.replace("'", "'\\''") + "'");
        return join.toString();
    }
    public static String parse(String output) {
        Matcher m = Pattern.compile("(?m)^Locales for .* are \\[(.*)\\]\\s*$").matcher(output);
        if (!m.find()) throw new IllegalStateException("Could not verify Android's language setting: " + output);
        return normalize(m.group(1));
    }
}
