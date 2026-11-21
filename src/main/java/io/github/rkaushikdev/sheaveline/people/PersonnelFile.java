package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every card the department holds.
 *
 * @param certificates the cards, by person, then endorsement, then expiry
 */
public record PersonnelFile(List<Certificate> certificates) {

    private static final Comparator<Certificate> ORDER = Comparator.comparing(Certificate::person)
            .thenComparing(card -> card.endorsement().value())
            .thenComparing(Certificate::expires);

    /** A file. */
    public PersonnelFile {
        certificates = List.copyOf(certificates);
    }

    /** A file with no cards in it. */
    public PersonnelFile() {
        this(List.of());
    }

    /**
     * A file with no cards in it.
     *
     * @return the empty file
     */
    public static PersonnelFile of() {
        return new PersonnelFile();
    }

    /**
     * A file with its cards put in order by person, then endorsement, then expiry.
     *
     * @param certificates the cards, in any order
     * @return the file
     */
    public static PersonnelFile of(List<Certificate> certificates) {
        return new PersonnelFile(certificates.stream().sorted(ORDER).toList());
    }

    /**
     * Everybody with a card.
     *
     * @return each name once, sorted
     */
    public List<String> people() {
        return certificates.stream()
                .map(Certificate::person)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * One person's cards.
     *
     * @param person the name
     * @return their cards, current or not
     */
    public List<Certificate> forPerson(String person) {
        return certificates.stream()
                .filter(card -> card.person().equals(person))
                .toList();
    }

    /**
     * One person's cards that hold on a day.
     *
     * @param person the name
     * @param on the day
     * @return their current cards
     */
    public List<Certificate> currentFor(String person, LocalDate on) {
        return forPerson(person).stream().filter(card -> card.isCurrent(on)).toList();
    }

    /**
     * Whether a person may stand at a post on a class of machine on a day.
     *
     * @param person the name
     * @param post the post
     * @param liftClass the class of machine
     * @param on the day
     * @return true when any of their cards allows it
     */
    public boolean mayHold(String person, Post post, LiftClass liftClass, LocalDate on) {
        return forPerson(person).stream().anyMatch(card -> card.allows(post, liftClass, on));
    }

    /**
     * Everybody who may stand at a post on a class of machine on a day.
     *
     * @param post the post
     * @param liftClass the class of machine
     * @param on the day
     * @return each name once, sorted
     */
    public List<String> qualifiedFor(Post post, LiftClass liftClass, LocalDate on) {
        return certificates.stream()
                .filter(card -> card.allows(post, liftClass, on))
                .map(Certificate::person)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The current cards that run out within a number of days.
     *
     * @param on the day looked from
     * @param days how far ahead to look
     * @return the cards that hold on the day and expire no later than the cutoff, in file order
     * @throws PeopleError when the look ahead is negative
     */
    public List<Certificate> expiringWithin(LocalDate on, int days) {
        if (days < 0) {
            throw new PeopleError("a look ahead is a positive number of days");
        }
        LocalDate cutoff = on.plusDays(days);
        return certificates.stream()
                .filter(card -> card.isCurrent(on) && !card.expires().isAfter(cutoff))
                .toList();
    }

    /**
     * The cards that have already run out.
     *
     * @param on the day
     * @return the cards that expired before it, in file order
     */
    public List<Certificate> lapsed(LocalDate on) {
        return certificates.stream().filter(card -> card.expires().isBefore(on)).toList();
    }

    /**
     * The same file with one more card in it.
     *
     * @param certificate the card
     * @return a new file in order; this one is unchanged
     */
    public PersonnelFile withCertificate(Certificate certificate) {
        List<Certificate> all = new ArrayList<>(certificates);
        all.add(certificate);
        return of(all);
    }

    /**
     * How many cards are in the file.
     *
     * @return the count
     */
    public int size() {
        return certificates.size();
    }
}
