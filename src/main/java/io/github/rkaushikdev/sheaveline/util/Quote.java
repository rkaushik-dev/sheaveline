package io.github.rkaushikdev.sheaveline.util;

/**
 * Text quoted the way a refusal shows what it was given.
 *
 * <p>Single quotes round it, or double quotes when the text holds a single quote and no double
 * one, so {@code 'nine'} and {@code "o'clock"} both read naturally. A backslash, the quote in use,
 * and the control characters are escaped, so what was typed and what is shown cannot be confused.
 */
public final class Quote {

    private Quote() {}

    /**
     * Quote a piece of text for a message.
     *
     * @param text the text, exactly as it arrived
     * @return the text in quotes, escaped where it has to be
     */
    public static String of(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(quote);
        text.codePoints().forEach(point -> escape(out, point, quote));
        return out.append(quote).toString();
    }

    private static void escape(StringBuilder out, int point, char quote) {
        switch (point) {
            case '\\' -> out.append("\\\\");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> {
                if (point == quote) {
                    out.append('\\').append(quote);
                } else if (point < 0x20 || point == 0x7f) {
                    out.append(String.format(java.util.Locale.ROOT, "\\x%02x", point));
                } else if (!printable(point)) {
                    String shape = point < 0x100 ? "\\x%02x" : point <= 0xffff ? "\\u%04x" : "\\U%08x";
                    out.append(String.format(java.util.Locale.ROOT, shape, point));
                } else {
                    out.appendCodePoint(point);
                }
            }
        }
    }

    private static boolean printable(int point) {
        if (point == ' ') {
            return true;
        }
        return switch (Character.getType(point)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR -> false;
            default -> true;
        };
    }
}
