package dev.nightbeam.corerpg.quests.filter;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses filter expressions. Grammar:
 * <pre>
 *   expr  := call | atom
 *   call  := name '(' [ arg (',' arg)* ] ')'
 *   arg   := call | string | atom
 *   atom  := item id ("minecraft:stone"), tag ("#c:ingots"), mod ("@create") or a comparison ("&gt;=4", "2..5", "50%")
 * </pre>
 * Example: {@code any(#c:ingots, all(@create, not(block())), food(>=6))}
 */
public final class FilterParser {
    /** A parsed argument before it is interpreted by a filter type. */
    public sealed interface Arg {
        int position();

        record Call(String name, List<Arg> args, int position) implements Arg {
        }

        record Atom(String text, int position) implements Arg {
        }

        record Str(String text, int position) implements Arg {
        }
    }

    private final String input;
    private int pos;

    private FilterParser(String input) {
        this.input = input;
    }

    public static ItemFilter parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new FilterException("The filter is empty", 0);
        }
        FilterParser parser = new FilterParser(expression);
        Arg arg = parser.parseArg();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new FilterException("Unexpected '" + parser.input.charAt(parser.pos) + "'; combine several filters with any(...) or all(...)", parser.pos);
        }
        return FilterTypes.toFilter(arg);
    }

    /** Null when the expression is valid, otherwise the error message. */
    public static @Nullable String validate(String expression) {
        try {
            parse(expression);
            return null;
        } catch (FilterException e) {
            return e.getMessage();
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Arg parseArg() {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new FilterException("Expected a filter", pos);
        }
        int start = pos;
        char c = input.charAt(pos);
        if (c == '"' || c == '\'') {
            return new Arg.Str(parseString(c), start);
        }
        int nameEnd = pos;
        while (nameEnd < input.length() && isNameChar(input.charAt(nameEnd))) {
            nameEnd++;
        }
        int after = nameEnd;
        while (after < input.length() && Character.isWhitespace(input.charAt(after))) {
            after++;
        }
        if (nameEnd > pos && after < input.length() && input.charAt(after) == '(') {
            String name = input.substring(pos, nameEnd);
            pos = after + 1;
            List<Arg> args = new ArrayList<>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ')') {
                pos++;
                return new Arg.Call(name, args, start);
            }
            while (true) {
                args.add(parseArg());
                skipWhitespace();
                if (pos >= input.length()) {
                    throw new FilterException("Missing ')' to close " + name + "(", start);
                }
                char next = input.charAt(pos);
                if (next == ',') {
                    pos++;
                } else if (next == ')') {
                    pos++;
                    return new Arg.Call(name, args, start);
                } else {
                    throw new FilterException("Expected ',' or ')' but found '" + next + "'", pos);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == ',' || ch == ')' || ch == '(') {
                break;
            }
            sb.append(ch);
            pos++;
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            throw new FilterException("Expected a filter", start);
        }
        return new Arg.Atom(text, start);
    }

    private String parseString(char quote) {
        int start = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos++);
            if (c == '\\' && pos < input.length()) {
                sb.append(input.charAt(pos++));
            } else if (c == quote) {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new FilterException("Unclosed string", start);
    }

    private static boolean isNameChar(char c) {
        return c == '_' || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** Quotes a string argument for {@link ItemFilter#toExpression()}. */
    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
