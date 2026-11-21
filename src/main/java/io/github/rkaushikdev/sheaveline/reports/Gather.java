package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.capacity.LineSpacing;
import io.github.rkaushikdev.sheaveline.line.Catenary;
import io.github.rkaushikdev.sheaveline.line.GeometryVerdict;
import io.github.rkaushikdev.sheaveline.line.LineGeometry;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.ops.StopLog;
import io.github.rkaushikdev.sheaveline.persistence.IncidentRepository;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.ropework.Verdict;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.ropework.WearVerdict;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import io.github.rkaushikdev.sheaveline.upkeep.RecordBook;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceAssessment;
import io.github.rkaushikdev.sheaveline.upkeep.UpkeepSchedule;
import io.github.rkaushikdev.sheaveline.weather.Hold;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import io.github.rkaushikdev.sheaveline.weather.WeatherHold;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reading the database into the shapes the reports want.
 *
 * <p>The reports deliberately refuse to look anything up, because a renderer that goes and asks the
 * database can only ever print today, and the manager who wants to know why the summit quad was down
 * on the eighth of February is asking about a Tuesday two months gone.
 *
 * <p>So the fetching lives here instead, in one place, where it can be read against the questions it
 * is answering. Every method takes a unit of work and a day and hands back something the report
 * already understands.
 *
 * <p>The one judgement in the class is what counts as the machine's status. It is not a column
 * anywhere. It falls out of four independent sources, and the assessment is the thing that knows
 * how to weigh them, so this class's job is to put the four in front of it and get out of the way.
 */
public final class Gather {

    /** How far ahead the morning page looks for upkeep coming due. */
    public static final int WARN_WITHIN_DAYS = 14;

    /** The line walked with no snow under it. */
    public static final Length BARE_GROUND = new Length(0);

    private static final String NEVER_WALKED = "never walked";

    private Gather() {}

    /**
     * The sources a machine's fitness is read from, put in one place, without walking the line.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @return the assessment, with no line verdict
     */
    public static ServiceAssessment assess(UnitOfWork work, Lift lift, LocalDate on) {
        return assess(work, lift, on, Optional.empty());
    }

    /**
     * The sources a machine's fitness is read from, put in one place.
     *
     * <p>The line walk is the one that has to be asked for. Clearance depends on how hard the rope
     * is pulled and how much snow is under it, and neither of those is a column anywhere, so a
     * caller that has not measured them gets a reading without a line verdict rather than a line
     * verdict off a made up tension.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @param tension what the rope is carrying, when the line is to be walked as well
     * @return the assessment
     */
    public static ServiceAssessment assess(UnitOfWork work, Lift lift, LocalDate on, Optional<Load> tension) {
        RecordBook book = RecordBook.of(lift.code(), work.inspections().forLift(lift.code()));
        UpkeepSchedule schedule = new UpkeepSchedule(
                lift.code(),
                lift.liftClass(),
                lift.commissioned(),
                book,
                work.dayLogs().meters(lift.code()));
        List<WearSurvey> surveys = work.wearSurveys().forRope(lift.rope());
        Optional<WearVerdict> rope =
                surveys.isEmpty() ? Optional.empty() : Optional.of(Verdict.assessWear(surveys.getLast()));
        return new ServiceAssessment(lift.code(), on, schedule, rope, tension.map(pulled -> lineVerdict(lift, pulled)));
    }

    /** The line walked on bare ground at the tension the caller says the rope is carrying. */
    private static GeometryVerdict lineVerdict(Lift lift, Load tension) {
        LineSpacing spacing = LineSpacing.of(lift);
        LoadPerFoot weight = Catenary.lineWeight(
                lift.rope().spec().weight(), lift.carriers().onLine().getFirst().gross(), spacing.spacing());
        return new LineGeometry(lift, new SagModel(weight, tension), BARE_GROUND).verdict();
    }

    /**
     * What the nearest station says about this machine right now.
     *
     * <p>A hill with no anemometer, or one that has read nothing, is a normal hill and holds
     * nothing. Neither does a reading from before yesterday.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @return the holds, in the order the policy reads them
     */
    public static List<Hold> weatherHolds(UnitOfWork work, Lift lift, LocalDate on) {
        Observation seen;
        try {
            Station station = work.weather().nearestTo(lift.top().ropeElevation());
            seen = work.weather().latest(station);
        } catch (StorageError noReading) {
            return List.of();
        }
        if (seen.taken().toLocalDate().isBefore(on.minusDays(1))) {
            return List.of();
        }
        return new WeatherHold(lift, seen).holds();
    }

    /**
     * One machine's line on the morning page, without walking the line.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @return the line
     */
    public static MachineLine machineLine(UnitOfWork work, Lift lift, LocalDate on) {
        return machineLine(work, lift, on, Optional.empty());
    }

