package io.github.rkaushikdev.sheaveline.upkeep;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * One thing wrong, where it is and whether it has been dealt with.
 *
 * <p>A defect has a different life from the record that found it. The record is closed the moment
 * it is written. The defect stays open until somebody fixes it, which may be three inspections
 * later, and the machine's service status depends on what is still open rather than on what was
 * found. Where a defect is matters as much as what it is, because a cracked sheave liner on tower
 * four and a cracked sheave liner on tower eleven are two defects and the crew is going to fix them
 * on the same walk.
 *
 * @param where where it is, such as {@code JP-T4}
 * @param level how much it costs while it is open
 * @param detail what is wrong
 * @param raised the day it was found
 * @param cleared the day it was fixed, once it has been
 * @param clearedBy who fixed it, once somebody has
 */
public record Defect(
        String where,
        DefectLevel level,
        String detail,
        LocalDate raised,
        Optional<LocalDate> cleared,
        Optional<String> clearedBy) {

    /**
     * A defect.
     *
     * @throws UpkeepError when it does not say where it is or what is wrong, is cleared before it was
     *     raised, or has only one of the day it was cleared and who cleared it
     */
    public Defect {
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(raised, "raised");
        Objects.requireNonNull(cleared, "cleared");
        Objects.requireNonNull(clearedBy, "clearedBy");
        if (where.strip().isEmpty()) {
            throw new UpkeepError("a defect says where it is");
        }
        if (detail.strip().isEmpty()) {
            throw new UpkeepError("a defect says what is wrong");
        }
        if (cleared.isPresent() && cleared.get().isBefore(raised)) {
            throw new UpkeepError("a defect cannot be cleared before it was raised");
        }
        if (cleared.isPresent() != clearedBy.isPresent()) {
            throw new UpkeepError("a defect is cleared by somebody on some day, or neither");
        }
    }

    /**
     * A defect still open.
     *
     * @param where where it is
     * @param level how much it costs while it is open
     * @param detail what is wrong
     * @param raised the day it was found
     */
    public Defect(String where, DefectLevel level, String detail, LocalDate raised) {
        this(where, level, detail, raised, Optional.empty(), Optional.empty());
    }

    /**
     * Whether nobody has cleared it yet.
     *
     * @return true until it is cleared
     */
    public boolean isOpen() {
        return cleared.isEmpty();
    }

    /**
     * How long the defect was open, or has been so far.
     *
     * @param on the day an open defect is measured to; a cleared one is measured to its clearing
     * @return the whole days
     * @throws UpkeepError when that day is before the defect was raised
     */
    public long ageInDays(LocalDate on) {
        LocalDate end = cleared.orElse(on);
        if (end.isBefore(raised)) {
            throw new UpkeepError("that day is before the defect was raised");
        }
        return ChronoUnit.DAYS.between(raised, end);
    }

    /**
     * Clear the defect.
     *
     * @param on the day it was fixed
     * @param by who fixed it
     * @return the cleared defect; this one is unchanged
     * @throws UpkeepError when it is already cleared, nobody is named, or the day is before it was
     *     raised
     */
    public Defect clear(LocalDate on, String by) {
        if (!isOpen()) {
            throw new UpkeepError("the defect at " + where + " is already cleared");
        }
        if (by.strip().isEmpty()) {
            throw new UpkeepError("a defect is cleared by somebody");
        }
        return new Defect(where, level, detail, raised, Optional.of(on), Optional.of(by));
    }

    /** Such as {@code JP-T4 defect: sheave liner cracked (open)}. */
    @Override
    public String toString() {
        String state = cleared.map(day -> "cleared " + day).orElse("open");
        return where + " " + level.value() + ": " + detail + " (" + state + ")";
    }
}
