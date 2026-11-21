package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.WILDCAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShiftTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm");

    private static Assignment assignment(
            String person, Post post, int startHour, int endHour, int startMinute, int endMinute) {
        return new Assignment(person, post, TimeOfDay.at(startHour, startMinute), TimeOfDay.at(endHour, endMinute));
    }

    private static Assignment assignment(String person, Post post, int startHour, int endHour) {
        return assignment(person, post, startHour, endHour, 0, 0);
    }

    private static Roster roster(Assignment... assignments) {
        return Roster.of(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, DAY, WINDOW, List.of(assignments));
    }

    private static Roster roster(List<Assignment> assignments) {
        return roster(assignments.toArray(Assignment[]::new));
    }

    private static List<Assignment> fullDay(Post... posts) {
        List<Assignment> day = new ArrayList<>();
        for (int index = 0; index < posts.length; index++) {
            day.add(assignment("P" + (index + 1), posts[index], 8, 17));
        }
        return day;
    }

    @Test
    void eachClassNamesThePostsItHasToFill() {
        assertThat(Roster.of(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, DAY, WINDOW).required())
                .isEqualTo(Set.of(Post.BOTTOM, Post.TOP));
        assertThat(Roster.of(WILDCAT, LiftClass.SURFACE_TOW, DAY, WINDOW).required())
                .isEqualTo(Set.of(Post.BOTTOM));
        Roster tram = Roster.of(JOHN_PAUL, LiftClass.AERIAL_TRAM, DAY, WINDOW);
        assertThat(tram.required()).contains(Post.MECHANIC);
    }

    @Test
    void anAssignmentNamesAPersonAndRunsForwards() {
        assertThatThrownBy(() -> assignment("  ", Post.BOTTOM, 9, 16))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an assignment names a person");
        assertThatThrownBy(() -> assignment("R Diaz", Post.BOTTOM, 16, 9))
                .isInstanceOf(OperationsError.class)
                .hasMessage("R Diaz would finish before starting");
    }

    @Test
    void anAssignmentCoversItsOwnStretch() {
        Assignment shift = assignment("R Diaz", Post.BOTTOM, 9, 16);
        assertThat(shift.covers(TimeOfDay.at(9))).isTrue();
        assertThat(shift.covers(TimeOfDay.at(12))).isTrue();
        assertThat(shift.covers(TimeOfDay.at(16))).isFalse();
        assertThat(shift.covers(TimeOfDay.at(8, 59))).isFalse();
        assertThat(shift.length()).isEqualTo(Duration.ofHours(7));
    }

    @Test
    void anAssignmentPrintsTheWayABoardReads() {
        assertThat(assignment("R Diaz", Post.BOTTOM, 9, 16)).hasToString("R Diaz on bottom operator, 9:00 to 16:00");
    }

    @Test
    void twoShiftsAtOnceForOnePersonAreRefused() {
        assertThatThrownBy(
                        () -> roster(assignment("R Diaz", Post.BOTTOM, 9, 13), assignment("R Diaz", Post.TOP, 12, 16)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("R Diaz is in two places at once");
    }

    @Test
    void theSamePersonCanWorkTwoStretchesThatDoNotTouch() {
        Roster covered = roster(assignment("R Diaz", Post.BOTTOM, 8, 12), assignment("R Diaz", Post.TOP, 12, 17));
        assertThat(covered.hoursWorked("R Diaz")).isEqualTo(Duration.ofHours(9));
    }

    @Test
    void aFullyStaffedMachineHasNoGaps() {
        Roster covered = roster(fullDay(Post.BOTTOM, Post.TOP));
        assertThat(covered.isCovered()).isTrue();
        assertThat(covered.gaps()).isEmpty();
        assertThat(covered.isStaffedAt(TimeOfDay.at(12))).isTrue();
        assertThat(covered).hasToString("JP 2025-01-15, 2 on, covered");
    }

    @Test
    void aMissingPostIsAGapForTheWholeDay() {
        Roster thin = roster(fullDay(Post.BOTTOM));
        assertThat(thin.isCovered()).isFalse();
        assertThat(thin.gaps().get(0).post()).isSameAs(Post.TOP);
        assertThat(thin.isStaffedAt(TimeOfDay.at(12))).isFalse();
        assertThat(thin).hasToString("JP 2025-01-15, 1 on, 1 gaps");
    }

    @Test
    void theTenMinuteLunchGapIsFound() {
        Roster slipped = roster(
                assignment("R Diaz", Post.BOTTOM, 8, 17),
                assignment("T Woo", Post.TOP, 8, 12),
                assignment("J Marsh", Post.TOP, 12, 17, 10, 0));
        assertThat(slipped.isCovered()).isFalse();
        List<Roster.Gap> gaps = slipped.gaps();
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).post()).isSameAs(Post.TOP);
        assertThat(gaps.get(0).at()).isEqualTo(TimeOfDay.at(12));
    }

    @Test
    void aCleanHandoverLeavesNoGap() {
        Roster clean = roster(
                assignment("R Diaz", Post.BOTTOM, 8, 17),
                assignment("T Woo", Post.TOP, 8, 12),
                assignment("J Marsh", Post.TOP, 12, 17));
        assertThat(clean.isCovered()).isTrue();
    }

    @Test
    void aShiftThatEndsBeforeTheSweepLeavesAGapAtTheEnd() {
        Roster early = roster(assignment("R Diaz", Post.BOTTOM, 8, 17), assignment("T Woo", Post.TOP, 8, 16));
        List<Roster.Gap> gaps = early.gaps();
        assertThat(gaps).isNotEmpty();
        assertThat(gaps.get(0).at()).isEqualTo(TimeOfDay.at(16));
    }

    @Test
    void aTowIsCoveredByOnePerson() {
        Roster tow = Roster.of(
                WILDCAT, LiftClass.SURFACE_TOW, DAY, WINDOW, List.of(assignment("A Bell", Post.BOTTOM, 8, 17)));
        assertThat(tow.isCovered()).isTrue();
    }

    @Test
    void aTramIsNotCoveredWithoutAMechanic() {
        Roster tram = Roster.of(
                JOHN_PAUL,
                LiftClass.AERIAL_TRAM,
                DAY,
                WINDOW,
                List.of(assignment("R Diaz", Post.BOTTOM, 8, 17), assignment("T Woo", Post.TOP, 8, 17)));
        assertThat(tram.isCovered()).isFalse();
        assertThat(tram.gaps().get(0).post()).isSameAs(Post.MECHANIC);
    }

    @Test
    void whoIsOnAtAGivenMinute() {
        Roster covered = roster(
                assignment("R Diaz", Post.BOTTOM, 8, 17),
                assignment("T Woo", Post.TOP, 8, 12),
                assignment("J Marsh", Post.TOP, 12, 17));
        assertThat(covered.peopleAt(TimeOfDay.at(11))).containsExactly("R Diaz", "T Woo");
        assertThat(covered.peopleAt(TimeOfDay.at(13))).containsExactly("J Marsh", "R Diaz");
        assertThat(covered.staffingAt(TimeOfDay.at(11))).isEqualTo(Set.of(Post.BOTTOM, Post.TOP));
    }

    @Test
    void thePeopleOnTheMachineAreListedOnceEach() {
        Roster covered = roster(assignment("R Diaz", Post.BOTTOM, 8, 12), assignment("R Diaz", Post.TOP, 12, 17));
        assertThat(covered.people()).containsExactly("R Diaz");
    }

    @Test
    void addingAnAssignmentCanCloseAGap() {
        Roster thin = roster(fullDay(Post.BOTTOM));
        assertThat(thin.isCovered()).isFalse();
        Roster filled = thin.withAssignment(assignment("T Woo", Post.TOP, 8, 17));
        assertThat(filled.isCovered()).isTrue();
    }

    @Test
    void thePostsKnowWhichOfThemAreOperators() {
        assertThat(Post.BOTTOM.isAnOperator()).isTrue();
        assertThat(Post.MID.isAnOperator()).isTrue();
        assertThat(Post.MECHANIC.isAnOperator()).isFalse();
    }
}
