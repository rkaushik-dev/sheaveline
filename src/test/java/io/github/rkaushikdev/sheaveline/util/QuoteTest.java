package io.github.rkaushikdev.sheaveline.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.plant.GripType;
import org.junit.jupiter.api.Test;

class QuoteTest {

    @Test
    void plainTextGoesInSingleQuotes() {
        assertThat(Quote.of("nine")).isEqualTo("'nine'");
        assertThat(Quote.of("")).isEqualTo("''");
    }

    @Test
    void aSingleQuoteInsideSwitchesToDoubleQuotes() {
        assertThat(Quote.of("o'clock")).isEqualTo("\"o'clock\"");
    }

    @Test
    void bothQuotesInsideKeepSingleQuotesAndEscapeTheSingle() {
        assertThat(Quote.of("it's \"big\"")).isEqualTo("'it\\'s \"big\"'");
    }

    @Test
    void controlCharactersAndBackslashesAreEscaped() {
        assertThat(Quote.of("a\\b")).isEqualTo("'a\\\\b'");
        assertThat(Quote.of("one\ntwo\tthree\r")).isEqualTo("'one\\ntwo\\tthree\\r'");
        assertThat(Quote.of("\u0007")).isEqualTo("'\\x07'");
        assertThat(Quote.of(Character.toString(0xa0))).isEqualTo("'\\xa0'");
        assertThat(Quote.of(Character.toString(0x2028))).isEqualTo("'\\u2028'");
    }

    @Test
    void printableLettersFromAnyScriptStandAsTheyAre() {
        assertThat(Quote.of("Zürich")).isEqualTo("'Zürich'");
    }

    @Test
    void anEnumIsLookedUpByTheWordsItIsWrittenAs() {
        assertThat(Valued.lookup(GripType.class, "detachable")).contains(GripType.DETACHABLE);
        assertThat(Valued.lookup(GripType.class, "DETACHABLE")).isEmpty();
    }
}
