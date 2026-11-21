package io.github.rkaushikdev.sheaveline.persistence.mappers;

import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.LogEntry;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.DayLogRow;
import io.github.rkaushikdev.sheaveline.persistence.models.HoldRow;
import io.github.rkaushikdev.sheaveline.persistence.models.LogEntryRow;
import io.github.rkaushikdev.sheaveline.persistence.models.MeterReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.PreOpResultRow;
import io.github.rkaushikdev.sheaveline.persistence.models.StopRow;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import io.github.rkaushikdev.sheaveline.weather.HoldRegister;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turning a day on the hill into rows and back again.
 *
 * <p>The day log is the awkward one. It owns its checks and its written entries, so those go down
 * and come back with it, but the stops and the holds do not belong to a day at all. A wind hold
 * called at four and cleared at nine the next morning is one hold across two logs, and the loader
 * takes them as arguments rather than reading them off the row, because only the caller knows how
 * wide a window it means.
 *
 * <p>The pre opening sheet needs the lift itself and not just its code, since the check list depends
 * on what class of machine it is. That is why the loader asks for a {@link Lift} and refuses to
 * guess.
 */
public final class OpsMapper {

    private OpsMapper() {}

    /**
     * A day as a row, with its written entries and the results of its morning walk. The stops and
     * the meter reading are written on their own.
     *
     * @param log the day
     * @return a new row
     */
    public static DayLogRow dayLogToRow(DayLog log) {
        OperatingWindow window = log.window();
        DayLogRow row = new DayLogRow(log.lift(), log.day(), window.firstLoad(), window.lastLoad(), window.sweepBy());
        row.setOpened(log.opened().orElse(null));
        row.setClosed(log.closed().orElse(null));
        log.sheet().ifPresent(sheet -> {
            row.setMechanic(sheet.mechanic());
            row.setOperator(sheet.operator());
        });
        for (LogEntry entry : log.entries()) {
            row.addEntry(new LogEntryRow(entry.at(), entry.kind(), entry.text(), entry.by()));
        }
        log.sheet().ifPresent(sheet -> {
            for (CheckResult result : sheet.results()) {
                PreOpResultRow check = new PreOpResultRow(result.item().code(), result.outcome(), result.by());
                check.setNote(result.note());
                row.addCheck(check);
            }
        });
        return row;
    }

    /**
     * A day back out of storage, with nothing that outlives it handed in.
     *
     * @param row the row
     * @param lift the machine it was logged on
     * @return the day, with no stop log and no meter reading
     * @throws StorageError when the row is for another machine, or its sheet is unsigned or names a
     *     check the machine does not have
     */
    public static DayLog dayLogFromRow(DayLogRow row, Lift lift) {
        return dayLogFromRow(row, lift, Optional.empty(), Optional.empty());
    }

    /**
     * The day back out of storage, with the parts that outlive it handed in.
     *
     * @param row the row
     * @param lift the machine it was logged on
     * @param stops the stops that touched the day, if they were asked for
     * @param meter the meter reading taken that day, if there was one
     * @return the day, its entries in the order they were written
     * @throws StorageError when the row is for another machine, or its sheet is unsigned or names a
     *     check the machine does not have
     */
    public static DayLog dayLogFromRow(
            DayLogRow row, Lift lift, Optional<StopLog> stops, Optional<MeterReading> meter) {
        if (!row.getLiftCode().equals(lift.code())) {
            throw new StorageError("that log is for " + row.getLiftCode() + " and not " + lift.code());
        }
        return new DayLog(
                lift.code(),
                row.getDay(),
                new OperatingWindow(row.getFirstLoad(), row.getLastLoad(), row.getSweepBy()),
                sheetFromRows(row, lift),
                HoldRegister.of(),
                stops,
                row.getEntries().stream()
                        .sorted(Comparator.comparing(LogEntryRow::getAt))
                        .map(one -> new LogEntry(one.getAt(), one.getKind(), one.getText(), one.getBy()))
                        .toList(),
                row.getOpened(),
                row.getClosed(),
                meter);
    }

