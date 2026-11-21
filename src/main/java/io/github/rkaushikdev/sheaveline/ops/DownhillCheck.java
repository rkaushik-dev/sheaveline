package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Whether a machine may carry people down right now, and why not.
 *
 * <p>Carrying down is authorised rather than assumed. A machine may do it, at a stated speed, with
 * somebody at the top, and only for a reason somebody wrote down.
 *
 * @param lift the machine
 * @param on the day asked about
 * @param authorisations the authorisations on file, for any machine
 * @param postsManned the posts somebody is standing at
 * @param ropeSpeed the rope speed set for the downhill run, if one is
 */
public record DownhillCheck(
        Lift lift,
        LocalDate on,
        List<DownhillAuthorisation> authorisations,
        Set<Post> postsManned,
        Optional<Speed> ropeSpeed) {

    /** A check, with the manned posts held in declaration order. */
    public DownhillCheck {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(ropeSpeed, "ropeSpeed");
        authorisations = List.copyOf(authorisations);
        postsManned = postSet(postsManned);
    }

    /**
     * A check over the posts as the roster lists them.
     *
     * @param lift the machine
     * @param on the day asked about
     * @param authorisations the authorisations on file
     * @param postsManned the posts somebody is standing at, repeats allowed
     * @param ropeSpeed the rope speed set for the downhill run, if one is
     * @return the check
     */
    public static DownhillCheck of(
            Lift lift,
            LocalDate on,
            List<DownhillAuthorisation> authorisations,
            Collection<Post> postsManned,
            Optional<Speed> ropeSpeed) {
        return new DownhillCheck(lift, on, authorisations, postSet(postsManned), ropeSpeed);
    }

    /**
     * What this class of machine may do downhill.
     *
     * @return the policy for the machine's class
     */
    public DownhillPolicy policy() {
        return DownhillPolicy.forClass(lift.liftClass());
    }

    /**
     * The authorisations that hold for this machine today.
     *
     * @return those authorisations, in the order given
     */
    public List<DownhillAuthorisation> current() {
        return authorisations.stream()
                .filter(one -> one.lift().equals(lift.code()) && one.isCurrent(on))
                .toList();
    }

    /**
     * Everything that stops the machine carrying down.
     *
     * @return for a class that never carries down, that alone; otherwise each of: nothing authorised,
     *     nobody at the top, no rope speed set, or the rope above the downhill limit
     */
    public List<String> refusals() {
        DownhillPolicy policy = policy();
        if (!policy.allowed()) {
            return List.of("a " + lift.liftClass().value() + " does not carry downhill");
        }
        List<String> found = new ArrayList<>();
        if (current().isEmpty()) {
            found.add("nothing authorised for today");
        }
        if (policy.attendantAtTop() && !postsManned.contains(Post.TOP)) {
            found.add("nobody at the top terminal");
        }
        if (policy.speed().isPresent()) {
            Speed limit = policy.speed().get();
            if (ropeSpeed.isEmpty()) {
                found.add("no rope speed set");
            } else if (ropeSpeed.get().compareTo(limit) > 0) {
                found.add(ropeSpeed.get().formatFpm() + " is above the " + limit.formatFpm() + " downhill limit");
            }
        }
        return List.copyOf(found);
    }

    /**
     * Whether the machine may carry down.
     *
     * @return true when nothing refuses it
     */
    public boolean mayRun() {
        return refusals().isEmpty();
    }

    /**
     * How many carriers may be loaded downhill at once.
     *
     * @return the class's number when the machine may run, otherwise none
     */
    public int carriersAtOnce() {
        return mayRun() ? policy().carriersAtOnce() : 0;
    }

    /** Such as {@code JP may carry down, 4 at once} or {@code JP may not carry down: no rope speed set}. */
    @Override
    public String toString() {
        List<String> refusals = refusals();
        if (refusals.isEmpty()) {
            return lift.code() + " may carry down, " + carriersAtOnce() + " at once";
        }
        return lift.code() + " may not carry down: " + refusals.get(0);
    }

    private static Set<Post> postSet(Collection<Post> posts) {
        Set<Post> copy = EnumSet.noneOf(Post.class);
        copy.addAll(posts);
        return Collections.unmodifiableSet(copy);
    }
}
