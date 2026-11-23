package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.MeterHistory;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.OperationsError;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.persistence.mappers.OpsMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.DayLogRow;
import io.github.rkaushikdev.sheaveline.persistence.models.HoldRow;
import io.github.rkaushikdev.sheaveline.persistence.models.MeterReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.StopRow;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import io.github.rkaushikdev.sheaveline.weather.HoldRegister;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The day book, and the stops, holds and meters that do not fit inside a day.
 *
 * <p>A stop or a hold is written against the machine and the moment, not against a day's log, so a
 * stop that ran overnight belongs to both days it touched and a log is read back with whatever
 * touched it.
 */
public final class DayLogRepository extends Repository {

    /** The last moment of a day the clock columns can hold. */
    private static final LocalTime LAST_MOMENT = LocalTime.MAX.truncatedTo(ChronoUnit.MICROS);

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private final LiftRepository lifts;

    /**
     * The day book in a session.
     *
     * @param session the session
     */
    public DayLogRepository(Session session) {
        super(session);
        this.lifts = new LiftRepository(session);
    }

    /**
     * Write a day, with the meter reading and the stops it carries.
     *
     * @param log the day
     * @throws StorageError when the machine already has a log for that day, or the meter or a stop
     *     is already written
     */
    public void add(DayLog log) {
        if (findLog(log.lift(), log.day()).isPresent()) {
            throw new StorageError(log.lift() + " already has a log for " + log.day());
        }
        session().persist(OpsMapper.dayLogToRow(log));
        log.meter().ifPresent(this::readMeter);
        log.stops().ifPresent(stops -> stops.stops().forEach(this::stop));
        session().flush();
    }

    /**
     * The row of a machine's day, if it was written up.
     *
     * @param code the machine
     * @param day the day
     * @return the row, or empty
     */
    public Optional<DayLogRow> findLog(LiftCode code, LocalDate day) {
        return first(logQuery(code, day));
    }

    /**
     * A machine's day, with the stops that touched it and the meter read on it.
     *
     * @param code the machine
     * @param day the day
     * @return the day
     * @throws StorageError when nobody wrote the day up, or the machine is not on file
     */
    public DayLog get(LiftCode code, LocalDate day) {
        DayLogRow row = one(logQuery(code, day), "there is no log for " + code + " on " + day);
        return OpsMapper.dayLogFromRow(row, lifts.get(code), Optional.of(stopsOn(code, day)), meterOn(code, day));
    }

    /**
     * The days a machine was written up, oldest first.
     *
     * @param code the machine
     * @return the days
     */
    public List<LocalDate> daysLogged(LiftCode code) {
        return many(query("select d from DayLogRow d where d.liftCode = :code order by d.day", DayLogRow.class)
                        .setParameter("code", code))
                .stream()
                .map(DayLogRow::getDay)
                .toList();
    }

    /**
     * A machine's days from one to another, both included, oldest first.
     *
     * @param code the machine
     * @param start the first day
     * @param end the last day
     * @return the days, each with its stops and meter
     * @throws StorageError when the machine is not on file
     */
    public List<DayLog> between(LiftCode code, LocalDate start, LocalDate end) {
        Lift lift = lifts.get(code);
        List<DayLogRow> rows = many(query(
                        "select d from DayLogRow d where d.liftCode = :code and d.day >= :start and d.day <= :end"
                                + " order by d.day",
                        DayLogRow.class)
                .setParameter("code", code)
                .setParameter("start", start)
                .setParameter("end", end));
        return rows.stream()
                .map(one -> OpsMapper.dayLogFromRow(
                        one, lift, Optional.of(stopsOn(code, one.getDay())), meterOn(code, one.getDay())))
                .toList();
    }

    /**
     * Rewrite a day, which happens when the log is signed off next morning.
     *
     * @param log the day as it now stands
     * @throws StorageError when nobody wrote the day up
     */
    public void replace(DayLog log) {
        DayLogRow found = findLog(log.lift(), log.day())
                .orElseThrow(() -> new StorageError("there is no log for " + log.lift() + " on " + log.day()));
        session().remove(found);
        session().flush();
        session().persist(OpsMapper.dayLogToRow(log));
        session().flush();
    }

