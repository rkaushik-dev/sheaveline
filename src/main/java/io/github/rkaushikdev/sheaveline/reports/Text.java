package io.github.rkaushikdev.sheaveline.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The character arithmetic a printed page is laid out with.
 *
 * <p>Widths are counted in characters as the page shows them, so a character outside the basic
 * plane takes one column and not the two {@code char}s it is stored in. White space is anything
 * Unicode calls a space or a line break, the no-break spaces, the information separators and the
 * next-line control included, so a pasted narrative breaks and trims the same however it was typed.
 */
final class Text {

    private static final int NEXT_LINE = 0x85;

    private Text() {}

    /**
     * How many columns the text takes.
     *
     * @param text the text
     * @return its length in characters
     */
    static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * The first characters of the text.
     *
     * @param text the text
     * @param count how many characters to keep
     * @return at most that many characters from the start, nothing when the count is not positive
     */
    static String head(String text, int count) {
        if (count <= 0) {
            return "";
        }
        if (length(text) <= count) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, count));
    }

    /**
     * The text with spaces after it to make up the width; text already as wide is left alone.
     *
     * @param text the text
     * @param width the columns to fill
     * @return the text, left aligned
     */
    static String padRight(String text, int width) {
        int spare = width - length(text);
        return spare > 0 ? text + " ".repeat(spare) : text;
    }

    /**
     * The text with spaces before it to make up the width; text already as wide is left alone.
     *
     * @param text the text
     * @param width the columns to fill
     * @return the text, right aligned
     */
    static String padLeft(String text, int width) {
        int spare = width - length(text);
        return spare > 0 ? " ".repeat(spare) + text : text;
    }

    /**
     * The text in the middle of the width. When the spare columns do not halve, the odd one goes on
     * the left in an odd width and on the right in an even one.
     *
     * @param text the text
     * @param width the columns to fill
     * @return the text, centred
     */
    static String centre(String text, int width) {
        int spare = width - length(text);
        if (spare <= 0) {
            return text;
        }
        int left = spare / 2 + (spare & width & 1);
        return " ".repeat(left) + text + " ".repeat(spare - left);
    }

    /**
     * Whether a character is white space on a page.
     *
     * @param point the character
     * @return true for spaces of every kind, tabs, line and paragraph breaks and the separators
     */
    static boolean isSpace(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point) || point == NEXT_LINE;
    }

    /**
     * The words of the text: the runs between white space.
     *
     * @param text the text
     * @return the words in order, none for blank text
     */
    static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        int start = -1;
        int at = 0;
        while (at < text.length()) {
            int point = text.codePointAt(at);
            if (isSpace(point)) {
                if (start >= 0) {
                    words.add(text.substring(start, at));
                    start = -1;
                }
            } else if (start < 0) {
                start = at;
            }
            at += Character.charCount(point);
        }
        if (start >= 0) {
            words.add(text.substring(start));
        }
        return words;
    }

    /**
     * The text without the white space at its end.
     *
     * @param text the text
     * @return the text up to its last character that is not white space
     */
    static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0) {
            int point = text.codePointBefore(end);
            if (!isSpace(point)) {
                break;
            }
            end -= Character.charCount(point);
        }
        return text.substring(0, end);
    }

    /**
     * A value as it is printed in a cell or after a label.
     *
     * @param value the value; an empty {@link Optional} is nothing and a present one is its content
     * @return the empty string for nothing, the value's own text otherwise
     */
    static String of(Object value) {
        if (value instanceof Optional<?> maybe) {
            return maybe.map(Text::of).orElse("");
        }
        return value.toString();
    }
}
