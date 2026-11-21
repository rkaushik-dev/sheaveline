package io.github.rkaushikdev.sheaveline.reports;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.STRAWBERRY;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.board.Incident;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BoardpacketTest {

    private static final LocalDate PREPARED = LocalDate.of(2025, 5, 12);

    private static Machine machine() {
        return new Machine();
    }

    private static Filed incident() {
        return new Filed();
    }

    private static BoardPacket packet(Machine... machines) {
        return packet(List.of(machines), List.of());
    }

    private static BoardPacket packet(List<Machine> machines, List<Filed> incidents) {
        List<MachineYear> years = machines.stream().map(Machine::year).toList();
        return BoardPacket.of(
                "2024-25",
                "Ogden Peak",
                PREPARED,
                "R Skaggs",
                years.isEmpty() ? List.of(machine().year()) : years,
                incidents.stream().map(Filed::made).toList());
    }

    private static BoardPacket packetWith(Filed... incidents) {
        return packet(List.of(), List.of(incidents));
    }

    @Test
    void aMachineWithNothingOutstandingIsClean() {
        assertThat(machine().year().isClean()).isTrue();
        assertThat(machine().year().concerns()).isEmpty();
    }

    @Test
    void aMachineOutOfServiceIsNamed() {
        MachineYear one = machine().status(ServiceStatus.OUT_OF_SERVICE).year();
        assertThat(one.isClean()).isFalse();
        assertThat(one.concerns()).contains("JP is out of service");
    }

    @Test
    void overdueInspectionsAndOpenDefectsAreBothCounted() {
        MachineYear one = machine().overdue(2).defects(3).year();
        assertThat(one.concerns()).contains("JP has 2 inspections overdue");
        assertThat(one.concerns()).contains("JP has 3 defects open");
    }

    @Test
    void aRopeThatIsNotServiceableReachesTheBoard() {
        MachineYear one = machine().verdict("on watch").year();
        assertThat(one.concerns()).anySatisfy(line -> assertThat(line).contains("rope FAT-2019-04412 is on watch"));
    }

    @Test
    void theMachinesComeBackInCodeOrder() {
        BoardPacket made = packet(machine().lift(STRAWBERRY, "Strawberry"), machine());
        assertThat(made.machines()).extracting(MachineYear::lift).containsExactly(JOHN_PAUL, STRAWBERRY);
    }

    @Test
    void theDaysOpenIsTheBusiestMachine() {
        BoardPacket made = packet(
                machine().days(142), machine().lift(STRAWBERRY, "Strawberry").days(96));
        assertThat(made.daysOpen()).isEqualTo(142);
    }

    @Test
    void aPacketWithNoMachinesSaysNothingRatherThanFailing() {
        BoardPacket made = BoardPacket.of("2024-25", "Ogden Peak", PREPARED, "R Skaggs");
        assertThat(made.daysOpen()).isZero();
        assertThat(made.isClean()).isTrue();
        assertThat(made.text()).contains("nothing was reportable this season");
    }

    @Test
    void aCleanSeasonSaysSo() {
        BoardPacket made = packet();
        assertThat(made.isClean()).isTrue();
        assertThat(made.text()).contains("nothing outstanding");
    }

    @Test
    void anUnfiledIncidentIsOutstanding() {
        BoardPacket made = packetWith(incident().unfiled());
        assertThat(made.unfiled()).isNotEmpty();
        assertThat(made.isClean()).isFalse();
        assertThat(made.text()).contains("NOT FILED");
        assertThat(made.text()).contains("has not been filed");
    }

    @Test
    void aFilingThatWentInLateIsOutstanding() {
        Filed late = incident().kind(IncidentKind.DEROPEMENT).filedOn(LocalDate.of(2025, 2, 1));
        BoardPacket made = packetWith(late);
        assertThat(made.lateFilings()).isNotEmpty();
        assertThat(made.text()).contains("after the deadline");
    }

    @Test
    void aFilingThatWentInOnTimeIsNot() {
        Filed prompt = incident().kind(IncidentKind.DEROPEMENT).filedOn(LocalDate.of(2025, 1, 15));
        assertThat(packetWith(prompt).lateFilings()).isEmpty();
    }

    @Test
    void anIncidentNobodyHasFiledIsNotCountedAsLate() {
        assertThat(packetWith(incident().unfiled()).lateFilings()).isEmpty();
    }

    @Test
    void theIncidentsComeBackOldestFirst() {
        BoardPacket made = packetWith(incident().hour(15), incident().hour(9));
        assertThat(made.incidents())
                .extracting(one -> one.incident().at().getHour())
                .containsExactly(9, 15);
    }

    @Test
    void thePageIsEightyColumns() {
        BoardPacket made = packet(List.of(machine(), machine().lift(STRAWBERRY, "Strawberry")), List.of(incident()));
        assertThat(made.render().lines().stream().mapToInt(String::length).max().orElseThrow())
                .isLessThanOrEqualTo(Forms.PAGE_WIDTH);
    }

    @Test
    void thePageAnswersTheFourQuestionsInOrder() {
        List<String> wanted = List.of("machines", "ropes", "evacuation", "incidents", "outstanding");
        Stream<String> lines = packetWith(incident()).text().lines();
        assertThat(lines.filter(wanted::contains)).containsExactlyElementsOf(wanted);
    }

    @Test
    void aMachineThatHasNotBeenDrilledSaysSo() {
        assertThat(packet(machine()).text()).contains("not drilled");
        assertThat(packet(machine().clears(Duration.ofMinutes(44))).text()).contains("44:00");
    }

    @Test
    void theCleanMachinesAreTheOnesWithNothingAgainstThem() {
        BoardPacket made =
                packet(machine(), machine().lift(STRAWBERRY, "Strawberry").defects(1));
        assertThat(made.cleanMachines()).extracting(MachineYear::lift).containsExactly(JOHN_PAUL);
    }

    /** One machine's year, built the way a test wants it. */
    private static final class Machine {

        private LiftCode lift = JOHN_PAUL;
        private String name = "John Paul";
        private int days = 142;
        private ServiceStatus status = ServiceStatus.IN_SERVICE;
        private int overdue;
        private int defects;
        private String verdict = "serviceable";
        private Optional<Duration> clears = Optional.empty();

        Machine lift(LiftCode code, String called) {
            lift = code;
            name = called;
            return this;
        }

        Machine days(int value) {
            days = value;
            return this;
        }

        Machine status(ServiceStatus value) {
            status = value;
            return this;
        }

        Machine overdue(int value) {
            overdue = value;
            return this;
        }

        Machine defects(int value) {
            defects = value;
            return this;
        }

        Machine verdict(String value) {
            verdict = value;
            return this;
        }

        Machine clears(Duration value) {
            clears = Optional.of(value);
            return this;
        }

        MachineYear year() {
            return new MachineYear(
                    lift,
                    name,
                    days,
                    Duration.ofHours(1065),
                    Duration.ofHours(11),
                    new BigDecimal("0.99"),
                    status,
                    overdue,
                    defects,
                    "FAT-2019-04412",
                    verdict,
                    clears);
        }
    }

    /** One incident and its filing, built the way a test wants it. */
    private static final class Filed {

        private int hour = 11;
        private IncidentKind kind = IncidentKind.LOADING;
        private Optional<FilingNumber> filing = Optional.of(FilingNumber.parse("UT-2025-0001"));
        private Optional<LocalDate> filedOn = Optional.empty();

        Filed hour(int value) {
            hour = value;
            return this;
        }

        Filed kind(IncidentKind value) {
            kind = value;
            return this;
        }

        Filed unfiled() {
            filing = Optional.empty();
            return this;
        }

        Filed filedOn(LocalDate value) {
            filedOn = Optional.of(value);
            return this;
        }

        FiledIncident made() {
            Incident incident = new Incident(
                    JOHN_PAUL,
                    LocalDateTime.of(2025, 1, 15, hour, 30),
                    kind,
                    InjuryLevel.NONE,
                    1,
                    "ski under the bail on loading",
                    "D Chin");
            return new FiledIncident(incident, filing, filedOn);
        }
    }
}
