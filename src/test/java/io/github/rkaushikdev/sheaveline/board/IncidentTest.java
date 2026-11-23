package io.github.rkaushikdev.sheaveline.board;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class IncidentTest {

    private static final LocalDateTime AT = LocalDateTime.of(2025, 1, 15, 11, 30);
    private static final String NARRATIVE = "skier caught a pole on the ramp";

    private static Incident incident(
            IncidentKind kind, InjuryLevel injury, int people, String narrative, String reportedBy) {
        return new Incident(JOHN_PAUL, AT, kind, injury, people, narrative, reportedBy);
    }

    private static Incident incident(IncidentKind kind, InjuryLevel injury, int people) {
        return incident(kind, injury, people, NARRATIVE, "K Ostler");
    }

    private static Incident incident(IncidentKind kind, InjuryLevel injury) {
        return incident(kind, injury, 1);
    }

    private static Incident nobodyHurt(IncidentKind kind) {
        return incident(kind, InjuryLevel.NONE, 0);
    }

    @Test
    void anIncidentCarriesANarrativeAndAName() {
        assertThatThrownBy(() -> incident(IncidentKind.LOADING, InjuryLevel.NONE, 1, "   ", "K Ostler"))
                .isInstanceOf(BoardError.class)
                .hasMessage("an incident carries a narrative");
        assertThatThrownBy(() -> incident(IncidentKind.LOADING, InjuryLevel.NONE, 1, NARRATIVE, ""))
                .isInstanceOf(BoardError.class)
                .hasMessage("an incident is signed by whoever reported it");
    }

    @Test
    void somebodyHurtMeansSomebodyInvolved() {
        assertThatThrownBy(() -> incident(IncidentKind.LOADING, InjuryLevel.FIRST_AID, 0))
                .isInstanceOf(BoardError.class)
                .hasMessage("somebody was hurt, so somebody was involved");
        assertThatThrownBy(() -> incident(IncidentKind.LOADING, InjuryLevel.NONE, -1))
                .isInstanceOf(BoardError.class)
                .hasMessage("an incident cannot involve fewer than nobody");
        assertThat(incident(IncidentKind.LOADING, InjuryLevel.NONE, 0).people()).isZero();
    }

    @Test
    void anOrdinaryLoadingBumpGoesInTheMonthlyReturn() {
        Incident ordinary = incident(IncidentKind.LOADING, InjuryLevel.FIRST_AID);
        assertThat(ordinary.deadline()).isEqualTo(Incident.MONTHLY);
        assertThat(ordinary.goesInTheMonthlyReturn()).isTrue();
        assertThat(ordinary.isImmediate()).isFalse();
    }

    @Test
    void aDeropementWithNobodyHurtIsStillATwoHourCall() {
        Incident quiet = nobodyHurt(IncidentKind.DEROPEMENT);
        assertThat(quiet.deadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(quiet.isImmediate()).isTrue();
        assertThat(quiet.deadlineDriver()).isEqualTo("kind");
    }

    @Test
    void aSeriousInjuryOnAMachineThatBehavedIsStillATwoHourCall() {
        Incident hurt = incident(IncidentKind.UNLOADING, InjuryLevel.ADMITTED);
        assertThat(hurt.deadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(hurt.deadlineDriver()).isEqualTo("injury");
    }

    @Test
    void whenBothFireTheShorterOneWins() {
        Incident both = incident(IncidentKind.EVACUATION, InjuryLevel.ADMITTED);
        assertThat(both.deadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(both.deadlineDriver()).isEqualTo("injury");
        Incident other = incident(IncidentKind.DEROPEMENT, InjuryLevel.TRANSPORTED);
        assertThat(other.deadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(other.deadlineDriver()).isEqualTo("kind");
    }

    @Test
    void twoEqualDeadlinesAreReportedAsBoth() {
        Incident equal = incident(IncidentKind.DEROPEMENT, InjuryLevel.FATAL);
        assertThat(equal.deadlineDriver()).isEqualTo("both");
    }

    @Test
    void threePeopleTakenOffOneChairIsATwoHourCallWhateverTheMachineDid() {
        Incident several = incident(IncidentKind.LOADING, InjuryLevel.TRANSPORTED, 3);
        assertThat(several.casualtyDeadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(several.deadline()).isEqualTo(Incident.IMMEDIATE);
        assertThat(several.deadlineDriver()).isEqualTo("people");
        assertThat(several.isImmediate()).isTrue();
    }

    @Test
    void twoTransportedOrThreePatchedUpStayOnTheOtherClocks() {
        Incident two = incident(IncidentKind.LOADING, InjuryLevel.TRANSPORTED, 2);
        assertThat(two.casualtyDeadline()).isEqualTo(Incident.MONTHLY);
        assertThat(two.deadline()).isEqualTo(Incident.NEXT_DAY);
        assertThat(two.deadlineDriver()).isEqualTo("injury");
        Incident bumped = incident(IncidentKind.LOADING, InjuryLevel.FIRST_AID, 4);
        assertThat(bumped.deadline()).isEqualTo(Incident.MONTHLY);
        assertThat(bumped.deadlineDriver()).isEqualTo("both");
    }

    @Test
    void theCasualtyClockIsNamedBesideTheOthersItMatches() {
        assertThat(incident(IncidentKind.DEROPEMENT, InjuryLevel.TRANSPORTED, 3).deadlineDriver())
                .isEqualTo("kind and people");
        assertThat(incident(IncidentKind.DEROPEMENT, InjuryLevel.FATAL, 5).deadlineDriver())
                .isEqualTo("both and people");
        assertThat(incident(IncidentKind.UNLOADING, InjuryLevel.ADMITTED, 3).deadlineDriver())
                .isEqualTo("injury and people");
    }

    @Test
    void aTransportedSkierIsANextDayFiling() {
        Incident hurt = incident(IncidentKind.LOADING, InjuryLevel.TRANSPORTED);
        assertThat(hurt.deadline()).isEqualTo(Incident.NEXT_DAY);
        assertThat(hurt.isImmediate()).isFalse();
        assertThat(hurt.goesInTheMonthlyReturn()).isFalse();
    }

    @Test
    void anEvacuationIsANextDayFilingHoweverItWent() {
        Incident quiet = nobodyHurt(IncidentKind.EVACUATION);
        assertThat(quiet.deadline()).isEqualTo(Incident.NEXT_DAY);
    }

    @Test
    void everyKindAndEveryLevelCarriesADeadline() {
        for (IncidentKind kind : IncidentKind.values()) {
            assertThat(kind.deadline().seconds()).isPositive();
        }
        for (InjuryLevel level : InjuryLevel.values()) {
            assertThat(level.deadline().seconds()).isPositive();
        }
    }

    @Test
    void theDueTimeIsTheDeadlineAfterTheEvent() {
        Incident quiet = nobodyHurt(IncidentKind.DEROPEMENT);
        assertThat(quiet.dueBy()).isEqualTo(LocalDateTime.of(2025, 1, 15, 13, 30));
    }

    @Test
    void aFilingAfterTheDeadlineIsOverdue() {
        Incident quiet = nobodyHurt(IncidentKind.DEROPEMENT);
        assertThat(quiet.isOverdue(LocalDateTime.of(2025, 1, 15, 12, 30))).isFalse();
        assertThat(quiet.isOverdue(LocalDateTime.of(2025, 1, 15, 14, 30))).isTrue();
        assertThat(quiet.isOverdue(AT, LocalDateTime.of(2025, 1, 16, 9, 0))).isTrue();
        assertThat(quiet.isOverdue(LocalDateTime.of(2025, 1, 20, 0, 0), LocalDateTime.of(2025, 1, 15, 12, 0)))
                .isFalse();
    }

    @Test
    void someKindsHoldTheMachineWhateverTheInjuriesWere() {
        for (IncidentKind kind : new IncidentKind[] {
            IncidentKind.DEROPEMENT, IncidentKind.ROLLBACK, IncidentKind.STRUCTURAL, IncidentKind.FALL_FROM_CARRIER
        }) {
            assertThat(nobodyHurt(kind).holdsTheMachine()).isTrue();
        }
    }

    @Test
    void aSeriousInjuryHoldsTheMachineWhateverTheKindWas() {
        assertThat(incident(IncidentKind.LOADING, InjuryLevel.ADMITTED).holdsTheMachine())
                .isTrue();
        assertThat(incident(IncidentKind.LOADING, InjuryLevel.FIRST_AID).holdsTheMachine())
                .isFalse();
    }

    @Test
    void theInjuryLevelsKnowWhichOfThemAreSerious() {
        assertThat(InjuryLevel.FATAL.isSerious()).isTrue();
        assertThat(InjuryLevel.ADMITTED.isSerious()).isTrue();
        assertThat(InjuryLevel.TRANSPORTED.isSerious()).isFalse();
        assertThat(InjuryLevel.NONE.isSerious()).isFalse();
    }

    @Test
    void theDeadlinesAreOrderedTheWayTheyRead() {
        assertThat(Incident.IMMEDIATE).isLessThan(Incident.NEXT_DAY);
        assertThat(Incident.NEXT_DAY).isLessThan(Incident.MONTHLY);
        assertThat(Duration.ofHours(2)).isEqualTo(Incident.IMMEDIATE);
    }

    @Test
    void anIncidentPrintsAOneLiner() {
        String printed = nobodyHurt(IncidentKind.DEROPEMENT).toString();
        assertThat(printed).isEqualTo("JP deropement at 2025-01-15 11:30, none");
    }
}
