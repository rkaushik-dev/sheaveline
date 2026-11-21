package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.line.GeometryVerdict;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.ropework.WearVerdict;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One machine on one day, read against everything that is known about it.
 *
 * <p>Four separate bodies of evidence decide whether a machine is fit to carry the public, and they
 * are held in four different places because they are gathered by four different people. The ladder
 * says what is overdue. The record book says what defects are still open. The rope file says what
 * the last survey made of the haul rope. The line walk says whether the rope clears the ground
 * today.
 *
 * <p>None of them outranks the others. Any one of them can take a machine out, and a machine is only
 * in service when all four agree it is. The answer comes with its reasons attached, because "out of
 * service" with no reason is an argument rather than a decision.
 *
 * @param lift the machine
 * @param on the day
 * @param schedule the machine's ladder, which holds its record book too
 * @param rope what the last survey made of the haul rope, when there has been one
 * @param geometry what the line walk said, when the line was walked
 */
public record ServiceAssessment(
        LiftCode lift,
        LocalDate on,
        UpkeepSchedule schedule,
        Optional<WearVerdict> rope,
        Optional<GeometryVerdict> geometry) {

    private static final String LADDER = "ladder";
    private static final String RECORD_BOOK = "record book";
    private static final String ROPE_FILE = "rope file";
    private static final String LINE_WALK = "line walk";

    /**
     * An assessment.
     *
     * @throws IllegalArgumentException when the ladder is another machine's
     */
    public ServiceAssessment {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(geometry, "geometry");
        if (!schedule.lift().equals(lift)) {
            throw new IllegalArgumentException("that ladder is for " + schedule.lift());
        }
    }

    /**
     * An assessment on the ladder and the record book alone.
     *
     * @param lift the machine
     * @param on the day
     * @param schedule the machine's ladder
     */
    public ServiceAssessment(LiftCode lift, LocalDate on, UpkeepSchedule schedule) {
        this(lift, on, schedule, Optional.empty(), Optional.empty());
    }

    /**
     * The machine's record book.
     *
     * @return the book the ladder reads
     */
    public RecordBook book() {
        return schedule.book();
    }

    /**
     * What the ladder says: an overdue critical job takes the machine out, any other restricts it.
     *
     * @return a reason per overdue job, in the manual's order
     */
    public List<StatusReason> ladderReasons() {
        return schedule.overdue(on).stream()
                .map(item -> new StatusReason(
                        LADDER,
                        item.job().critical() ? ServiceStatus.OUT_OF_SERVICE : ServiceStatus.RESTRICTED,
                        item.job().code() + " overdue, " + item.job().description()))
                .toList();
    }

    /**
     * What the record book says: an open serious defect takes the machine out.
     *
     * @return a reason per open serious defect
     */
    public List<StatusReason> defectReasons() {
        return book().openSerious().stream()
                .map(defect -> new StatusReason(
                        RECORD_BOOK, ServiceStatus.OUT_OF_SERVICE, defect.where() + " " + defect.detail()))
                .toList();
    }

    /**
     * What the rope file says: a condemned rope takes the machine out, one on watch restricts it.
     *
     * @return nothing for a serviceable rope or no survey, otherwise one reason
     */
    public List<StatusReason> ropeReasons() {
        return rope.map(verdict -> switch (verdict) {
                    case SERVICEABLE -> List.<StatusReason>of();
                    case CONDEMNED ->
                        List.of(new StatusReason(ROPE_FILE, ServiceStatus.OUT_OF_SERVICE, "rope condemned"));
                    case MONITOR -> List.of(new StatusReason(ROPE_FILE, ServiceStatus.RESTRICTED, "rope on watch"));
                })
                .orElse(List.of());
    }

    /**
     * What the line walk says: an unfit line takes the machine out, a restricted one restricts it.
     *
     * @return nothing for a fit line or no walk, otherwise one reason
     */
    public List<StatusReason> geometryReasons() {
        return geometry.map(verdict -> switch (verdict) {
                    case FIT -> List.<StatusReason>of();
                    case UNFIT -> List.of(new StatusReason(LINE_WALK, ServiceStatus.OUT_OF_SERVICE, "line unfit"));
                    case RESTRICTED ->
                        List.of(new StatusReason(LINE_WALK, ServiceStatus.RESTRICTED, "line restricted"));
                })
                .orElse(List.of());
    }

    /**
     * Every reason from the four sources.
     *
     * @return the ladder's, then the record book's, the rope file's and the line walk's
     */
    public List<StatusReason> reasons() {
        List<StatusReason> all = new ArrayList<>(ladderReasons());
        all.addAll(defectReasons());
        all.addAll(ropeReasons());
        all.addAll(geometryReasons());
        return List.copyOf(all);
    }

    /**
     * The worst thing any of the four sources says, and nothing else.
     *
     * @return out of service if any reason says so, else restricted if any says so, else in service
     */
    public ServiceStatus status() {
        List<ServiceStatus> found = reasons().stream().map(StatusReason::status).toList();
        if (found.contains(ServiceStatus.OUT_OF_SERVICE)) {
            return ServiceStatus.OUT_OF_SERVICE;
        }
        if (found.contains(ServiceStatus.RESTRICTED)) {
            return ServiceStatus.RESTRICTED;
        }
        return ServiceStatus.IN_SERVICE;
    }

    /**
     * The reasons that take the machine out.
     *
     * @return those reasons, in the order of {@link #reasons()}
     */
    public List<StatusReason> blocking() {
        return reasons().stream()
                .filter(reason -> reason.status() == ServiceStatus.OUT_OF_SERVICE)
                .toList();
    }

    /**
     * The sources that have something to say.
     *
     * @return an unmodifiable set, in the order of {@link #reasons()}
     */
    public Set<String> sourcesReporting() {
        Set<String> found = new LinkedHashSet<>();
        for (StatusReason reason : reasons()) {
            found.add(reason.source());
        }
        return Collections.unmodifiableSet(found);
    }

    /**
     * Whether the machine may open to the public today.
     *
     * @return true unless out of service
     */
    public boolean mayOpen() {
        return status().mayCarryPublic();
    }

    /** Such as {@code JP in service} or {@code JP restricted, 2 reasons}. */
    @Override
    public String toString() {
        int count = reasons().size();
        if (count == 0) {
            return lift + " in service";
        }
        return lift + " " + status().value() + ", " + count + " reasons";
    }
}
