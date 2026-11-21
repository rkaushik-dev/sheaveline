package io.github.rkaushikdev.sheaveline.board;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FilingTest {

    private static final LocalDateTime AT = LocalDateTime.of(2025, 1, 15, 11, 30);
    private static final LocalDateTime SENT = LocalDateTime.of(2025, 1, 15, 12, 30);

    private static Filing filing(
            int sequence, FilingKind kind, LiftCode lift, LocalDateTime filed, Optional<LocalDate> acknowledged) {
        return new Filing(
                new FilingNumber("UT", 2025, sequence),
                kind,
                lift,
                filed,
                "chair 34 came off the sheave train at T4",
                "H Okafor",
                acknowledged);
    }

    private static Filing filing(int sequence, FilingKind kind, LiftCode lift) {
        return filing(sequence, kind, lift, SENT, Optional.empty());
    }

    private static Filing filing(int sequence, FilingKind kind) {
        return filing(sequence, kind, JOHN_PAUL);
    }

    private static Filing filing(int sequence) {
        return filing(sequence, FilingKind.INCIDENT);
    }

    private static Filing filing(FilingKind kind) {
        return filing(1, kind);
    }

    private static Filing filedAt(LocalDateTime filed) {
        return filing(1, FilingKind.INCIDENT, JOHN_PAUL, filed, Optional.empty());
    }

    private static FilingSeries series(Filing... filings) {
        return FilingSeries.of("UT", 2025, List.of(filings));
    }

    private static Incident deropement() {
        return new Incident(
                JOHN_PAUL, AT, IncidentKind.DEROPEMENT, InjuryLevel.NONE, 0, "chair 34 off at T4", "K Ostler");
    }

    private static List<Integer> sequences(List<Filing> filings) {
        return filings.stream().map(one -> one.number().sequence()).toList();
    }

    @Test
    void aFilingSaysSomethingAndIsSigned() {
        FilingNumber first = new FilingNumber("UT", 2025, 1);
        assertThatThrownBy(() -> new Filing(first, FilingKind.INCIDENT, JOHN_PAUL, AT, " ", "H Okafor"))
                .isInstanceOf(BoardError.class)
                .hasMessage("UT-2025-0001 says nothing");
        assertThatThrownBy(() -> new Filing(first, FilingKind.INCIDENT, JOHN_PAUL, AT, "about", " "))
                .isInstanceOf(BoardError.class)
                .hasMessage("UT-2025-0001 is signed by whoever sent it");
    }

    @Test
    void aFilingCannotBeAcknowledgedBeforeItWasSent() {
        assertThatThrownBy(() -> filing(1, FilingKind.INCIDENT, JOHN_PAUL, SENT, Optional.of(LocalDate.of(2025, 1, 1))))
                .isInstanceOf(BoardError.class)
                .hasMessage("UT-2025-0001 was acknowledged before it was sent");
    }

    @Test
    void onlySomeKindsWaitOnTheBoardWritingBack() {
        assertThat(FilingKind.CAPACITY.needsAnAcknowledgement()).isTrue();
        assertThat(FilingKind.MODIFICATION.needsAnAcknowledgement()).isTrue();
        assertThat(FilingKind.INCIDENT.needsAnAcknowledgement()).isFalse();
        assertThat(FilingKind.MONTHLY_RETURN.isRoutine()).isTrue();
        assertThat(FilingKind.INCIDENT.isRoutine()).isFalse();
    }

    @Test
    void aFilingWaitingOnAnAcknowledgementIsOutstanding() {
        Filing waiting = filing(FilingKind.CAPACITY);
        assertThat(waiting.isOutstanding()).isTrue();
        assertThat(waiting.isAcknowledged()).isFalse();
        Filing answered = waiting.acknowledge(LocalDate.of(2025, 2, 1));
        assertThat(answered.isAcknowledged()).isTrue();
        assertThat(answered.isOutstanding()).isFalse();
    }

    @Test
    void anIncidentFilingIsNeverOutstanding() {
        assertThat(filing(FilingKind.INCIDENT).isOutstanding()).isFalse();
    }

    @Test
    void aFilingIsAcknowledgedOnce() {
        Filing answered = filing(FilingKind.CAPACITY).acknowledge(LocalDate.of(2025, 2, 1));
        assertThatThrownBy(() -> answered.acknowledge(LocalDate.of(2025, 3, 1)))
                .isInstanceOf(BoardError.class)
                .hasMessage("UT-2025-0001 is already acknowledged");
    }

    @Test
    void latenessIsMeasuredAgainstTheIncidentNotTheClerk() {
        Filing onTime = filedAt(LocalDateTime.of(2025, 1, 15, 12, 30));
        assertThat(onTime.wasLate(deropement())).isFalse();
        assertThat(onTime.lateness(deropement()).isZero()).isTrue();
        Filing monday = filedAt(LocalDateTime.of(2025, 1, 17, 9, 0));
        assertThat(monday.wasLate(deropement())).isTrue();
        assertThat(monday.lateness(deropement())).isGreaterThan(Duration.ofHours(40));
    }

    @Test
    void aFilingPrintsAOneLiner() {
        assertThat(filing(1)).hasToString("UT-2025-0001 incident, JP, 2025-01-15");
    }

    @Test
    void aSeriesHandsOutTheFirstNumberWhenItIsEmpty() {
        assertThat(series().nextNumber()).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(series().isGapless()).isTrue();
    }

    @Test
    void filingThroughTheSeriesTakesTheNextNumber() {
        FilingSeries.Filed first = series().file(
                        FilingKind.INCIDENT, JOHN_PAUL, LocalDateTime.of(2025, 1, 15, 12, 30), "a thing", "H Okafor");
        FilingSeries.Filed second = first.series()
                .file(FilingKind.CAPACITY, STRAWBERRY, LocalDateTime.of(2025, 2, 1, 9, 0), "another", "H Okafor");
        FilingSeries held = second.series();
        assertThat(first.filing().number().sequence()).isEqualTo(1);
        assertThat(second.filing().number().sequence()).isEqualTo(2);
        assertThat(held.size()).isEqualTo(2);
        assertThat(held.isGapless()).isTrue();
    }

    @Test
    void aFilingFromAnotherYearDoesNotGoInThisSeries() {
        assertThatThrownBy(() -> series().file(
                                FilingKind.INCIDENT,
                                JOHN_PAUL,
                                LocalDateTime.of(2026, 1, 5, 9, 0),
                                "a thing",
                                "H Okafor"))
                .isInstanceOf(BoardError.class)
                .hasMessage("a 2026 filing does not go in the 2025 series");
    }

    @Test
    void aNumberFromAnotherStateOrYearIsRefused() {
        Filing stranger =
                new Filing(new FilingNumber("CO", 2025, 1), FilingKind.INCIDENT, JOHN_PAUL, AT, "a thing", "H Okafor");
        assertThatThrownBy(() -> series(stranger))
                .isInstanceOf(BoardError.class)
                .hasMessage("CO-2025-0001 does not belong in UT 2025");
    }

    @Test
    void twoFilingsCannotShareANumber() {
        assertThatThrownBy(() -> series(filing(1), filing(1, FilingKind.INCIDENT, STRAWBERRY)))
                .isInstanceOf(BoardError.class)
                .hasMessage("two filings share a number");
    }

    @Test
    void aGapInTheSequenceIsFound() {
        FilingSeries held = series(filing(1), filing(3));
        assertThat(held.isGapless()).isFalse();
        assertThat(held.missingNumbers()).containsExactly(2);
        assertThat(series().missingNumbers()).isEmpty();
    }

    @Test
    void filingsCanBePulledOutByMachineAndByKind() {
        FilingSeries held =
                series(filing(1, FilingKind.INCIDENT, JOHN_PAUL), filing(2, FilingKind.CAPACITY, STRAWBERRY));
        assertThat(held.forLift(JOHN_PAUL)).hasSize(1);
        assertThat(held.ofKind(FilingKind.CAPACITY)).hasSize(1);
    }

    @Test
    void theOutstandingFilingsAreTheOnesStillWaiting() {
        FilingSeries held = series(
                filing(1, FilingKind.INCIDENT),
                filing(2, FilingKind.CAPACITY),
                filing(3, FilingKind.MODIFICATION, JOHN_PAUL, SENT, Optional.of(LocalDate.of(2025, 2, 1))));
        assertThat(sequences(held.outstanding())).containsExactly(2);
    }

    @Test
    void acknowledgingThroughTheSeriesKeepsTheOrder() {
        FilingSeries held = series(filing(1, FilingKind.CAPACITY), filing(2));
        FilingSeries answered = held.acknowledge(new FilingNumber("UT", 2025, 1), LocalDate.of(2025, 2, 1));
        assertThat(answered.outstanding()).isEmpty();
        assertThat(sequences(answered.filings())).containsExactly(1, 2);
    }

    @Test
    void acknowledgingANumberThatIsNotInTheSeries() {
        FilingSeries held = series(filing(1));
        assertThatThrownBy(() -> held.acknowledge(new FilingNumber("UT", 2025, 9), LocalDate.of(2025, 2, 1)))
                .isInstanceOf(BoardError.class)
                .hasMessage("UT-2025-0009 is not in this series");
    }
}
