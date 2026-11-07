package dev.nightbeam.corerpg.quests.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quest text: {@code &} colour and format codes (as in "&6Gold &lbold"), {@code \&} for a literal ampersand, and a
 * {@code lang:} prefix that makes the whole string a translation key so packs can ship translations.
 */
public final class TextUtil {
    public static final String PAGE_BREAK = "{@pagebreak}";
    private static final Pattern CODES = Pattern.compile("(?<!\\\\)&([0-9a-fk-orA-FK-OR])");
    private static final Pattern SECTION_CODES = Pattern.compile("§[0-9a-fk-orA-FK-OR]");

    private TextUtil() {
    }

    public static MutableComponent format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.startsWith("lang:")) {
            return Component.translatable(raw.substring(5).trim());
        }
        return Component.literal(toSection(raw));
    }

    /** Converts {@code &} codes to section signs, which the font renderer understands. */
    public static String toSection(String raw) {
        String s = CODES.matcher(raw).replaceAll(m -> "§" + m.group(1).toLowerCase(Locale.ROOT));
        return s.replace("\\&", "&");
    }

    /** Text without formatting codes, for searching and sorting. */
    public static String plain(String raw) {
        if (raw == null) {
            return "";
        }
        return SECTION_CODES.matcher(toSection(raw)).replaceAll("");
    }

    public static boolean isBlank(List<String> lines) {
        for (String line : lines) {
            if (!line.isBlank()) {
                return false;
            }
        }
        return true;
    }

    /** "1h 5m", "42s", for cooldowns. */
    public static String duration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        long d = seconds / 86400;
        long h = seconds % 86400 / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append("d ");
        }
        if (h > 0) {
            sb.append(h).append("h ");
        }
        if (m > 0 && d == 0) {
            sb.append(m).append("m ");
        }
        if (s > 0 && d == 0 && h == 0) {
            sb.append(s).append("s ");
        }
        return sb.toString().trim();
    }

    /** "1,234", "12.5k", "3.2M", for task progress. */
    public static String compact(long value) {
        if (value < 10_000) {
            return String.format(Locale.ROOT, "%,d", value);
        }
        if (value < 1_000_000) {
            return trim(value / 1000.0) + "k";
        }
        if (value < 1_000_000_000) {
            return trim(value / 1_000_000.0) + "M";
        }
        return trim(value / 1_000_000_000.0) + "B";
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}