    /**
     * Write a stop.
     *
     * @param event the stop
     * @throws StorageError when the machine already has a stop at that moment
     */
    public void stop(StopEvent event) {
        if (first(stopQuery(event.lift(), event.stopped())).isPresent()) {
            throw new StorageError(event.lift() + " is already stopped at " + CLOCK.format(event.stopped()));
        }
        session().persist(OpsMapper.stopToRow(event));
        session().flush();
    }

    /**
     * Restart a stopped machine without a line check.
     *
     * @param code the machine
     * @param stopped when it stopped
     * @param when when it ran again
     * @param by who restarted it
     * @return the restarted stop
     * @throws StorageError when there is no such stop
     * @throws io.github.rkaushikdev.sheaveline.ops.OperationsError when the stop may not restart yet
     */
    public StopEvent restart(LiftCode code, LocalDateTime stopped, LocalDateTime when, String by) {
        return restart(code, stopped, when, by, false);
    }

    /**
     * Restart a stopped machine. The domain decides whether that is allowed, not the table.
     *
     * @param code the machine
     * @param stopped when it stopped
     * @param when when it ran again
     * @param by who restarted it
     * @param lineChecked whether the line was walked before it ran
     * @return the restarted stop
     * @throws StorageError when there is no such stop
     * @throws io.github.rkaushikdev.sheaveline.ops.OperationsError when the stop may not restart yet,
     *     which includes a stop that is one too many inside the hour and has not had the line walked
     */
    public StopEvent restart(LiftCode code, LocalDateTime stopped, LocalDateTime when, String by, boolean lineChecked) {
        StopRow row = one(stopQuery(code, stopped), "there is no stop on " + code + " at " + CLOCK.format(stopped));
        StopEvent stop = OpsMapper.stopFromRow(row);
        if (!lineChecked && stopsOn(code, stopped.toLocalDate()).callsTheMechanic(stop)) {
            throw new OperationsError(StopLog.REPEAT_LIMIT + " trips on " + code + " inside the hour: the mechanic"
                    + " walks the line before it runs again");
        }
        StopEvent restarted = stop.restart(when, by, lineChecked);
        row.setRestarted(restarted.restarted().orElse(null));
        row.setRestartedBy(restarted.restartedBy().orElse(null));
        row.setLineChecked(restarted.lineChecked());
        session().flush();
        return restarted;
    }

    /**
     * Every stop that touched a day, which is not every stop that began on it: one that ran
     * overnight touches both days, and one still open touches every day since it began.
     *
     * @param code the machine
     * @param day the day
     * @return the stops, in the order they began
     */
    public StopLog stopsOn(LiftCode code, LocalDate day) {
        List<StopRow> rows = many(query(
                        "select s from StopRow s where s.liftCode = :code and s.stopped <= :shut"
                                + " and (s.restarted is null or s.restarted >= :opened) order by s.stopped",
                        StopRow.class)
                .setParameter("code", code)
                .setParameter("shut", day.atTime(LAST_MOMENT))
                .setParameter("opened", day.atStartOfDay()));
        return OpsMapper.stopLogFromRows(code, rows);
    }

    /**
     * Every machine still stopped, in the order they stopped.
     *
     * @return the stops
     */
    public List<StopEvent> openStops() {
        return openStops(Optional.empty());
    }

    /**
     * One machine's stops that have not restarted, in the order they stopped.
     *
     * @param code the machine
     * @return the stops
     */
    public List<StopEvent> openStops(LiftCode code) {
        return openStops(Optional.of(code));
    }

    private List<StopEvent> openStops(Optional<LiftCode> code) {
        String statement = "select s from StopRow s where s.restarted is null"
                + code.map(any -> " and s.liftCode = :code").orElse("")
                + " order by s.stopped";
        TypedQuery<StopRow> query = query(statement, StopRow.class);
        code.ifPresent(wanted -> query.setParameter("code", wanted));
        return many(query).stream().map(OpsMapper::stopFromRow).toList();
    }

    /**
     * Write a weather hold.
     *
     * @param record the hold
     */
    public void callHold(HoldRecord record) {
        session().persist(OpsMapper.holdToRow(record));
        session().flush();
    }

