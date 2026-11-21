package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.persistence.Numbering.adopt;
import static io.github.rkaushikdev.sheaveline.persistence.Numbering.issuedCount;
import static io.github.rkaushikdev.sheaveline.persistence.Numbering.peekNumber;
import static io.github.rkaushikdev.sheaveline.persistence.Numbering.seriesYears;
import static io.github.rkaushikdev.sheaveline.persistence.Numbering.takeNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.persistence.models.FilingCounterKey;
import io.github.rkaushikdev.sheaveline.persistence.models.FilingCounterRow;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NumberingTest {

    private Database database;
    private Session session;

    @BeforeEach
    void open() {
        database = TestDatabase.fresh();
        session = database.session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    @Test
    void theCounterTableIsOnTheSchema() {
        assertThat(database.existingTables()).contains("filing_counters");
    }

    @Test
    void theFirstNumberOfAYearIsOne() {
        assertThat(takeNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 1));
    }

    @Test
    void numbersComeOutInSequenceWithNoGaps() {
        List<FilingNumber> taken = IntStream.range(0, 5)
                .mapToObj(one -> takeNumber(session, "UT", 2025))
                .toList();
        assertThat(taken).extracting(FilingNumber::sequence).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void twoStatesKeepSeparateSequences() {
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(1);
        assertThat(takeNumber(session, "CO", 2025).sequence()).isEqualTo(1);
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(2);
    }

    @Test
    void twoYearsKeepSeparateSequences() {
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(1);
        assertThat(takeNumber(session, "UT", 2026).sequence()).isEqualTo(1);
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(2);
    }

    @Test
    void theStateCodeIsNormalised() {
        assertThat(takeNumber(session, "ut", 2025).state()).isEqualTo("UT");
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(2);
    }

    @Test
    void somethingThatIsNotAStateCodeIsRefused() {
        assertThatThrownBy(() -> takeNumber(session, "UTAH", 2025))
                .isInstanceOf(StorageError.class)
                .hasMessage("UTAH is not a state code");
        assertThatThrownBy(() -> takeNumber(session, "U1", 2025))
                .isInstanceOf(StorageError.class)
                .hasMessage("U1 is not a state code");
    }

    @Test
    void peekingDoesNotTakeTheNumber() {
        assertThat(peekNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(peekNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(takeNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(peekNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 2));
    }

    @Test
    void theIssuedCountFollowsTheCounter() {
        assertThat(issuedCount(session, "UT", 2025)).isZero();
        takeNumber(session, "UT", 2025);
        takeNumber(session, "UT", 2025);
        assertThat(issuedCount(session, "UT", 2025)).isEqualTo(2);
        assertThat(issuedCount(session, "CO", 2025)).isZero();
    }

    @Test
    void theYearsAStateHasFiledInComeBackInOrder() {
        takeNumber(session, "UT", 2026);
        takeNumber(session, "UT", 2024);
        takeNumber(session, "CO", 2025);
        assertThat(seriesYears(session, "UT")).containsExactly(2024, 2026);
        assertThat(seriesYears(session, "CO")).containsExactly(2025);
    }

    @Test
    void aNumberLoadedFromPaperMovesTheCounterPastIt() {
        adopt(session, new FilingNumber("UT", 2025, 40));
        assertThat(takeNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 41));
    }

    @Test
    void adoptingAnOlderNumberNeverMovesTheCounterBackwards() {
        takeNumber(session, "UT", 2025);
        takeNumber(session, "UT", 2025);
        adopt(session, new FilingNumber("UT", 2025, 1));
        assertThat(takeNumber(session, "UT", 2025)).isEqualTo(new FilingNumber("UT", 2025, 3));
    }

    @Test
    void theCounterSurvivesACommit() {
        takeNumber(session, "UT", 2025);
        session.commit();
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(2);
        session.commit();
        assertThat(session.find(FilingCounterRow.class, new FilingCounterKey("UT", 2025)))
                .get()
                .extracting(FilingCounterRow::getNextSequence)
                .isEqualTo(3);
    }

    @Test
    void aRolledBackNumberIsHandedOutAgain() {
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(1);
        session.rollback();
        assertThat(takeNumber(session, "UT", 2025).sequence()).isEqualTo(1);
    }

    /** Counting rows is the mistake this table exists to avoid. */
    @Test
    void theCounterDoesNotCountTheRowsAlreadyWritten() {
        takeNumber(session, "UT", 2025);
        takeNumber(session, "UT", 2025);
        session.commit();
        assertThat(session.find(FilingCounterRow.class, new FilingCounterKey("UT", 2025)))
                .get()
                .extracting(FilingCounterRow::getNextSequence)
                .isEqualTo(3);
        assertThat(issuedCount(session, "UT", 2025)).isEqualTo(2);
    }
}
