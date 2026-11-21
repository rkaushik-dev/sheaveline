package io.github.rkaushikdev.sheaveline.persistence.mappers;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.PersonnelFile;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.AssignmentRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CertificateClassRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CertificateRow;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Cards and rosters, both ways.
 *
 * <p>The classes a card covers are their own table rather than a packed string, because the question
 * the office asks is which people can run a detachable, and a {@code LIKE} against a comma separated
 * column is how you end up with {@code SURFACE} matching {@code SURFACE_TOW}. Each class is written
 * in the words the card uses for it.
 *
 * <p>A roster is not stored as one row with people hanging off it. The assignments stand on their
 * own against the machine and the day, so a person's shift can be moved without rewriting everybody
 * else's, and the roster is assembled on the way out. Which means the loader has to be told the
 * machine's class, because a shift row does not know whether it belongs on a quad or a tow.
 */
public final class PeopleMapper {

    /** What a card with no classes written against it covers: every machine. */
    public static final Set<LiftClass> EVERY_CLASS = Collections.unmodifiableSet(EnumSet.allOf(LiftClass.class));

    private PeopleMapper() {}

    /**
     * A card as a row, with a row for each class it covers, in the order of their words.
     *
     * @param card the card
     * @return a new row
     */
    public static CertificateRow certificateToRow(Certificate card) {
        CertificateRow row =
                new CertificateRow(card.person(), card.endorsement(), card.issued(), card.expires(), card.issuedBy());
        card.classes().stream()
                .sorted(Comparator.comparing(LiftClass::value))
                .forEach(one -> row.addClass(new CertificateClassRow(one.value())));
        return row;
    }

    /**
     * A card back out of its row.
     *
     * @param row the row
     * @return the card, covering every class when the row lists none
     * @throws StorageError when the row lists a class there is no such thing as
     */
    public static Certificate certificateFromRow(CertificateRow row) {
        Set<LiftClass> covered = EnumSet.noneOf(LiftClass.class);
        for (CertificateClassRow one : row.getClasses()) {
            covered.add(Valued.lookup(LiftClass.class, one.getLiftClass())
                    .orElseThrow(() -> new StorageError("there is no lift class called " + one.getLiftClass())));
        }
        return new Certificate(
                row.getPerson(),
                row.getEndorsement(),
                row.getIssued(),
                row.getExpires(),
                row.getIssuedBy(),
                covered.isEmpty() ? EVERY_CLASS : covered);
    }

    /**
     * The whole personnel file out of its card rows.
     *
     * @param rows the rows
     * @return the file
     */
    public static PersonnelFile personnelFromRows(List<CertificateRow> rows) {
        return PersonnelFile.of(
                rows.stream().map(PeopleMapper::certificateFromRow).toList());
    }

    /**
     * A shift as a row against a machine and a day.
     *
     * @param lift the machine
     * @param day the day
     * @param shift the shift
     * @return a new row
     */
    public static AssignmentRow assignmentToRow(LiftCode lift, LocalDate day, Assignment shift) {
        return new AssignmentRow(lift, day, shift.person(), shift.post(), shift.starts(), shift.ends());
    }

    /**
     * A shift back out of its row.
     *
     * @param row the row
     * @return the shift
     */
    public static Assignment assignmentFromRow(AssignmentRow row) {
        return new Assignment(row.getPerson(), row.getPost(), row.getStarts(), row.getEnds());
    }

    /**
     * The shifts assembled into a roster, which needs to be told what it is on.
     *
     * @param lift the machine
     * @param liftClass what kind of machine it is
     * @param day the day
     * @param window when it runs that day
     * @param rows the shift rows
     * @return the roster
     * @throws StorageError when a shift is on another machine or another day
     */
    public static Roster rosterFromRows(
            LiftCode lift, LiftClass liftClass, LocalDate day, OperatingWindow window, List<AssignmentRow> rows) {
        for (AssignmentRow row : rows) {
            if (!row.getLiftCode().equals(lift)) {
                throw new StorageError("that shift is on " + row.getLiftCode() + " and not " + lift);
            }
            if (!row.getDay().equals(day)) {
                throw new StorageError("that shift is on " + row.getDay() + " and not " + day);
            }
        }
        return Roster.of(
                lift,
                liftClass,
                day,
                window,
                rows.stream().map(PeopleMapper::assignmentFromRow).toList());
    }
}