    /**
     * Clear a hold and write the clearance back.
     *
     * @param code the machine
     * @param started when the hold was called
     * @param when when it cleared
     * @param by who cleared it
     * @return the cleared hold
     * @throws StorageError when there is no such hold
     * @throws io.github.rkaushikdev.sheaveline.weather.WeatherError when it cannot clear then
     */
    public HoldRecord clearHold(LiftCode code, LocalDateTime started, LocalDateTime when, String by) {
        HoldRow row = one(
                query("select h from HoldRow h where h.liftCode = :code and h.started = :started", HoldRow.class)
                        .setParameter("code", code)
                        .setParameter("started", started),
                "there is no hold on " + code + " from " + CLOCK.format(started));
        HoldRecord cleared = OpsMapper.holdFromRow(row).closed(when, by);
        row.setEnded(cleared.ended().orElse(null));
        row.setEndedBy(cleared.endedBy().orElse(null));
        session().flush();
        return cleared;
    }

    /**
     * Every hold across the hill, in the order they were called.
     *
     * @return the register
     */
    public HoldRegister holds() {
        return holds(Optional.empty());
    }

    /**
     * One machine's holds, in the order they were called.
     *
     * @param code the machine
     * @return the register
     */
    public HoldRegister holds(LiftCode code) {
        return holds(Optional.of(code));
    }

    private HoldRegister holds(Optional<LiftCode> code) {
        String statement = "select h from HoldRow h"
                + code.map(any -> " where h.liftCode = :code").orElse("")
                + " order by h.started";
        TypedQuery<HoldRow> query = query(statement, HoldRow.class);
        code.ifPresent(wanted -> query.setParameter("code", wanted));
        return HoldRegister.of(many(query).stream().map(OpsMapper::holdFromRow).toList());
    }

    /**
     * Write the day's meter reading, checked against the reading before it.
     *
     * @param reading the reading
     * @return the hours it adds to the machine, zero for the first reading on file
     * @throws StorageError when the machine was already read that day
     * @throws io.github.rkaushikdev.sheaveline.ops.OperationsError when the reading is more hours on
     *     from the one before it than the days between them hold
     */
    public BigDecimal readMeter(MeterReading reading) {
        if (first(meterQuery(reading.lift(), reading.day())).isPresent()) {
            throw new StorageError(reading.lift() + " was already read on " + reading.day());
        }
        BigDecimal added =
                meters(reading.lift()).map(history -> history.check(reading)).orElse(BigDecimal.ZERO);
        session().persist(OpsMapper.meterToRow(reading));
        session().flush();
        return added;
    }

    /**
     * The meter reading taken on a day.
     *
     * @param code the machine
     * @param day the day
     * @return the reading, or empty when nobody read the meter
     */
    public Optional<MeterReading> meterOn(LiftCode code, LocalDate day) {
        return first(meterQuery(code, day)).map(OpsMapper::meterFromRow);
    }

    /**
     * Every reading of a machine's meters, as a history that can be stepped.
     *
     * @param code the machine
     * @return the history, oldest first, or empty rather than an empty history, which the domain
     *     refuses to make
     */
    public Optional<MeterHistory> meters(LiftCode code) {
        List<MeterReadingRow> rows = many(
                query("select m from MeterReadingRow m where m.liftCode = :code order by m.day", MeterReadingRow.class)
                        .setParameter("code", code));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                MeterHistory.of(code, rows.stream().map(OpsMapper::meterFromRow).toList()));
    }

    private TypedQuery<DayLogRow> logQuery(LiftCode code, LocalDate day) {
        return query("select d from DayLogRow d where d.liftCode = :code and d.day = :day", DayLogRow.class)
                .setParameter("code", code)
                .setParameter("day", day);
    }

    private TypedQuery<StopRow> stopQuery(LiftCode code, LocalDateTime stopped) {
        return query("select s from StopRow s where s.liftCode = :code and s.stopped = :stopped", StopRow.class)
                .setParameter("code", code)
                .setParameter("stopped", stopped);
    }

    private TypedQuery<MeterReadingRow> meterQuery(LiftCode code, LocalDate day) {
        return query("select m from MeterReadingRow m where m.liftCode = :code and m.day = :day", MeterReadingRow.class)
                .setParameter("code", code)
                .setParameter("day", day);
    }
}
