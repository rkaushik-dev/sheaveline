package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.models.LiftRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoriesTest {

    private Session session;

    @BeforeEach
    void openASession() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void closeIt() {
        session.close();
    }

    private LiftRepository stocked() {
        LiftRepository lifts = new LiftRepository(session);
        lifts.add(johnPaul());
        lifts.add(strawberry());
        session.commit();
        return lifts;
    }

    private static WearSurvey survey(HaulRope rope, LocalDate taken, String inches) {
        return WearSurvey.of(
                rope,
                taken,
                "R Halvorsen",
                List.of(new DiameterReading(Length.ofFeet(0), Length.ofInches(inches), Length.ofInches(inches))));
    }

    private static WearSurvey survey(HaulRope rope) {
        return survey(rope, LocalDate.of(2025, 1, 15), "1.376");
    }

    private static Incident incident(LocalDateTime at, LiftCode lift) {
        return new Incident(lift, at, IncidentKind.DEROPEMENT, InjuryLevel.NONE, 0, "chair 34 off at T4", "K Ostler");
    }

    private static Incident incident(LocalDateTime at) {
        return incident(at, JOHN_PAUL);
    }

    private static WorkOrder order(int number, Priority priority) {
        return new WorkOrder(
                number,
                JOHN_PAUL,
                LocalDate.of(2025, 1, 15),
                "M Vetter",
                "sheave liner cracked",
                priority,
                Optional.of("JP-T4"),
                Optional.empty(),
                Optional.empty());
    }

    private static WorkOrder order() {
        return order(1, Priority.SCHEDULED);
    }

    @Test
    void aLiftGoesInAndComesBackOut() {
        LiftRepository lifts = stocked();
        Lift found = lifts.get(JOHN_PAUL);
        assertThat(found.name()).isEqualTo("John Paul");
        assertThat(found.carriersOnLine()).isEqualTo(84);
        assertThat(lifts.exists(JOHN_PAUL)).isTrue();
    }

    @Test
    void aLiftThatIsNotThere() {
        LiftRepository lifts = new LiftRepository(session);
        assertThatThrownBy(() -> lifts.get(JOHN_PAUL))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no lift JP");
        assertThat(lifts.exists(JOHN_PAUL)).isFalse();
    }

    @Test
    void aLiftGoesInOnce() {
        LiftRepository lifts = stocked();
        assertThatThrownBy(() -> lifts.add(johnPaul()))
                .isInstanceOf(StorageError.class)
                .hasMessage("lift JP is already on file");
    }

    @Test
    void theLiftsComeBackInCodeOrder() {
        LiftRepository lifts = stocked();
        assertThat(lifts.codes()).containsExactly(JOHN_PAUL, STRAWBERRY);
        assertThat(lifts.all().stream().map(Lift::code)).containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void addingALiftPutsItsRopeOnFile() {
        stocked();
        RopeRepository ropes = new RopeRepository(session);
        assertThat(ropes.exists(johnPaul().rope().serial())).isTrue();
        assertThat(ropes.onLift(JOHN_PAUL).stream().map(HaulRope::serial))
                .containsExactly(johnPaul().rope().serial());
    }

    @Test
    void aRopeThatIsOnFileAlreadyIsNotAddedTwice() {
        stocked();
        RopeRepository ropes = new RopeRepository(session);
        assertThatThrownBy(() -> ropes.add(johnPaul().rope(), JOHN_PAUL))
                .isInstanceOf(StorageError.class)
                .hasMessage("rope FAT-2019-04412 is already on file");
    }

    @Test
    void aSpareRopeSitsOnNoMachine() {
        stocked();
        RopeRepository ropes = new RopeRepository(session);
        HaulRope spare = wildcat().rope();
        ropes.add(spare);
        session.commit();
        assertThat(ropes.spares().stream().map(HaulRope::serial)).containsExactly(spare.serial());
        assertThat(ropes.all()).hasSize(3);
    }

    @Test
    void aRopeCanBeTakenOffFile() {
        RopeRepository ropes = new RopeRepository(session);
        RopeSerial serial = wildcat().rope().serial();
        ropes.add(wildcat().rope());
        session.commit();
        ropes.remove(serial);
        session.commit();
        assertThat(ropes.exists(serial)).isFalse();
        assertThatThrownBy(() -> ropes.remove(serial)).isInstanceOf(StorageError.class);
    }

    @Test
    void aRopeThatIsNotThere() {
        assertThatThrownBy(() -> new RopeRepository(session).get(RopeSerial.parse("BRI-2011-01887")))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no rope BRI-2011-01887");
    }

    @Test
    void aLiftCanBeTakenOffFile() {
        LiftRepository lifts = stocked();
        lifts.remove(STRAWBERRY);
        session.commit();
        assertThat(lifts.codes()).containsExactly(JOHN_PAUL);
        assertThatThrownBy(() -> lifts.remove(STRAWBERRY)).isInstanceOf(StorageError.class);
    }

    @Test
    void wearSurveysComeBackInTheOrderTheyWereWalked() {
        stocked();
        HaulRope rope = johnPaul().rope();
        WearSurveyRepository surveys = new WearSurveyRepository(session);
        surveys.add(survey(rope, LocalDate.of(2025, 1, 15), "1.376"));
        surveys.add(survey(rope, LocalDate.of(2024, 12, 1), "1.377"));
        session.commit();
        assertThat(surveys.forRope(rope).stream().map(WearSurvey::taken))
                .containsExactly(LocalDate.of(2024, 12, 1), LocalDate.of(2025, 1, 15));
        assertThat(surveys.latest(rope).taken()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(surveys.countFor(rope)).isEqualTo(2);
    }

    @Test
    void aSurveyCanBeFetchedByTheDayItWasTaken() {
        stocked();
        HaulRope rope = johnPaul().rope();
        WearSurveyRepository surveys = new WearSurveyRepository(session);
        surveys.add(survey(rope));
        session.commit();
        assertThat(surveys.on(rope, LocalDate.of(2025, 1, 15)).inspector()).isEqualTo("R Halvorsen");
        assertThatThrownBy(() -> surveys.on(rope, LocalDate.of(2025, 2, 1)))
                .isInstanceOf(StorageError.class)
                .hasMessage("rope FAT-2019-04412 was not walked on 2025-02-01");
    }

    @Test
    void aRopeNeverWalkedHasNoLatestSurvey() {
        stocked();
        assertThatThrownBy(() ->
                        new WearSurveyRepository(session).latest(johnPaul().rope()))
                .isInstanceOf(StorageError.class)
                .hasMessage("rope FAT-2019-04412 has never been walked");
    }

    @Test
    void incidentsComeBackByMachineAndInOrder() {
        stocked();
        IncidentRepository incidents = new IncidentRepository(session);
        incidents.add(incident(LocalDateTime.of(2025, 1, 15, 14, 0)));
        incidents.add(incident(LocalDateTime.of(2025, 1, 15, 11, 30)));
        incidents.add(incident(LocalDateTime.of(2025, 1, 15, 11, 30), STRAWBERRY));
        session.commit();
        assertThat(incidents.forLift(JOHN_PAUL).stream().map(one -> one.at().getHour()))
                .containsExactly(11, 14);
        assertThat(incidents.forLift(STRAWBERRY)).hasSize(1);
    }

    @Test
    void everythingStartsUnfiled() {
        stocked();
        IncidentRepository incidents = new IncidentRepository(session);
        incidents.add(incident(LocalDateTime.of(2025, 1, 15, 11, 30)));
        session.commit();
        assertThat(incidents.unfiled()).hasSize(1);
    }

    @Test
    void incidentsCanBePulledOutByDate() {
        stocked();
        IncidentRepository incidents = new IncidentRepository(session);
        incidents.add(incident(LocalDateTime.of(2025, 1, 15, 11, 30)));
        incidents.add(incident(LocalDateTime.of(2025, 3, 1, 11, 30)));
        session.commit();
        assertThat(incidents.between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                .hasSize(1);
    }

    @Test
    void workOrdersTakeTheNextNumber() {
        stocked();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        assertThat(orders.nextNumber()).isEqualTo(1);
        orders.add(order());
        session.commit();
        assertThat(orders.nextNumber()).isEqualTo(2);
    }

    @Test
    void aWorkOrderGoesInOnce() {
        stocked();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        orders.add(order());
        session.commit();
        assertThatThrownBy(() -> orders.add(order()))
                .isInstanceOf(StorageError.class)
                .hasMessage("work order 1 is already on file");
    }

    @Test
    void aWorkOrderComesBackTheWayItWentIn() {
        stocked();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        orders.add(order());
        session.commit();
        WorkOrder found = orders.get(1);
        assertThat(found.priority()).isSameAs(Priority.SCHEDULED);
        assertThat(found.where()).contains("JP-T4");
        assertThat(found.isOpen()).isTrue();
    }

    @Test
    void closingAWorkOrderWritesItBack() {
        stocked();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        orders.add(order());
        session.commit();
        orders.close(1, LocalDate.of(2025, 1, 20), "M Vetter");
        session.commit();
        assertThat(orders.get(1).isOpen()).isFalse();
        assertThat(orders.openOrders()).isEmpty();
    }

    @Test
    void theOpenOrdersCanBeNarrowedAndSorted() {
        stocked();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        orders.add(order(1, Priority.URGENT));
        orders.add(order(2, Priority.ROUTINE));
        session.commit();
        assertThat(orders.openOrders(JOHN_PAUL).stream().map(WorkOrder::number)).containsExactly(1, 2);
        assertThat(orders.openOrders(STRAWBERRY)).isEmpty();
        assertThat(orders.ofPriority(Priority.URGENT).stream().map(WorkOrder::number))
                .containsExactly(1);
    }

    @Test
    void aWorkOrderThatIsNotThere() {
        assertThatThrownBy(() -> new WorkOrderRepository(session).get(9))
                .isInstanceOf(StorageError.class)
                .hasMessage("there is no work order 9");
        assertThatThrownBy(() -> new WorkOrderRepository(session).close(9, LocalDate.of(2025, 1, 20), "M Vetter"))
                .isInstanceOf(StorageError.class);
    }

    @Test
    void aRopeTakenOffAMachineStaysOnFileAsASpare() {
        LiftRepository lifts = stocked();
        RopeRepository ropes = new RopeRepository(session);
        RopeSerial serial = johnPaul().rope().serial();
        lifts.remove(JOHN_PAUL);
        session.flush();
        assertThat(ropes.exists(serial)).isTrue();
        assertThat(ropes.spares().stream().map(HaulRope::serial)).contains(serial);
    }

    @Test
    void puttingTheMachineBackClaimsItsRopeAgain() {
        LiftRepository lifts = stocked();
        RopeRepository ropes = new RopeRepository(session);
        RopeSerial serial = johnPaul().rope().serial();
        lifts.remove(JOHN_PAUL);
        session.flush();
        lifts.add(johnPaul());
        session.commit();
        assertThat(lifts.get(JOHN_PAUL).rope().serial()).isEqualTo(serial);
        assertThat(ropes.spares()).isEmpty();
    }

    @Test
    void aRopeAlreadyOnAnotherMachineCannotBeClaimed() {
        stocked();
        RopeRepository ropes = new RopeRepository(session);
        assertThatThrownBy(() -> ropes.attach(johnPaul().rope().serial(), STRAWBERRY))
                .isInstanceOf(StorageError.class)
                .hasMessage("rope FAT-2019-04412 is already on JP");
    }

    @Test
    void aRopeCanBeTakenOffAMachineWithoutBeingDeleted() {
        stocked();
        RopeRepository ropes = new RopeRepository(session);
        RopeSerial serial = johnPaul().rope().serial();
        ropes.detach(serial);
        session.flush();
        assertThat(ropes.spares().stream().map(HaulRope::serial)).contains(serial);
        assertThatThrownBy(() -> ropes.detach(RopeSerial.parse("FAT-2019-99999")))
                .isInstanceOf(StorageError.class);
    }

    @Test
    void everyRepositorySharesTheOnePieceOfSessionPlumbing() {
        List<Function<Session, Repository>> kinds = List.of(
                LiftRepository::new,
                RopeRepository::new,
                WearSurveyRepository::new,
                IncidentRepository::new,
                WorkOrderRepository::new);
        for (Function<Session, Repository> kind : kinds) {
            Repository made = kind.apply(session);
            assertThat(made.getClass().getSuperclass()).isEqualTo(Repository.class);
            assertThat(made.session()).isSameAs(session);
        }
    }

    @Test
    void theFetchHelperSaysWhichRowWasAskedFor() {
        Repository plumbing = new Repository(session);
        assertThatThrownBy(() -> plumbing.fetch(LiftRow.class, JOHN_PAUL, "there is no lift JP"))
                .isInstanceOf(StorageError.class)
                .hasMessageContaining("there is no lift JP");
    }

    @Test
    void theFindHelperHandsBackNothingRatherThanComplaining() {
        assertThat(new Repository(session).find(LiftRow.class, JOHN_PAUL)).isEmpty();
        stocked();
        assertThat(new Repository(session).find(LiftRow.class, JOHN_PAUL)).isPresent();
    }

    @Test
    void theOneHelperComplainsWhenAQueryFindsNothing() {
        Repository plumbing = new Repository(session);
        assertThatThrownBy(() -> plumbing.one(
                        plumbing.query("select l from LiftRow l where l.name = :name", LiftRow.class)
                                .setParameter("name", "Nothing"),
                        "no such machine"))
                .isInstanceOf(StorageError.class)
                .hasMessage("no such machine");
    }

    @Test
    void theManyHelperHandsBackAList() {
        stocked();
        Repository plumbing = new Repository(session);
        List<LiftRow> found = plumbing.many(plumbing.query("select l from LiftRow l order by l.code", LiftRow.class));
        assertThat(found.stream().map(LiftRow::getCode)).containsExactly(JOHN_PAUL, STRAWBERRY);
    }
}
