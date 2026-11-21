package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.all;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.assertRefused;
import static io.github.rkaushikdev.sheaveline.persistence.TestDatabase.one;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.JP;
import static io.github.rkaushikdev.sheaveline.persistence.models.Rows.johnPaul;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.EntryKind;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.Session;
import io.github.rkaushikdev.sheaveline.persistence.TestDatabase;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelOpsTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 15);

    private Database database;

    @BeforeEach
    void johnPaulOnFile() {
        database = TestDatabase.fresh();
        try (Session session = database.session()) {
            session.persist(johnPaul());
            session.commit();
        }
    }

    private static DayLogRow dayLog() {
        DayLogRow book = new DayLogRow(JP, DAY, TimeOfDay.at(9), TimeOfDay.at(15, 45), TimeOfDay.at(16, 15));
        book.setMechanic("M Vetter");
        book.setOperator("K Ostler");
        return book;
    }

    private static StopRow stopRow(StopKind kind, LocalDateTime stopped) {
        return new StopRow(JP, kind, stopped, "K Ostler");
    }

    private static HoldRow gustHold() {
        HoldRow hold = new HoldRow(JP, HoldReason.GUST, LocalDateTime.of(2025, 1, 15, 16, 0), "K Ostler");
        hold.setDetail("52 mph against a limit of 45 mph");
        return hold;
    }

    private static MeterReadingRow meterReading(String hours, int cycles) {
        return new MeterReadingRow(JP, DAY, new BigDecimal(hours), cycles, "M Vetter");
    }

    @Test
    void theSchemaCarriesEveryOperatingTable() {
        assertThat(database.existingTables())
                .contains(
                        "day_logs", "log_entries", "preop_results", "stops", "holds", "meter_readings", "assignments");
    }

    @Test
    void aDayLogTakesItsLinesAndItsChecksWithIt() {
        try (Session session = database.session()) {
            DayLogRow book = dayLog();
            book.setOpened(TimeOfDay.at(9));
            book.setClosed(TimeOfDay.at(16));
            book.addEntry(new LogEntryRow(
                    LocalDateTime.of(2025, 1, 15, 9, 0), EntryKind.OPENED, "first load at 9:00", "K Ostler"));
            book.addCheck(new PreOpResultRow("B1", CheckOutcome.PASS, "M Vetter"));
            session.persist(book);
            session.commit();
        }
        try (Session session = database.session()) {
            DayLogRow found = one(session, DayLogRow.class);
            assertThat(found.getEntries()).hasSize(1);
            assertThat(found.getChecks()).hasSize(1);
            assertThat(found.getOpened()).contains(TimeOfDay.at(9));
            assertThat(found.getLift().getName()).isEqualTo("John Paul");
        }
    }

    @Test
    void oneDayLogAMachineADay() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(dayLog());
                session.persist(dayLog());
                session.commit();
            });
        }
    }

    @Test
    void theWindowHasToRunForwards() {
        try (Session session = database.session()) {
            DayLogRow book = dayLog();
            book.setLastLoad(TimeOfDay.at(8));
            assertRefused(() -> {
                session.persist(book);
                session.commit();
            });
        }
    }

    @Test
    void theSweepComesAfterTheLastLoad() {
        try (Session session = database.session()) {
            DayLogRow book = dayLog();
            book.setSweepBy(TimeOfDay.at(15));
            assertRefused(() -> {
                session.persist(book);
                session.commit();
            });
        }
    }

    @Test
    void anItemIsWalkedOnceADay() {
        try (Session session = database.session()) {
            DayLogRow book = dayLog();
            book.addCheck(new PreOpResultRow("B1", CheckOutcome.PASS, "M Vetter"));
            book.addCheck(new PreOpResultRow("B1", CheckOutcome.PASS, "M Vetter"));
            assertRefused(() -> {
                session.persist(book);
                session.commit();
            });
        }
    }

    @Test
    void aStopSitsAgainstTheLiftRatherThanTheDay() {
        try (Session session = database.session()) {
            StopRow stop = stopRow(StopKind.WEATHER, LocalDateTime.of(2025, 1, 15, 16, 0));
            stop.setRestarted(LocalDateTime.of(2025, 1, 16, 9, 0));
            stop.setRestartedBy("K Ostler");
            session.persist(stop);
            session.commit();
        }
        try (Session session = database.session()) {
            StopRow found = one(session, StopRow.class);
            assertThat(found.getStopped().getDayOfMonth()).isEqualTo(15);
            assertThat(found.getRestarted())
                    .get()
                    .extracting(LocalDateTime::getDayOfMonth)
                    .isEqualTo(16);
        }
    }

    @Test
    void aStopCannotRestartBeforeItStopped() {
        try (Session session = database.session()) {
            StopRow stop = stopRow(StopKind.GATE, LocalDateTime.of(2025, 1, 15, 12, 0));
            stop.setRestarted(LocalDateTime.of(2025, 1, 15, 11, 0));
            stop.setRestartedBy("K Ostler");
            assertRefused(() -> {
                session.persist(stop);
                session.commit();
            });
        }
    }

    @Test
    void twoStopsCannotShareAMomentOnOneMachine() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                for (int twice = 0; twice < 2; twice++) {
                    session.persist(stopRow(StopKind.GATE, LocalDateTime.of(2025, 1, 15, 12, 0)));
                }
                session.commit();
            });
        }
    }

    @Test
    void aHoldRunsAcrossTheEndOfTheDay() {
        try (Session session = database.session()) {
            session.persist(gustHold());
            session.commit();
        }
        try (Session session = database.session()) {
            HoldRow found = one(session, HoldRow.class);
            assertThat(found.getReason()).isSameAs(HoldReason.GUST);
            assertThat(found.getEnded()).isEmpty();
        }
    }

    @Test
    void aHoldCannotEndBeforeItStarts() {
        try (Session session = database.session()) {
            HoldRow hold = gustHold();
            hold.setEnded(LocalDateTime.of(2025, 1, 15, 15, 0));
            hold.setEndedBy("K Ostler");
            assertRefused(() -> {
                session.persist(hold);
                session.commit();
            });
        }
    }

    @Test
    void oneMeterReadingAMachineADay() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                for (int twice = 0; twice < 2; twice++) {
                    session.persist(meterReading("42000.0", 1_000_000));
                }
                session.commit();
            });
        }
    }

    @Test
    void aMeterReadingKeepsItsTenthOfAnHour() {
        try (Session session = database.session()) {
            session.persist(meterReading("42009.5", 1_000_400));
            session.commit();
        }
        try (Session session = database.session()) {
            MeterReadingRow found = one(session, MeterReadingRow.class);
            assertThat(found.getHours()).isEqualByComparingTo("42009.5");
        }
    }

    @Test
    void aNegativeMeterReadingIsRefused() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(meterReading("-1.0", 0));
                session.commit();
            });
        }
    }

    @Test
    void aShiftRunsForwards() {
        try (Session session = database.session()) {
            assertRefused(() -> {
                session.persist(new AssignmentRow(JP, DAY, "R Diaz", Post.BOTTOM, TimeOfDay.at(16), TimeOfDay.at(9)));
                session.commit();
            });
        }
    }

    @Test
    void twoShiftsForOnePersonCanStartAtDifferentTimes() {
        try (Session session = database.session()) {
            session.persist(new AssignmentRow(JP, DAY, "R Diaz", Post.BOTTOM, TimeOfDay.at(8), TimeOfDay.at(12)));
            session.persist(new AssignmentRow(JP, DAY, "R Diaz", Post.TOP, TimeOfDay.at(12), TimeOfDay.at(17)));
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, AssignmentRow.class)).hasSize(2);
        }
    }

    @Test
    void deletingADayLogTakesItsLinesWithIt() {
        try (Session session = database.session()) {
            DayLogRow book = dayLog();
            book.addEntry(new LogEntryRow(
                    LocalDateTime.of(2025, 1, 15, 11, 0), EntryKind.NOTE, "wind picking up", "K Ostler"));
            session.persist(book);
            session.commit();
            session.remove(book);
            session.commit();
        }
        try (Session session = database.session()) {
            assertThat(all(session, LogEntryRow.class)).isEmpty();
        }
    }
}
