package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.board.Filing;
import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.persistence.models.FilingRow;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Incidents addressed by their ids, and filed with the board under the next number. */
class IncidentFilingTest {

    private static final LocalDateTime FILED = LocalDateTime.of(2025, 1, 15, 15, 0);

    private UnitOfWork work;
    private IncidentRepository incidents;

    @BeforeEach
    void bothMachinesOnFile() {
        work = UnitOfWork.on(TestDatabase.fresh()).open();
        LiftRepository lifts = work.lifts();
        lifts.add(johnPaul());
        lifts.add(strawberry());
        work.commit();
        incidents = work.incidents();
    }

    @AfterEach
    void closeIt() {
        work.close();
    }

    private static Incident incident(LiftCode lift, int hour) {
        return new Incident(
                lift,
                LocalDateTime.of(2025, 1, 15, hour, 30),
                IncidentKind.DEROPEMENT,
                InjuryLevel.NONE,
                0,
                "chair 34 off at T4",
                "K Ostler");
    }

    @Test
    void anIncidentRecordedHandsBackTheIdItIsFoundBy() {
        Incident before = incident(JOHN_PAUL, 11);
        int id = incidents.record(before);
        work.commit();
        IncidentRepository.Numbered found = incidents.get(id);
        assertThat(found.id()).isEqualTo(id);
        assertThat(found.incident()).isEqualTo(before);
        assertThat(found.filingNumber()).isEmpty();
        assertThat(incidents.numberedForLift(JOHN_PAUL).stream().map(IncidentRepository.Numbered::id))
                .containsExactly(id);
        assertThat(incidents.numberedForLift(STRAWBERRY)).isEmpty();
    }

    @Test
    void filingAnIncidentTakesTheNextBoardNumber() {
        int first = incidents.record(incident(JOHN_PAUL, 9));
        int second = incidents.record(incident(STRAWBERRY, 10));
        work.commit();
        Filing filed = incidents.file(first, FilingKind.INCIDENT, FILED, "K Ostler");
        work.commit();
        assertThat(filed.number()).hasToString("UT-2025-0001");
        assertThat(filed.lift()).isEqualTo(JOHN_PAUL);
        assertThat(filed.about()).isEqualTo("chair 34 off at T4");
        assertThat(incidents.get(first).filingNumber()).contains(FilingNumber.parse("UT-2025-0001"));
        FilingRow row = work.session().find(FilingRow.class, filed.number()).orElseThrow();
        assertThat(row.getKind()).isSameAs(FilingKind.INCIDENT);
        assertThat(row.getLiftCode()).isEqualTo(JOHN_PAUL);
        assertThat(row.getFiled()).isEqualTo(FILED);
        assertThat(row.getFiledBy()).isEqualTo("K Ostler");
        assertThat(row.getAbout()).isEqualTo("chair 34 off at T4");
        assertThat(row.getAcknowledged()).isEmpty();
        assertThat(incidents.numberedUnfiled().stream().map(IncidentRepository.Numbered::id))
                .containsExactly(second);
        assertThat(incidents.unfiled()).hasSize(1);
        assertThat(incidents
                        .file(second, FilingKind.INCIDENT, FILED, "K Ostler")
                        .number())
                .hasToString("UT-2025-0002");
    }

    @Test
    void anIncidentIsFiledOnce() {
        int id = incidents.record(incident(JOHN_PAUL, 9));
        incidents.file(id, FilingKind.INCIDENT, FILED, "K Ostler");
        work.commit();
        assertThatThrownBy(() -> incidents.file(id, FilingKind.INCIDENT, FILED, "K Ostler"))
                .isInstanceOf(StorageError.class)
                .hasMessage("incident " + id + " is already filed");
    }

    @Test
    void anIncidentThatIsNotThere() {
        assertThatThrownBy(() -> incidents.get(99))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no incident 99");
        assertThatThrownBy(() -> incidents.file(99, FilingKind.INCIDENT, FILED, "K Ostler"))
                .isInstanceOf(StorageError.class);
    }
}
