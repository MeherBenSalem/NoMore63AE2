package dev.nightbeam.corerpg.quests.util;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Quest object ids: lower-case, 1-64 characters of {@code a-z 0-9 _ - . /}. Hand-written books can use readable ids
 * ("act1/kindle"); the editor generates eight random hex digits.
 */
public final class Ids {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_./-]{1,64}");

    private Ids() {
    }

    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    /** Turns free text into a valid id ("The Armory!" becomes "the_armory"). */
    public static String sanitize(String text) {
        String s = text.toLowerCase(Locale.ROOT).replaceAll("&[0-9a-fk-or]", "").replaceAll("[^a-z0-9_./-]+", "_");
        s = s.replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (s.isEmpty()) {
            s = "id";
        }
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    public static String random() {
        return String.format(Locale.ROOT, "%08x", ThreadLocalRandom.current().nextInt());
    }

    /** A random id that is not taken yet. */
    public static String unique(Predicate<String> taken) {
        for (int i = 0; i < 1000; i++) {
            String id = random();
            if (!taken.test(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Could not find a free id");
    }

    /** {@code base}, or {@code base_2}, {@code base_3}... when taken. */
    public static String uniqueFrom(String base, Predicate<String> taken) {
        String clean = sanitize(base);
        if (!taken.test(clean)) {
            return clean;
        }
        for (int i = 2; i < 10000; i++) {
            String suffix = "_" + i;
            String candidate = (clean.length() + suffix.length() > 64 ? clean.substring(0, 64 - suffix.length()) : clean) + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        return unique(taken);
    }
}
