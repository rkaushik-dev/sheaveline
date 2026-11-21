package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * One card, for one person, for one endorsement: a permission with an expiry, not a qualification.
 *
 * <p>The person who ran the top of the quad for six seasons is not allowed to run it on the first of
 * December if their card ran out on the thirtieth of November, and the fact that they obviously still
 * know how is not the point: the board audits the cards.
 *
 * <p>Cards are also specific. An endorsement to run a fixed grip triple does not let somebody run a
 * detachable, because the machines fail differently and the training is different. So the question
 * is always three questions at once. Is the card current, does it carry the endorsement the post
 * needs, and does that endorsement cover this class of machine.
 *
 * @param person who holds it
 * @param endorsement what it lets them do
 * @param issued the first day it holds
 * @param expires the last day it holds
 * @param issuedBy who issued it
 * @param classes the classes of machine it covers
 */
public record Certificate(
        String person,
        Endorsement endorsement,
        LocalDate issued,
        LocalDate expires,
        String issuedBy,
        Set<LiftClass> classes) {

    /**
     * A card with a holder and an issuer that runs forwards and covers at least one class.
     *
     * @throws PeopleError when it names nobody, has no issuer, expires no later than it was issued,
     *     or covers no machines
     */
    public Certificate {
        Objects.requireNonNull(person, "person");
        Objects.requireNonNull(endorsement, "endorsement");
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(expires, "expires");
        Objects.requireNonNull(issuedBy, "issuedBy");
        Set<LiftClass> copy = EnumSet.noneOf(LiftClass.class);
        copy.addAll(classes);
        classes = Collections.unmodifiableSet(copy);
        if (person.strip().isEmpty()) {
            throw new PeopleError("a certificate names a person");
        }
        if (issuedBy.strip().isEmpty()) {
            throw new PeopleError(person + " has a card with nobody's name on it");
        }
        if (!expires.isAfter(issued)) {
            throw new PeopleError(person + " has a card that expires before it was issued");
        }
        if (classes.isEmpty()) {
            throw new PeopleError(person + " has a card that covers no machines");
        }
    }

    /**
     * A card that covers every class of machine.
     *
     * @param person who holds it
     * @param endorsement what it lets them do
     * @param issued the first day it holds
     * @param expires the last day it holds
     * @param issuedBy who issued it
     */
    public Certificate(String person, Endorsement endorsement, LocalDate issued, LocalDate expires, String issuedBy) {
        this(person, endorsement, issued, expires, issuedBy, EnumSet.allOf(LiftClass.class));
    }

    /**
     * Whether the card holds on a day.
     *
     * @param on the day
     * @return true from the day it was issued to the day it expires, both included
     */
    public boolean isCurrent(LocalDate on) {
        return !on.isBefore(issued) && !on.isAfter(expires);
    }

    /**
     * Whether the card covers a class of machine.
     *
     * @param liftClass the class
     * @return true when the class is one of the card's
     */
    public boolean coversClass(LiftClass liftClass) {
        return classes.contains(liftClass);
    }

    /**
     * Whether the card's endorsement is the one a post needs. A lead operator card also covers the
     * posts an ordinary operator holds.
     *
     * @param post the post
     * @return true when the card is enough for the post
     */
    public boolean coversPost(Post post) {
        Endorsement needed = Endorsement.neededFor(post);
        if (endorsement == needed) {
            return true;
        }
        return endorsement == Endorsement.LEAD_OPERATOR && needed == Endorsement.OPERATOR;
    }

    /**
     * Whether the card lets its holder stand at a post on a class of machine on a day.
     *
     * @param post the post
     * @param liftClass the class of machine
     * @param on the day
     * @return true when it is current, covers the post and covers the class
     */
    public boolean allows(Post post, LiftClass liftClass, LocalDate on) {
        return isCurrent(on) && coversPost(post) && coversClass(liftClass);
    }

    /**
     * Days from one day to the expiry.
     *
     * @param on the day
     * @return the days left, negative once the card has run out
     */
    public long daysLeft(LocalDate on) {
        return ChronoUnit.DAYS.between(on, expires);
    }

    /**
     * The card renewed, starting where this one ends.
     *
     * @param until the new expiry
     * @param by who issued the renewal
     * @return the new card, issued on this one's expiry
     * @throws PeopleError when the new expiry is no later than this one's
     */
    public Certificate renewedTo(LocalDate until, String by) {
        if (!until.isAfter(expires)) {
            throw new PeopleError(person + " would be renewed to no later than they already are");
        }
        return new Certificate(person, endorsement, expires, until, by, classes);
    }

    /** Such as {@code R Diaz, operator, to 2025-11-01}. */
    @Override
    public String toString() {
        return person + ", " + endorsement.value() + ", to " + expires;
    }
}
