package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.people.PersonnelFile;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepoPeopleTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");

    private Session session;
    private PersonnelRepository people;

    @BeforeEach
    void bothMachinesOnFile() {
        session = TestDatabase.fresh().session();
        LiftRepository lifts = new LiftRepository(session);
        lifts.add(johnPaul());
        lifts.add(strawberry());
        session.commit();
        people = new PersonnelRepository(session);
    }

    @AfterEach
    void closeIt() {
        session.close();
    }

    private static Certificate card(String person, Endorsement endorsement, Set<LiftClass> classes) {
        return new Certificate(
                person,
                endorsement,
                LocalDate.of(2024, 11, 1),
                LocalDate.of(2025, 11, 1),
                "Utah tramway board",
                classes);
    }

    private static Certificate card(String person, Endorsement endorsement) {
        return card(person, endorsement, EnumSet.allOf(LiftClass.class));
    }

    private static Certificate card() {
        return card("D Chin", Endorsement.OPERATOR);
    }

    private static Assignment shift(String person, String starts, String ends) {
        return new Assignment(person, Post.BOTTOM, TimeOfDay.parse(starts), TimeOfDay.parse(ends));
    }

    private static Assignment shift(String person) {
        return shift(person, "08:30", "16:45");
    }

    private static Assignment shift() {
        return shift("D Chin");
    }

    @Test
    void aCardGoesInAndComesBackOut() {
        people.issue(card());
        session.commit();
        List<Certificate> found = people.forPerson("D Chin");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).endorsement()).isSameAs(Endorsement.OPERATOR);
        assertThat(found.get(0).isCurrent(ON)).isTrue();
    }

    @Test
    void theClassesACardCoversSurviveTheRoundTrip() {
        people.issue(card("D Chin", Endorsement.OPERATOR, Set.of(LiftClass.DETACHABLE_CHAIR)));
        session.commit();
        Certificate found = people.forPerson("D Chin").get(0);
        assertThat(found.classes()).isEqualTo(Set.of(LiftClass.DETACHABLE_CHAIR));
        assertThat(found.coversClass(LiftClass.DETACHABLE_CHAIR)).isTrue();
        assertThat(found.coversClass(LiftClass.SURFACE_TOW)).isFalse();
    }

    @Test
    void aCardCoveringEverythingComesBackCoveringEverything() {
        people.issue(card());
        session.commit();
        assertThat(people.forPerson("D Chin").get(0).classes()).isEqualTo(EnumSet.allOf(LiftClass.class));
    }

    @Test
    void theSameCardIsNotIssuedTwice() {
        people.issue(card());
        session.commit();
        assertThatThrownBy(() -> people.issue(card()))
                .isInstanceOf(StorageError.class)
                .hasMessage("D Chin already has a operator card issued 2024-11-01");
    }

    @Test
    void theWholeFileAnswersWhoMayHoldAPost() {
        people.issue(card("D Chin", Endorsement.OPERATOR));
        people.issue(card("R Skaggs", Endorsement.MECHANIC));
        session.commit();
        PersonnelFile held = people.file();
        assertThat(held.qualifiedFor(Post.BOTTOM, LiftClass.DETACHABLE_CHAIR, ON))
                .containsExactly("D Chin");
        assertThat(people.people()).containsExactly("D Chin", "R Skaggs");
    }

    @Test
    void renewingACardWritesANewOneAndKeepsTheOld() {
        people.issue(card());
        session.commit();
        Certificate renewed = people.renew("D Chin", Endorsement.OPERATOR, LocalDate.of(2026, 11, 1), "R Skaggs");
        session.commit();
        assertThat(renewed.expires()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(people.forPerson("D Chin")).hasSize(2);
        assertThat(people.file().currentFor("D Chin", LocalDate.of(2026, 1, 1))).isNotEmpty();
    }

    @Test
    void renewingACardNobodyHolds() {
        assertThatThrownBy(() -> people.renew("D Chin", Endorsement.OPERATOR, LocalDate.of(2026, 11, 1), "R Skaggs"))
                .isInstanceOf(StorageError.class)
                .hasMessageContaining("no operator card to renew");
    }

    @Test
    void aShiftGoesInAndComesBackInTheRoster() {
        people.assign(JOHN_PAUL, ON, shift());
        session.commit();
        Roster roster = people.roster(JOHN_PAUL, ON, WINDOW);
        assertThat(roster.lift()).isEqualTo(JOHN_PAUL);
        assertThat(roster.liftClass()).isSameAs(LiftClass.DETACHABLE_CHAIR);
        assertThat(roster.assignments().stream().map(Assignment::person)).containsExactly("D Chin");
    }

    @Test
    void theSamePersonCannotStartTwiceAtTheSameTime() {
        people.assign(JOHN_PAUL, ON, shift());
        session.commit();
        assertThatThrownBy(() -> people.assign(JOHN_PAUL, ON, shift()))
                .isInstanceOf(StorageError.class)
                .hasMessageContaining("already on JP");
    }

    @Test
    void aRosterOnAMachineThatIsNotOnTheRegister() {
        assertThatThrownBy(() -> people.roster(new LiftCode("ZZ"), ON, WINDOW))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no lift ZZ");
    }

    @Test
    void theRosterIsOnlyTheShiftsOnThatMachineAndThatDay() {
        people.assign(JOHN_PAUL, ON, shift());
        people.assign(STRAWBERRY, ON, shift("T Woo"));
        people.assign(JOHN_PAUL, LocalDate.of(2025, 1, 16), shift("J Marsh"));
        session.commit();
        assertThat(people.roster(JOHN_PAUL, ON, WINDOW).assignments().stream().map(Assignment::person))
                .containsExactly("D Chin");
    }

    @Test
    void whereOnePersonIsDownToBeAcrossTheHill() {
        people.assign(JOHN_PAUL, ON, shift("D Chin", "08:30", "12:30"));
        people.assign(STRAWBERRY, ON, shift("D Chin", "13:00", "16:45"));
        session.commit();
        assertThat(people.working("D Chin", ON).stream().map(PersonnelRepository.Placement::lift))
                .containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void somebodyWhoIsNotWorking() {
        assertThat(people.working("T Woo", ON)).isEmpty();
    }
}
