package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.people.PersonnelFile;
import io.github.rkaushikdev.sheaveline.persistence.mappers.PeopleMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.AssignmentRow;
import io.github.rkaushikdev.sheaveline.persistence.models.CertificateRow;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** The cards and the shifts, which are the same question asked two ways. */
public final class PersonnelRepository extends Repository {

    /**
     * Where one person is down to be: a machine and the shift on it.
     *
     * @param lift the machine
     * @param shift the shift
     */
    public record Placement(LiftCode lift, Assignment shift) {

        /** A placement. */
        public Placement {
            Objects.requireNonNull(lift, "lift");
            Objects.requireNonNull(shift, "shift");
        }
    }

    private final LiftRepository lifts;

    /**
     * The personnel file and the rosters in a session.
     *
     * @param session the session
     */
    public PersonnelRepository(Session session) {
        super(session);
        this.lifts = new LiftRepository(session);
    }

    /**
     * Put a card on file.
     *
     * @param card the card
     * @throws StorageError when the person already has a card of that endorsement issued that day
     */
    public void issue(Certificate card) {
        boolean clash = first(query(
                                "select c from CertificateRow c where c.person = :person"
                                        + " and c.endorsement = :endorsement and c.issued = :issued",
                                CertificateRow.class)
                        .setParameter("person", card.person())
                        .setParameter("endorsement", card.endorsement())
                        .setParameter("issued", card.issued()))
                .isPresent();
        if (clash) {
            throw new StorageError(
                    card.person() + " already has a " + card.endorsement().value() + " card issued " + card.issued());
        }
        session().persist(PeopleMapper.certificateToRow(card));
        session().flush();
    }

    /**
     * Every card on file, as the personnel file.
     *
     * @return the file, by person
     */
    public PersonnelFile file() {
        return PeopleMapper.personnelFromRows(
                many(query("select c from CertificateRow c order by c.person", CertificateRow.class)));
    }

    /**
     * One person's cards, oldest first.
     *
     * @param person the person
     * @return the cards
     */
    public List<Certificate> forPerson(String person) {
        return many(query(
                                "select c from CertificateRow c where c.person = :person order by c.issued",
                                CertificateRow.class)
                        .setParameter("person", person))
                .stream()
                .map(PeopleMapper::certificateFromRow)
                .toList();
    }

    /**
     * Everybody who holds a card, in name order.
     *
     * @return the names
     */
    public List<String> people() {
        return List.copyOf(new TreeSet<>(many(query("select c.person from CertificateRow c", String.class))));
    }

    /**
     * Renew a person's latest card of an endorsement, writing a new card and keeping the old.
     *
     * @param person the person
     * @param endorsement the endorsement
     * @param expires when the new card runs out
     * @param by who issued it
     * @return the new card
     * @throws StorageError when the person has no such card to renew
     */
    public Certificate renew(String person, Endorsement endorsement, LocalDate expires, String by) {
        CertificateRow row = one(
                query(
                                "select c from CertificateRow c where c.person = :person"
                                        + " and c.endorsement = :endorsement order by c.issued desc",
                                CertificateRow.class)
                        .setParameter("person", person)
                        .setParameter("endorsement", endorsement),
                person + " has no " + endorsement.value() + " card to renew");
        Certificate renewed = PeopleMapper.certificateFromRow(row).renewedTo(expires, by);
        issue(renewed);
        return renewed;
    }

    /**
     * Put a person on a machine for a shift.
     *
     * @param lift the machine
     * @param day the day
     * @param shift the shift
     * @throws StorageError when the person already starts a shift on that machine at that time
     */
    public void assign(LiftCode lift, LocalDate day, Assignment shift) {
        boolean clash = first(query(
                                "select a from AssignmentRow a where a.liftCode = :code and a.day = :day"
                                        + " and a.person = :person and a.starts = :starts",
                                AssignmentRow.class)
                        .setParameter("code", lift)
                        .setParameter("day", day)
                        .setParameter("person", shift.person())
                        .setParameter("starts", shift.starts()))
                .isPresent();
        if (clash) {
            throw new StorageError(shift.person() + " is already on " + lift + " at " + shift.starts());
        }
        session().persist(PeopleMapper.assignmentToRow(lift, day, shift));
        session().flush();
    }

    /**
     * The shift rows on a machine for a day, by start.
     *
     * @param lift the machine
     * @param day the day
     * @return the rows
     */
    public List<AssignmentRow> shifts(LiftCode lift, LocalDate day) {
        return many(query(
                        "select a from AssignmentRow a where a.liftCode = :code and a.day = :day order by a.starts",
                        AssignmentRow.class)
                .setParameter("code", lift)
                .setParameter("day", day));
    }

    /**
     * The roster for a machine and a day.
     *
     * @param lift the machine
     * @param day the day
     * @param window when it runs that day
     * @return the roster
     * @throws StorageError when the machine is not on file
     */
    public Roster roster(LiftCode lift, LocalDate day, OperatingWindow window) {
        Lift machine = lifts.get(lift);
        return PeopleMapper.rosterFromRows(lift, machine.liftClass(), day, window, shifts(lift, day));
    }

    /**
     * Everywhere one person is down to be on a day, which is how a clash is found.
     *
     * @param person the person
     * @param day the day
     * @return the machines and shifts, by start
     */
    public List<Placement> working(String person, LocalDate day) {
        return many(query(
                                "select a from AssignmentRow a where a.person = :person and a.day = :day"
                                        + " order by a.starts",
                                AssignmentRow.class)
                        .setParameter("person", person)
                        .setParameter("day", day))
                .stream()
                .map(one -> new Placement(one.getLiftCode(), PeopleMapper.assignmentFromRow(one)))
                .toList();
    }
}