    /**
     * One machine's line on the morning page: the assessment, the day book, the weather and the
     * open work orders.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @param tension what the rope is carrying, when the line is to be walked as well
     * @return the line
     */
    public static MachineLine machineLine(UnitOfWork work, Lift lift, LocalDate on, Optional<Load> tension) {
        ServiceAssessment assessment = assess(work, lift, on, tension);
        boolean walked = false;
        List<String> blockers = new ArrayList<>();
        if (work.dayLogs().findLog(lift.code(), on).isPresent()) {
            DayLog book = work.dayLogs().get(lift.code(), on);
            walked = book.sheet().map(PreOpSheet::isComplete).orElse(false);
            blockers.addAll(book.blockers());
        }
        assessment.blocking().forEach(reason -> blockers.add(reason.toString()));
        return new MachineLine(
                lift.code(),
                lift.name(),
                assessment.status(),
                walked,
                blockers,
                weatherHolds(work, lift, on),
                work.workOrders().openOrders(lift.code()),
                assessment.schedule().dueWithin(on, WARN_WITHIN_DAYS).stream()
                        .map(Object::toString)
                        .toList());
    }

    /**
     * The seven o'clock page, gathered off the register, without walking the line.
     *
     * @param work the unit of work
     * @param on the day
     * @param resort the name on the page
     * @param snow what the patrol reported overnight, or empty
     * @return the page
     */
    public static Morning morning(UnitOfWork work, LocalDate on, String resort, String snow) {
        return morning(work, on, resort, snow, Optional.empty());
    }

    /**
     * The seven o'clock page, gathered off the register: one line for every machine on it.
     *
     * @param work the unit of work
     * @param on the day
     * @param resort the name on the page
     * @param snow what the patrol reported overnight, or empty
     * @param tension what the rope is carrying, when the line is to be walked as well
     * @return the page
     */
    public static Morning morning(UnitOfWork work, LocalDate on, String resort, String snow, Optional<Load> tension) {
        List<MachineLine> machines = work.lifts().all().stream()
                .map(lift -> machineLine(work, lift, on, tension))
                .toList();
        return Morning.of(on, resort, snow, machines);
    }

    /**
     * One machine on one day, as the season summary counts it.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param on the day
     * @return the record, or empty when nobody wrote the day up
     */
    public static Optional<DayRecord> dayRecord(UnitOfWork work, LiftCode lift, LocalDate on) {
        if (work.dayLogs().findLog(lift, on).isEmpty()) {
            return Optional.empty();
        }
        DayLog book = work.dayLogs().get(lift, on);
        StopLog stops = work.dayLogs().stopsOn(lift, on);
        Duration scheduled = book.scheduledTime();
        return Optional.of(new DayRecord(
                lift,
                on,
                book.isOpen() || book.opened().isPresent(),
                scheduled,
                scheduled.minus(stops.downtime()),
                stops.downtime(),
                stops.chargeableDowntime(),
                stops.stops().stream().map(StopEvent::kind).toList()));
    }

    /**
     * Every machine, every day it was written up inside the season, added up once.
     *
     * @param work the unit of work
     * @param season the season
     * @return the summary
     */
    public static SeasonSummary seasonSummary(UnitOfWork work, Season season) {
        List<DayRecord> records = new ArrayList<>();
        for (LiftCode code : work.lifts().codes()) {
            for (LocalDate day : work.dayLogs().daysLogged(code)) {
                if (season.covers(day)) {
                    dayRecord(work, code, day).ifPresent(records::add);
                }
            }
        }
        return SeasonSummary.of(season, records);
    }

    /**
     * One machine's season, read the way the board asks about it: on the day the season closed.
     *
     * @param work the unit of work
     * @param lift the machine
     * @param season the season
     * @param summary the season already added up
     * @return the machine's year
     */
    public static MachineYear machineYear(UnitOfWork work, Lift lift, Season season, SeasonSummary summary) {
        ServiceAssessment reading = assess(work, lift, season.closes());
        List<WearSurvey> surveys = work.wearSurveys().forRope(lift.rope());
        return new MachineYear(
                lift.code(),
                lift.name(),
                summary.daysOpen(lift.code()),
                summary.scheduled(lift.code()),
                summary.chargeable(lift.code()),
                summary.availability(lift.code()),
                reading.status(),
                reading.schedule().overdue(season.closes()).size(),
                work.inspections().openDefects(lift.code()).size(),
                lift.rope().serial().toString(),
                surveys.isEmpty()
                        ? NEVER_WALKED
                        : Verdict.assessWear(surveys.getLast()).value());
    }

    /**
     * The annual page, gathered off a season that is already over.
     *
     * @param work the unit of work
     * @param season the season
     * @param resort the operator
     * @param prepared the day it was prepared
     * @param preparedBy who is signing it
     * @return the packet
     */
    public static BoardPacket boardPacket(
            UnitOfWork work, Season season, String resort, LocalDate prepared, String preparedBy) {
        SeasonSummary summary = seasonSummary(work, season);
        List<MachineYear> machines = work.lifts().all().stream()
                .map(one -> machineYear(work, one, season, summary))
                .toList();
        List<FiledIncident> incidents = new ArrayList<>();
        for (LiftCode lift : work.lifts().codes()) {
            for (IncidentRepository.Numbered numbered : work.incidents().numberedForLift(lift)) {
                if (season.covers(numbered.incident().at().toLocalDate())) {
                    incidents.add(new FiledIncident(numbered.incident(), numbered.filingNumber()));
                }
            }
        }
        return BoardPacket.of(season.label(), resort, prepared, preparedBy, machines, incidents);
    }
}
