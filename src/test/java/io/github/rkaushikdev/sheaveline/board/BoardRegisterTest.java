package io.github.rkaushikdev.sheaveline.board;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardRegisterTest {

    private static final LocalDateTime AT = LocalDateTime.of(2025, 1, 15, 11, 30);
    private static final LocalDateTime ON_TIME = LocalDateTime.of(2025, 1, 15, 12, 30);

    private static Incident incident(
            LiftCode lift, IncidentKind kind, InjuryLevel injury, int people, LocalDateTime at, String narrative) {
        return new Incident(lift, at, kind, injury, people, narrative, "K Ostler");
    }

    private static Incident incident(LiftCode lift) {
        return incident(lift, IncidentKind.DEROPEMENT, InjuryLevel.NONE, 0, AT, "chair 34 off at T4");
    }

    private static Incident incident() {
        return incident(JOHN_PAUL);
    }

    private static Incident poleCaught(LocalDateTime at, String narrative) {
        return incident(JOHN_PAUL, IncidentKind.LOADING, InjuryLevel.FIRST_AID, 1, at, narrative);
    }

    private static BoardRegister register(Incident... incidents) {
        BoardRegister held = BoardRegister.of(FilingSeries.of("UT", 2025));
        for (Incident one : incidents) {
            held = held.record(one);
        }
        return held;
    }

    private static Filing filing(FilingKind kind, LiftCode lift) {
        return new Filing(new FilingNumber("UT", 2025, 1), kind, lift, ON_TIME, "a thing", "H Okafor");
    }

    @Test
    void anIncidentStartsUnfiled() {
        BoardRegister held = register(incident());
        assertThat(held.size()).isEqualTo(1);
        assertThat(held.unfiled()).hasSize(1);
        assertThat(held.reported().get(0).isFiled()).isFalse();
        assertThat(held.reported().get(0).toString()).endsWith("not filed");
        assertThat(held.reported().get(0)).hasToString("JP deropement at 2025-01-15 11:30, none, not filed");
    }

    @Test
    void filingTakesTheNextNumberFromTheSeries() {
        Incident one = incident();
        BoardRegister held = register(one).file(one, ON_TIME, "H Okafor");
        assertThat(held.unfiled()).isEmpty();
        ReportedIncident filed = held.reported().get(0);
        assertThat(filed.isFiled()).isTrue();
        assertThat(filed.filing()).isPresent();
        assertThat(filed.filing().get().number()).isEqualTo(new FilingNumber("UT", 2025, 1));
        assertThat(filed.toString()).endsWith("filed as UT-2025-0001");
    }

    @Test
    void filingSomethingThatIsNotInTheRegister() {
        BoardRegister empty = register();
        Incident stranger = incident();
        assertThatThrownBy(() -> empty.file(stranger, ON_TIME, "H Okafor"))
                .isInstanceOf(BoardError.class)
                .hasMessage("that incident is not in the register");
    }

    @Test
    void anIncidentIsFiledOnce() {
        Incident one = incident();
        BoardRegister held = register(one).file(one, ON_TIME, "H Okafor");
        assertThatThrownBy(() -> held.file(one, LocalDateTime.of(2025, 1, 16, 9, 0), "H Okafor"))
                .isInstanceOf(BoardError.class)
                .hasMessage("that incident has already been filed");
    }

    @Test
    void aFilingAgainstAnotherMachineIsRefused() {
        Filing wrong = filing(FilingKind.INCIDENT, STRAWBERRY);
        Incident one = incident();
        assertThatThrownBy(() -> new ReportedIncident(one, wrong))
                .isInstanceOf(BoardError.class)
                .hasMessage("that filing is against another machine");
    }

    @Test
    void anIncidentIsAnsweredByAnIncidentFiling() {
        Filing wrongKind = filing(FilingKind.CAPACITY, JOHN_PAUL);
        Incident one = incident();
        assertThatThrownBy(() -> new ReportedIncident(one, wrongKind))
                .isInstanceOf(BoardError.class)
                .hasMessage("an incident is answered by an incident filing");
    }

    @Test
    void anUnfiledIncidentGoesOverdueWhenItsDeadlinePasses() {
        BoardRegister held = register(incident());
        assertThat(held.overdue(LocalDateTime.of(2025, 1, 15, 12, 30))).isEmpty();
        assertThat(held.overdue(LocalDateTime.of(2025, 1, 15, 14, 30))).isNotEmpty();
    }

    @Test
    void aFilingInsideTheDeadlineClearsTheOverdue() {
        Incident one = incident();
        BoardRegister held = register(one).file(one, ON_TIME, "H Okafor");
        assertThat(held.overdue(LocalDateTime.of(2025, 2, 1, 0, 0))).isEmpty();
        assertThat(held.lateFilings()).isEmpty();
    }

    @Test
    void aFilingAfterTheDeadlineStaysOverdueForever() {
        Incident one = incident();
        BoardRegister held = register(one).file(one, LocalDateTime.of(2025, 1, 17, 9, 0), "H Okafor");
        assertThat(held.overdue(LocalDateTime.of(2025, 2, 1, 0, 0))).isNotEmpty();
        assertThat(held.lateFilings()).hasSize(1);
        assertThat(held.lateFilings().get(0).lateness()).isGreaterThan(Duration.ofHours(40));
    }

    @Test
    void latenessOnSomethingUnfiledIsAnErrorRatherThanAZero() {
        ReportedIncident entry = register(incident()).reported().get(0);
        assertThatThrownBy(entry::lateness)
                .isInstanceOf(BoardError.class)
                .hasMessage("nothing was filed, so there is no lateness yet");
    }

    @Test
    void anUnfiledIncidentReportsHowLongItHasBeenSitting() {
        ReportedIncident entry = register(incident()).reported().get(0);
        assertThat(entry.outstandingFor(LocalDateTime.of(2025, 1, 15, 13, 30))).isEqualTo(Duration.ofHours(2));
        assertThatThrownBy(() -> entry.outstandingFor(LocalDateTime.of(2025, 1, 15, 10, 0)))
                .isInstanceOf(BoardError.class)
                .hasMessage("that moment is before the incident");
    }

    @Test
    void aFiledIncidentHasNothingOutstanding() {
        Incident one = incident();
        BoardRegister held = register(one).file(one, ON_TIME, "H Okafor");
        assertThat(held.reported()
                        .get(0)
                        .outstandingFor(LocalDateTime.of(2025, 2, 1, 0, 0))
                        .isZero())
                .isTrue();
    }

    @Test
    void theIncidentDueNextIsTheOneWithLeastTimeOnIt() {
        Incident urgent = incident();
        Incident slack = poleCaught(LocalDateTime.of(2025, 1, 15, 10, 0), "pole caught on the ramp");
        BoardRegister held = register(slack, urgent);
        Optional<ReportedIncident> due = held.dueNext(LocalDateTime.of(2025, 1, 15, 12, 0));
        assertThat(due).isPresent();
        assertThat(due.get().incident()).isEqualTo(urgent);
    }

    @Test
    void nothingDueMeansNothingRatherThanAnError() {
        assertThat(register().dueNext(LocalDateTime.of(2025, 1, 15, 12, 0))).isEmpty();
    }

    @Test
    void theMonthlyReturnOnlyHoldsWhatBelongsInIt() {
        Incident quiet = poleCaught(AT, "pole caught on the ramp");
        BoardRegister held = register(incident(), quiet);
        List<ReportedIncident> monthly = held.monthlyReturn(2025, 1);
        assertThat(monthly.stream().map(ReportedIncident::incident)).containsExactly(quiet);
        assertThat(held.monthlyReturn(2025, 2)).isEmpty();
    }

    @Test
    void incidentsAreKeptApartByMachine() {
        BoardRegister held = register(incident(JOHN_PAUL), incident(STRAWBERRY));
        assertThat(held.forLift(JOHN_PAUL)).hasSize(1);
        assertThat(held.forLift(STRAWBERRY)).hasSize(1);
    }

    @Test
    void anUnfiledHoldingIncidentNamesTheMachineItIsHolding() {
        BoardRegister held = register(incident());
        assertThat(held.holdingMachines()).contains(JOHN_PAUL);
        Incident one = held.reported().get(0).incident();
        BoardRegister after = held.file(one, ON_TIME, "H Okafor");
        assertThat(after.holdingMachines()).isEmpty();
    }

    @Test
    void aGentleIncidentNeverHoldsAMachine() {
        BoardRegister held = register(poleCaught(AT, "pole caught"));
        assertThat(held.holdingMachines()).isEmpty();
    }
}
