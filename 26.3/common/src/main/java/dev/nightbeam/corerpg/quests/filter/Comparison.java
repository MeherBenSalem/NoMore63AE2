package dev.nightbeam.corerpg.quests.filter;

import java.util.Locale;

/**
 * A numeric condition written as {@code >=4}, {@code <10}, {@code =3}, {@code !=0}, {@code 2..5} or a bare number
 * (meaning equal). A trailing {@code %} marks a percentage.
 */
public record Comparison(Op op, double value, double max, boolean percent) {
    public enum Op {
        EQ("="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="), RANGE("..");

        final String symbol;

        Op(String symbol) {
            this.symbol = symbol;
        }
    }

    public boolean test(double v) {
        return switch (op) {
            case EQ -> v == value;
            case NE -> v != value;
            case LT -> v < value;
            case LE -> v <= value;
            case GT -> v > value;
            case GE -> v >= value;
            case RANGE -> v >= value && v <= max;
        };
    }

    public static Comparison parse(String text, int position) {
        String s = text.trim().replace(" ", "");
        boolean percent = s.endsWith("%");
        if (percent) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            int range = s.indexOf("..");
            if (range > 0) {
                double lo = Double.parseDouble(s.substring(0, range));
                double hi = Double.parseDouble(s.substring(range + 2));
                return new Comparison(Op.RANGE, Math.min(lo, hi), Math.max(lo, hi), percent);
            }
            for (Op op : new Op[]{Op.NE, Op.LE, Op.GE, Op.LT, Op.GT}) {
                if (s.startsWith(op.symbol)) {
                    return new Comparison(op, Double.parseDouble(s.substring(op.symbol.length())), 0, percent);
                }
            }
            if (s.startsWith("==")) {
                return new Comparison(Op.EQ, Double.parseDouble(s.substring(2)), 0, percent);
            }
            if (s.startsWith("=")) {
                return new Comparison(Op.EQ, Double.parseDouble(s.substring(1)), 0, percent);
            }
            return new Comparison(Op.EQ, Double.parseDouble(s), 0, percent);
        } catch (NumberFormatException e) {
            throw new FilterException("Expected a comparison like >=4, <10, 2..5 but got '" + text.trim() + "'", position);
        }
    }

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%s", v);
    }

    @Override
    public String toString() {
        String p = percent ? "%" : "";
        if (op == Op.RANGE) {
            return num(value) + ".." + num(max) + p;
        }
        return op.symbol + num(value) + p;
    }
}
