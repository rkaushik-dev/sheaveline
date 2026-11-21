package io.github.rkaushikdev.sheaveline.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalTest {

    private final StringWriter stream = new StringWriter();

    private Journal book(Level level) {
        return new Journal(stream, level, "2025-01-15T07:00:00");
    }

    private Journal book() {
        return book(Level.INFO);
    }

    private List<String> lines() {
        return Arrays.stream(stream.toString().split("\n"))
                .filter(one -> !one.isEmpty())
                .toList();
    }

    @Test
    void aLineCarriesTheStampTheLevelAndTheEvent() {
        book().info("started");
        assertThat(lines()).containsExactly("2025-01-15T07:00:00 info started");
    }

    @Test
    void theFieldsGoOnInTheOrderTheyWereGiven() {
        book().info("request", "method", "GET", "path", "/lifts", "status", 200);
        assertThat(lines().get(0)).endsWith("request method=GET path=/lifts status=200");
    }

    @Test
    void aValueWithASpaceInItIsQuoted() {
        assertThat(Journal.field("who", "R Skaggs")).isEqualTo("who=\"R Skaggs\"");
        assertThat(Journal.field("who", "Skaggs")).isEqualTo("who=Skaggs");
    }

    @Test
    void anEmptyValueIsQuotedSoTheFieldDoesNotVanish() {
        assertThat(Journal.field("note", "")).isEqualTo("note=\"\"");
        assertThat(Journal.field("note", null)).isEqualTo("note=\"\"");
    }

    @Test
    void aValueWithAQuoteInItIsEscaped() {
        assertThat(Journal.field("what", "the \"big\" one")).isEqualTo("what=\"the \\\"big\\\" one\"");
    }

    @Test
    void aWindowsPathWithASpaceKeepsItsBackslashesReadable() {
        String made = Journal.field("path", "c:\\Program Files\\office.sqlite3");
        assertThat(made).isEqualTo("path=\"c:\\\\Program Files\\\\office.sqlite3\"");
    }

    @Test
    void aPathWithNoSpaceIsLeftAlone() {
        assertThat(Journal.field("path", "c:\\office.sqlite3")).isEqualTo("path=c:\\office.sqlite3");
    }

    @Test
    void aLevelBelowTheBarWritesNothing() {
        Journal made = book(Level.WARN);
        made.debug("noise");
        made.info("noise");
        made.warn("something");
        assertThat(lines()).hasSize(1);
    }

    @Test
    void aSilentJournalWritesNothingAtAll() {
        Journal made = Journal.silent();
        assertThat(made.wants(Level.ERROR)).isFalse();
        made.error("this goes nowhere");
    }

    @Test
    void theLevelsAreOrderedTheWayAComparisonReads() {
        assertThat(Level.values()).containsExactly(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.OFF);
        assertThat(Level.DEBUG).isLessThan(Level.INFO);
        assertThat(Level.INFO).isLessThan(Level.WARN);
        assertThat(Level.WARN).isLessThan(Level.ERROR);
        assertThat(Level.ERROR).isLessThan(Level.OFF);
    }

    @Test
    void aLevelIsLookedUpByName() {
        assertThat(Level.named("warn")).isSameAs(Level.WARN);
        assertThat(Level.named("  Error  ")).isSameAs(Level.ERROR);
    }

    @Test
    void aLevelNobodyHas() {
        assertThatThrownBy(() -> Level.named("chatty"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no such level")
                .hasMessage("no such level chatty, wanted one of debug, info, warn, error, off");
    }

    @Test
    void contextGoesOnEveryLineFromThenOn() {
        Journal about = book().about("request", "abc123");
        about.info("one");
        about.info("two", "status", 200);
        assertThat(lines()).allMatch(one -> one.contains("request=abc123"));
        assertThat(lines().get(1)).contains("status=200");
    }

    @Test
    void contextAddsRatherThanReplaces() {
        book().about("request", "abc").about("lift", "JP").info("both");
        assertThat(lines().get(0)).contains("request=abc").contains("lift=JP");
    }

    @Test
    void theJournalContextDoesNotLeakBackToTheOneItCameFrom() {
        Journal made = book();
        made.about("request", "abc").info("one");
        made.info("two");
        assertThat(lines().get(0)).contains("request=abc");
        assertThat(lines().get(1)).doesNotContain("request=abc");
    }

    @Test
    void timingWritesWhenTheWorkIsDone() {
        book().timing("season", () -> {}, "label", "2024-25");
        String line = lines().get(0);
        assertThat(line).contains("outcome=ok").contains("ms=").contains("label=2024-25");
    }

    @Test
    void timingStillWritesWhenTheWorkFallsOver() {
        Journal made = book();
        assertThatThrownBy(() -> made.timing("season", () -> {
                    throw new ArithmeticException("/ by zero");
                }))
                .isInstanceOf(ArithmeticException.class);
        String line = lines().get(0);
        assertThat(line).contains("error").contains("outcome=failed").contains("error=ArithmeticException");
    }

    @Test
    void aRequestIdIsShortEnoughToReadOutAndDifferentEveryTime() {
        String made = Journal.newRequestId();
        assertThat(made).hasSize(12);
        assertThat(made).isNotEqualTo(Journal.newRequestId());
    }

    @Test
    void aJournalOnStderrWritesWhereItSaysItDoes() {
        Journal made = Journal.toStderr(Level.DEBUG);
        assertThat(made.wants(Level.DEBUG)).isTrue();
        assertThat(made.stream()).isSameAs(System.err);
    }
}
