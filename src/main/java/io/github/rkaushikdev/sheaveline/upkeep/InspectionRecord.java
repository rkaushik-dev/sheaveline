package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One job done to one machine on one day, and the defects it turned up.
 *
 * @param lift the machine
 * @param code the job, as the manual codes it
 * @param done the day it was done
 * @param by who did it
 * @param hours the hour meter at the time, which a job on the hour meter must record
 * @param cycles the cycle counter at the time, which a job on the cycle counter must record
 * @param defects what it found, in the order written up
 */
public record InspectionRecord(
        LiftCode lift,
        String code,
        LocalDate done,
        String by,
        Optional<BigDecimal> hours,
        Optional<Long> cycles,
        List<Defect> defects) {

    /**
     * A record of a job in the manual.
     *
     * @throws UpkeepError when the manual has no such job, nobody signed it, the meter the job runs
     *     on was not recorded, or a defect was raised before the inspection that found it
     */
    public InspectionRecord {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(done, "done");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(cycles, "cycles");
        defects = List.copyOf(defects);
        Requirement job = Library.requirement(code);
        if (by.strip().isEmpty()) {
            throw new UpkeepError(code + " is signed by whoever did it");
        }
        if (job.interval().runsOn(Basis.HOURS) && hours.isEmpty()) {
            throw new UpkeepError(code + " runs on the hour meter and none was recorded");
        }
        if (job.interval().runsOn(Basis.CYCLES) && cycles.isEmpty()) {
            throw new UpkeepError(code + " runs on the cycle counter and none was recorded");
        }
        for (Defect defect : defects) {
            if (defect.raised().isBefore(done)) {
                throw new UpkeepError("a defect cannot be raised before the inspection that found it");
            }
        }
    }

    /**
     * A clean record of a job, with no meter readings, its code upper cased.
     *
     * @param lift the machine
     * @param code the job, in either case
     * @param done the day it was done
     * @param by who did it
     * @return the record
     */
    public static InspectionRecord of(LiftCode lift, String code, LocalDate done, String by) {
        return of(lift, code, done, by, Optional.empty(), Optional.empty(), List.of());
    }

    /**
     * A record of a job, its code upper cased.
     *
     * @param lift the machine
     * @param code the job, in either case
     * @param done the day it was done
     * @param by who did it
     * @param hours the hour meter at the time, if read
     * @param cycles the cycle counter at the time, if read
     * @param defects what it found
     * @return the record
     */
    public static InspectionRecord of(
            LiftCode lift,
            String code,
            LocalDate done,
            String by,
            Optional<BigDecimal> hours,
            Optional<Long> cycles,
            List<Defect> defects) {
        return new InspectionRecord(lift, code.toUpperCase(Locale.ROOT), done, by, hours, cycles, defects);
    }

    /**
     * The job this record is of.
     *
     * @return the job in the manual
     */
    public Requirement job() {
        return Library.requirement(code);
    }

    /**
     * The defects not yet cleared.
     *
     * @return those defects, in the order written up
     */
    public List<Defect> openDefects() {
        return defects.stream().filter(Defect::isOpen).toList();
    }

    /**
     * The serious defects, cleared or not.
     *
     * @return those defects, in the order written up
     */
    public List<Defect> serious() {
        return defects.stream()
                .filter(defect -> defect.level() == DefectLevel.SERIOUS)
                .toList();
    }

    /**
     * The open defects that stop the machine.
     *
     * @return those defects, in the order written up
     */
    public List<Defect> openSerious() {
        return openDefects().stream()
                .filter(defect -> defect.level().stopsTheMachine())
                .toList();
    }

    /**
     * Whether the job turned up nothing that needs a work order.
     *
     * @return true when every defect, if any, is an observation
     */
    public boolean wasClean() {
        return defects.stream().noneMatch(defect -> defect.level().needsAWorkOrder());
    }

    /** Such as {@code JP W1 on 2025-01-15, clean} or {@code ..., 1 written up}. */
    @Override
    public String toString() {
        String tail = wasClean() ? "clean" : defects.size() + " written up";
        return lift + " " + code + " on " + done + ", " + tail;
    }
}
