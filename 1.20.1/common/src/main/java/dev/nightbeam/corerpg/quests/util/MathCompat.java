package dev.nightbeam.corerpg.quests.util;

/** Minecraft 1.20.1 edition (Java 17): the Java 21 {@code Math.clamp} overloads this mod uses. */
public final class MathCompat {
    private MathCompat() {
    }

    public static int clamp(long value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }
}