    private static Optional<PreOpSheet> sheetFromRows(DayLogRow row, Lift lift) {
        if (row.getChecks().isEmpty()) {
            return Optional.empty();
        }
        if (row.getMechanic().isEmpty() || row.getOperator().isEmpty()) {
            throw new StorageError("the sheet for " + row.getDay() + " has checks on it but nobody signed it");
        }
        Map<String, CheckItem> items = new LinkedHashMap<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(lift)) {
                items.put(item.code(), item);
            }
        }
        List<CheckResult> results = new ArrayList<>();
        for (PreOpResultRow check : row.getChecks().stream()
                .sorted(Comparator.comparing(PreOpResultRow::getCode))
                .toList()) {
            CheckItem item = items.get(check.getCode());
            if (item == null) {
                throw new StorageError("there is no check called " + check.getCode() + " on a "
                        + lift.liftClass().value());
            }
            results.add(new CheckResult(item, check.getOutcome(), check.getBy(), check.getNote()));
        }
        return Optional.of(new PreOpSheet(
                lift,
                row.getDay(),
                results,
                row.getMechanic().get(),
                row.getOperator().get()));
    }

    /**
     * A stop as a row.
     *
     * @param stop the stop
     * @return a new row
     */
    public static StopRow stopToRow(StopEvent stop) {
        StopRow row = new StopRow(stop.lift(), stop.kind(), stop.stopped(), stop.stoppedBy());
        row.setNote(stop.note());
        row.setRestarted(stop.restarted().orElse(null));
        row.setRestartedBy(stop.restartedBy().orElse(null));
        row.setLineChecked(stop.lineChecked());
        return row;
    }

    /**
     * A stop back out of its row.
     *
     * @param row the row
     * @return the stop
     */
    public static StopEvent stopFromRow(StopRow row) {
        return new StopEvent(
                row.getLiftCode(),
                row.getKind(),
                row.getStopped(),
                row.getStoppedBy(),
                row.getNote(),
                row.getRestarted(),
                row.getRestartedBy(),
                row.isLineChecked());
    }

    /**
     * One machine's stops over whatever window the caller asked the database for.
     *
     * @param liftCode the machine
     * @param rows its stop rows
     * @return the stop log
     * @throws StorageError when a row is on another machine
     */
    public static StopLog stopLogFromRows(LiftCode liftCode, List<StopRow> rows) {
        for (StopRow row : rows) {
            if (!row.getLiftCode().equals(liftCode)) {
                throw new StorageError("that stop is on " + row.getLiftCode() + " and not " + liftCode);
            }
        }
        return StopLog.of(liftCode, rows.stream().map(OpsMapper::stopFromRow).toList());
    }

    /**
     * A weather hold as a row.
     *
     * @param hold the hold
     * @return a new row
     */
    public static HoldRow holdToRow(HoldRecord hold) {
        HoldRow row = new HoldRow(hold.lift(), hold.reason(), hold.started(), hold.startedBy());
        row.setDetail(hold.detail());
        row.setEnded(hold.ended().orElse(null));
        row.setEndedBy(hold.endedBy().orElse(null));
        return row;
    }

    /**
     * A weather hold back out of its row.
     *
     * @param row the row
     * @return the hold
     */
    public static HoldRecord holdFromRow(HoldRow row) {
        return new HoldRecord(
                row.getLiftCode(),
                row.getReason(),
                row.getStarted(),
                row.getStartedBy(),
                row.getDetail(),
                row.getEnded(),
                row.getEndedBy());
    }

    /**
     * A meter reading as a row.
     *
     * @param reading the reading
     * @return a new row
     */
    public static MeterReadingRow meterToRow(MeterReading reading) {
        MeterReadingRow row = new MeterReadingRow(
                reading.lift(), reading.day(), reading.hours(), Math.toIntExact(reading.cycles()), reading.readBy());
        row.setMeterReplaced(reading.meterReplaced());
        return row;
    }

    /**
     * A meter reading back out of its row.
     *
     * @param row the row
     * @return the reading
     */
    public static MeterReading meterFromRow(MeterReadingRow row) {
        return new MeterReading(
                row.getLiftCode(),
                row.getDay(),
                row.getHours(),
                row.getCycles(),
                row.getReadBy(),
                row.isMeterReplaced());
    }
}
