package io.github.rkaushikdev.sheaveline.persistence.mappers;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.AssignmentRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CertificateClassRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CertificateRow;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cards and shifts turned into rows and back, and the rows they refuse to read. */
class PeopleMapperTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");

    private static Certificate card(Set<LiftClass> classes) {
        return new Certificate(
                "D Chin",
                Endorsement.OPERATOR,
                LocalDate.of(2024, 11, 1),
                LocalDate.of(2025, 11, 1),
                "Utah tramway board",
                classes);
    }

    private static CertificateRow bareRow() {
        return new CertificateRow(
                "D Chin", Endorsement.OPERATOR, LocalDate.of(2024, 11, 1), LocalDate.of(2025, 11, 1), "R Skaggs");
    }

    private static Assignment shift() {
        return new Assignment("D Chin", Post.BOTTOM, TimeOfDay.parse("08:30"), TimeOfDay.parse("16:45"));
    }

    @Test
    void theClassesACardCoversAreWrittenInTheirOwnWords() {
        Certificate before = card(Set.of(LiftClass.SURFACE_TOW, LiftClass.DETACHABLE_CHAIR));
        CertificateRow row = PeopleMapper.certificateToRow(before);
        assertThat(row.getClasses().stream().map(CertificateClassRow::getLiftClass))
                .containsExactly("detachable chairlift", "surface lift");
        assertThat(PeopleMapper.certificateFromRow(row)).isEqualTo(before);
        assertThat(PeopleMapper.personnelFromRows(List.of(row)).size()).isEqualTo(1);
    }

    @Test
    void aCardWithNoClassesWrittenCoversEveryMachine() {
        assertThat(PeopleMapper.certificateFromRow(bareRow()).classes()).isEqualTo(PeopleMapper.EVERY_CLASS);
    }

    @Test
    void aClassThereIsNoSuchThingAsIsRefused() {
        CertificateRow row = bareRow();
        row.addClass(new CertificateClassRow("gondola"));
        assertThatThrownBy(() -> PeopleMapper.certificateFromRow(row))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no lift class called gondola");
    }

    @Test
    void aRosterIsAssembledOnlyFromItsOwnMachineAndDay() {
        AssignmentRow here = PeopleMapper.assignmentToRow(JOHN_PAUL, ON, shift());
        assertThat(PeopleMapper.rosterFromRows(JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, ON, WINDOW, List.of(here))
                        .assignments())
                .containsExactly(shift());
        AssignmentRow elsewhere = PeopleMapper.assignmentToRow(STRAWBERRY, ON, shift());
        assertThatThrownBy(() -> PeopleMapper.rosterFromRows(
                        JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, ON, WINDOW, List.of(here, elsewhere)))
                .isInstanceOf(StorageError.class)
                .hasMessage("that shift is on SB and not JP");
        AssignmentRow tomorrow = PeopleMapper.assignmentToRow(JOHN_PAUL, ON.plusDays(1), shift());
        assertThatThrownBy(() -> PeopleMapper.rosterFromRows(
                        JOHN_PAUL, LiftClass.DETACHABLE_CHAIR, ON, WINDOW, List.of(tomorrow)))
                .isInstanceOf(StorageError.class)
                .hasMessage("that shift is on 2025-01-16 and not 2025-01-15");
    }
}
