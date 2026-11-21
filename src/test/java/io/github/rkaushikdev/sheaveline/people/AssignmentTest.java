package io.github.rkaushikdev.sheaveline.people;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AssignmentTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("9:00", "3:45 pm", "4:15 pm");
    private static final LiftClass QUAD = LiftClass.DETACHABLE_CHAIR;
    private static final LocalDate EXPIRES = LocalDate.of(2025, 11, 1);

    private static Certificate card(String person, Endorsement endorsement, LocalDate expires, Set<LiftClass> classes) {
        return new Certificate(person, endorsement, LocalDate.of(2024, 11, 1), expires, "Utah tramway board", classes);
    }

    private static Certificate card(String person, Endorsement endorsement) {
        return card(person, endorsement, EXPIRES, EnumSet.allOf(LiftClass.class));
    }

    private static Certificate card(String person) {
        return card(person, Endorsement.OPERATOR);
    }

    private static Certificate card(String person, LocalDate expires) {
        return card(person, Endorsement.OPERATOR, expires, EnumSet.allOf(LiftClass.class));
    }

    private static Certificate card(String person, Set<LiftClass> classes) {
        return card(person, Endorsement.OPERATOR, EXPIRES, classes);
    }

    private static Assignment shift(String person, Post post) {
        return new Assignment(person, post, TimeOfDay.at(8), TimeOfDay.at(17));
    }

    private static RosterCheck check(List<Assignment> assignments, List<Certificate> cards) {
        Roster roster = Roster.of(JOHN_PAUL, QUAD, DAY, WINDOW, assignments);
        return new RosterCheck(roster, PersonnelFile.of(cards), QUAD, DAY);
    }

    private static RosterCheck clean() {
        return check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", Endorsement.OPERATOR), card("T Woo", Endorsement.LEAD_OPERATOR)));
    }

    private static List<String> people(List<RosterProblem> problems) {
        return problems.stream().map(problem -> problem.person().orElseThrow()).toList();
    }

    @Test
    void aCoveredRosterWithTheRightCardsIsClear() {
        RosterCheck read = clean();
        assertThat(read.isLegal()).isTrue();
        assertThat(read.problems()).isEmpty();
        assertThat(read).hasToString("JP 2025-01-15, roster clear");
    }

    @Test
    void aRosterForAnotherClassIsRefused() {
        Roster roster = Roster.of(JOHN_PAUL, LiftClass.FIXED_CHAIR, DAY, WINDOW, List.of());
        assertThatThrownBy(() -> new RosterCheck(roster, PersonnelFile.of(), QUAD, DAY))
                .isInstanceOf(PeopleError.class)
                .hasMessage("that roster is for another class of machine");
    }

    @Test
    void anEmptyPostShowsAsUncovered() {
        RosterCheck read = check(List.of(shift("R Diaz", Post.BOTTOM)), List.of(card("R Diaz")));
        assertThat(read.isLegal()).isFalse();
        assertThat(read.problemsOf(ProblemKind.UNCOVERED)).isNotEmpty();
        assertThat(read.problemsOf(ProblemKind.UNCOVERED).get(0).post()).isSameAs(Post.TOP);
    }

    @Test
    void somebodyWithNoCardAtAllIsReported() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("T Woo", Endorsement.LEAD_OPERATOR)));
        List<RosterProblem> problems = read.problemsOf(ProblemKind.UNCARDED);
        assertThat(people(problems)).containsExactly("R Diaz");
    }

    @Test
    void theBottomOperatorMovingUpIsCaught() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", Endorsement.OPERATOR), card("T Woo", Endorsement.OPERATOR)));
        List<RosterProblem> problems = read.problemsOf(ProblemKind.UNCARDED);
        assertThat(people(problems)).containsExactly("T Woo");
        assertThat(problems.get(0).post()).isSameAs(Post.TOP);
    }

    @Test
    void aCardThatDoesNotCoverTheMachineSaysSo() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", Set.of(LiftClass.FIXED_CHAIR)), card("T Woo", Endorsement.LEAD_OPERATOR)));
        List<RosterProblem> problems = read.problemsOf(ProblemKind.WRONG_CLASS);
        assertThat(people(problems)).containsExactly("R Diaz");
    }

    @Test
    void aCardThatHasRunOutSaysThatInstead() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", LocalDate.of(2024, 12, 1)), card("T Woo", Endorsement.LEAD_OPERATOR)));
        List<RosterProblem> problems = read.problemsOf(ProblemKind.LAPSED);
        assertThat(people(problems)).containsExactly("R Diaz");
    }

    @Test
    void theThreeCardFailuresAreToldApart() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", LocalDate.of(2024, 12, 1)), card("T Woo", Endorsement.OPERATOR)));
        Set<ProblemKind> kinds =
                read.problems().stream().map(RosterProblem::kind).collect(Collectors.toSet());
        assertThat(kinds).contains(ProblemKind.LAPSED);
        assertThat(kinds).contains(ProblemKind.UNCARDED);
    }

    @Test
    void onlyAPersonProblemCarriesAName() {
        RosterCheck read = check(List.of(shift("R Diaz", Post.BOTTOM)), List.of(card("R Diaz")));
        assertThat(read.problems()).isNotEmpty();
        for (RosterProblem problem : read.problems()) {
            if (problem.kind().isAboutAPerson()) {
                assertThat(problem.person()).isPresent();
            } else {
                assertThat(problem.person()).isEmpty();
            }
        }
    }

    @Test
    void thePeopleWhoNeedSortingOutAreListedOnceEach() {
        RosterCheck read = check(List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)), List.of());
        assertThat(read.peopleToFix()).containsExactly("R Diaz", "T Woo");
    }

    @Test
    void aSubstituteIsSomebodyCardedAndNotAlreadyWorking() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(
                        card("R Diaz", Endorsement.OPERATOR),
                        card("T Woo", Endorsement.LEAD_OPERATOR),
                        card("J Marsh", Endorsement.LEAD_OPERATOR)));
        Optional<Assignment> topShift = read.roster().assignments().stream()
                .filter(one -> one.post() == Post.TOP)
                .findFirst();
        assertThat(topShift).isPresent();
        assertThat(read.substitutes(topShift.get())).containsExactly("J Marsh");
    }

    @Test
    void coverForAnEmptyPostListsWhoIsFree() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM)),
                List.of(card("R Diaz", Endorsement.OPERATOR), card("J Marsh", Endorsement.LEAD_OPERATOR)));
        assertThat(read.coverFor(Post.TOP, TimeOfDay.at(12))).containsExactly("J Marsh");
    }

    @Test
    void nobodyFreeMeansAnEmptyListRatherThanAnError() {
        RosterCheck read = clean();
        assertThat(read.coverFor(Post.TOP, TimeOfDay.at(12))).isEmpty();
    }

    @Test
    void aProblemPrintsTheWayASupervisorReadsIt() {
        RosterCheck read = check(
                List.of(shift("R Diaz", Post.BOTTOM), shift("T Woo", Post.TOP)),
                List.of(card("R Diaz", Endorsement.OPERATOR)));
        String printed = read.problemsOf(ProblemKind.UNCARDED).get(0).toString();
        assertThat(printed).endsWith("T Woo, no card for the post");
        assertThat(printed).isEqualTo("8:00 top operator: T Woo, no card for the post");
        assertThat(read.problemsOf(ProblemKind.UNCOVERED)).isEmpty();
    }

    @Test
    void theCheckPrintsACountWhenItIsNotClear() {
        RosterCheck read = check(List.of(shift("R Diaz", Post.BOTTOM)), List.of());
        assertThat(read.toString()).contains("problems");
        assertThat(read).hasToString("JP 2025-01-15, 2 problems");
    }
}
