package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.ServiceStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One machine's year, as the board is told about it.
 *
 * @param lift the machine
 * @param name what the machine is called
 * @param daysOpen the days it opened
 * @param scheduled the time it was meant to run
 * @param chargeable the stopped time charged to it
 * @param availability the share of its scheduled time it was not charged for
 * @param status what it may do at the end of the season
 * @param ladderOverdue how many inspections on its ladder are overdue
 * @param openDefects how many defects are still open against it
 * @param ropeSerial its haul rope
 * @param ropeVerdict what the rope's last walk found, in words
 * @param evacuationTime how long the last drill took to clear the line, when there was one
 */
public record MachineYear(
        LiftCode lift,
        String name,
        int daysOpen,
        Duration scheduled,
        Duration chargeable,
        BigDecimal availability,
        ServiceStatus status,
        int ladderOverdue,
        int openDefects,
        String ropeSerial,
        String ropeVerdict,
        Optional<Duration> evacuationTime) {

    private static final String SERVICEABLE = "serviceable";

    /** A machine's year. */
    public MachineYear {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scheduled, "scheduled");
        Objects.requireNonNull(chargeable, "chargeable");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(ropeSerial, "ropeSerial");
        Objects.requireNonNull(ropeVerdict, "ropeVerdict");
        Objects.requireNonNull(evacuationTime, "evacuationTime");
    }

    /**
     * A machine's year with no evacuation drill on it.
     *
     * @param lift the machine
     * @param name what the machine is called
     * @param daysOpen the days it opened
     * @param scheduled the time it was meant to run
     * @param chargeable the stopped time charged to it
     * @param availability the share of its scheduled time it was not charged for
     * @param status what it may do at the end of the season
     * @param ladderOverdue how many inspections are overdue
     * @param openDefects how many defects are open
     * @param ropeSerial its haul rope
     * @param ropeVerdict what the rope's last walk found
     */
    public MachineYear(
            LiftCode lift,
            String name,
            int daysOpen,
            Duration scheduled,
            Duration chargeable,
            BigDecimal availability,
            ServiceStatus status,
            int ladderOverdue,
            int openDefects,
            String ropeSerial,
            String ropeVerdict) {
        this(
                lift,
                name,
                daysOpen,
                scheduled,
                chargeable,
                availability,
                status,
                ladderOverdue,
                openDefects,
                ropeSerial,
                ropeVerdict,
                Optional.empty());
    }

    /**
     * Whether nothing is outstanding, which is the line the board is hoping to read.
     *
     * @return true when no inspection is overdue, no defect is open and it may carry the public
     */
    public boolean isClean() {
        return ladderOverdue == 0 && openDefects == 0 && status.mayCarryPublic();
    }

    /**
     * What the board should worry about on this machine, one sentence each.
     *
     * @return the concerns: its status, its overdue inspections, its open defects, then its rope
     */
    public List<String> concerns() {
        List<String> found = new ArrayList<>();
        if (!status.mayCarryPublic()) {
            found.add(lift + " is " + status.value());
        }
        if (ladderOverdue != 0) {
            found.add(lift + " has " + ladderOverdue + " inspections overdue");
        }
        if (openDefects != 0) {
            found.add(lift + " has " + openDefects + " defects open");
        }
        if (!ropeVerdict.equals(SERVICEABLE)) {
            found.add(lift + " rope " + ropeSerial + " is " + ropeVerdict);
        }
        return List.copyOf(found);
    }
}
