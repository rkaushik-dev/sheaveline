package io.github.rkaushikdev.sheaveline;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.WILDCAT;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Office.Machine;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The template is only worth having if a test cannot see what another one wrote. */
class OfficeTest {

    @TempDir
    Path tmp;

    private static List<LiftCode> codes(String url) {
        try (Database database = Database.open(url);
                UnitOfWork work = UnitOfWork.on(database).open()) {
            return work.lifts().codes();
        }
    }

    @Test
    void theWholeHillIsOnTheRegisterByDefault() {
        assertThat(codes(Office.stocked(tmp))).containsExactly(JOHN_PAUL, STRAWBERRY, WILDCAT);
    }

    @Test
    void onlyTheMachinesThatWereAskedFor() {
        assertThat(codes(Office.stocked(tmp, Machine.JOHN_PAUL))).containsExactly(JOHN_PAUL);
        assertThat(codes(Office.stocked(tmp.resolve("two"), Machine.JOHN_PAUL, Machine.STRAWBERRY)))
                .containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void anEmptyDatabaseStillHasEveryTable() {
        List<String> built;
        try (Database database = Database.open(Office.empty(tmp))) {
            built = database.existingTables();
        }
        assertThat(Database.tableNames()).isNotEmpty();
        assertThat(built).containsAll(Database.tableNames());
    }

    @Test
    void whatOneCopyWritesDoesNotReachTheNext() {
        String first = Office.stocked(tmp.resolve("first"), Machine.JOHN_PAUL, Machine.STRAWBERRY);
        try (Database database = Database.open(first);
                UnitOfWork work = UnitOfWork.on(database).open()) {
            work.lifts().remove(JOHN_PAUL);
            work.commit();
        }
        assertThat(codes(first)).containsExactly(STRAWBERRY);
        assertThat(codes(Office.stocked(tmp.resolve("second"), Machine.JOHN_PAUL, Machine.STRAWBERRY)))
                .containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void theTemplateIsBuiltOnceAndThenHandedBack() {
        Consumer<UnitOfWork> fill = work -> work.lifts().add(Hill.johnPaul());
        Path first = Office.template("one-machine", fill);
        assertThat(Office.template("one-machine", fill)).isSameAs(first);
    }

    @Test
    void twoCopiesOfTheSameTemplateAreDifferentFiles() {
        String one = Office.copied(tmp.resolve("one"), "empty", work -> {});
        String two = Office.copied(tmp.resolve("two"), "empty", work -> {});
        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void theWholeHillIsTheThreeMachinesTheFixturesUse() {
        assertThat(Office.WHOLE_HILL).extracting(Machine::label).containsExactly("john_paul", "strawberry", "wildcat");
        assertThat(Office.WHOLE_HILL)
                .extracting(Machine::get)
                .extracting(Lift::code)
                .containsExactly(JOHN_PAUL, STRAWBERRY, WILDCAT);
    }
}
