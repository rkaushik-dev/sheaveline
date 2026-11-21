package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One autumn's total station shots on one machine.
 *
 * <p>Towers move. Frost gets under a footing and lifts it an inch a winter, a fill settles for twenty
 * years after it is placed, and a tower on a sidehill leans downslope. None of that is a defect on its
 * own. It becomes one when the crossarm is far enough out of plumb that the sheave train does not sit
 * under the rope any more, and the point of shooting the towers every autumn is to see it coming.
 *
 * <p>Settlement is the footing going down, an elevation change against the as built drawing. Lean is
 * the top going sideways. They are not the same question and are reported apart.
 *
 * @param line the towers as built
 * @param taken the day of the survey
 * @param surveyor who shot it
 * @param shots the shots taken
 */
public record AlignmentSurvey(TowerLine line, LocalDate taken, String surveyor, List<TowerShot> shots) {

    /** How far a crossarm may settle below the drawing before it is a finding. */
    public static final Length SETTLEMENT_TOLERANCE = Length.ofInches("2.000");

    /**
     * A survey of one machine.
     *
     * @throws PlantError when nobody is named, a tower is shot twice, or a shot is off another lift
     */
    public AlignmentSurvey {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(surveyor, "surveyor");
        shots = List.copyOf(shots);
        if (surveyor.strip().isEmpty()) {
            throw new PlantError("a survey names who shot it");
        }
        if (shots.stream().map(TowerShot::ref).distinct().count() != shots.size()) {
            throw new PlantError("a tower is shot once in a survey");
        }
        for (TowerShot shot : shots) {
            if (!shot.ref().lift().equals(line.lift())) {
                throw new PlantError(shot.ref() + " is not on " + line.lift());
            }
        }
    }

    /**
     * A survey with its shots put in tower order.
     *
     * @param line the towers as built
     * @param taken the day
     * @param surveyor who shot it
     * @param shots the shots, in any order
     * @return the survey
     */
    public static AlignmentSurvey of(TowerLine line, LocalDate taken, String surveyor, List<TowerShot> shots) {
        List<TowerShot> ordered = shots.stream()
                .sorted(Comparator.comparingInt(one -> one.ref().number()))
                .toList();
        return new AlignmentSurvey(line, taken, surveyor, ordered);
    }

    /**
     * A tower as built.
     *
     * @param ref the tower
     * @return its drawing
     * @throws PlantError when the line has no such tower
     */
    public Tower tower(TowerRef ref) {
        for (Tower one : line) {
            if (one.ref().equals(ref)) {
                return one;
            }
        }
        throw new PlantError("there is no " + ref + " on this line");
    }

    /**
     * The shot of a tower.
     *
     * @param ref the tower
     * @return its shot, or empty when nobody shot it
     */
    public Optional<TowerShot> shot(TowerRef ref) {
        return shots.stream().filter(one -> one.ref().equals(ref)).findFirst();
    }

    /**
     * Towers on the line that nobody shot, which is what the file is for.
     *
     * @return those towers in order
     */
    public List<TowerRef> missed() {
        Set<TowerRef> seen = shots.stream().map(TowerShot::ref).collect(Collectors.toSet());
        return line.towers().stream()
                .map(Tower::ref)
                .filter(ref -> !seen.contains(ref))
                .toList();
    }

    /**
     * How far the crossarm has come down since the drawing.
     *
     * @param ref the tower
     * @return the settlement, positive downward
     * @throws PlantError when the tower was not shot
     */
    public Length settlement(TowerRef ref) {
        return tower(ref).crossarmElevation().minus(shotOf(ref).crossarmElevation());
    }

    /**
     * How far the tower leans.
     *
     * @param ref the tower
     * @return the lean over its own height
     * @throws PlantError when the tower was not shot
     */
    public Angle lean(TowerRef ref) {
        return shotOf(ref).lean(tower(ref).height());
    }

    /**
     * The towers leaning past the plumb tolerance.
     *
     * @return those towers in shot order
     */
    public List<TowerRef> outOfPlumb() {
        return outOfPlumb(TowerShot.PLUMB_TOLERANCE);
    }

    /**
     * The towers leaning past a tolerance.
     *
     * @param tolerance the largest lean allowed
     * @return those towers in shot order
     */
    public List<TowerRef> outOfPlumb(Angle tolerance) {
        return shots.stream()
                .filter(one -> !one.isPlumb(tower(one.ref()).height(), tolerance))
                .map(TowerShot::ref)
                .toList();
    }

    /**
     * The towers settled past the settlement tolerance.
     *
     * @return those towers in shot order
     */
    public List<TowerRef> settledTooFar() {
        return settledTooFar(SETTLEMENT_TOLERANCE);
    }

    /**
     * The towers settled past a limit.
     *
     * @param limit the most a crossarm may settle
     * @return those towers in shot order
     */
    public List<TowerRef> settledTooFar(Length limit) {
        return shots.stream()
                .map(TowerShot::ref)
                .filter(ref -> settlement(ref).compareTo(limit) > 0)
                .toList();
    }

    /**
     * Whether the survey found nothing.
     *
     * @return true when every tower was shot, plumb, and not settled too far
     */
    public boolean isClean() {
        return outOfPlumb().isEmpty() && settledTooFar().isEmpty() && missed().isEmpty();
    }

    /**
     * What the survey found, one sentence each: towers missed, then leaning, then settled.
     *
     * @return the findings
     */
    public List<String> findings() {
        List<String> found = new ArrayList<>();
        missed().forEach(ref -> found.add(ref + " was not shot"));
        outOfPlumb().forEach(ref -> found.add(ref + " leans " + lean(ref).formatDegrees()));
        settledTooFar()
                .forEach(
                        ref -> found.add(ref + " has settled " + settlement(ref).formatInches(2)));
        return List.copyOf(found);
    }

    /**
     * How many towers were shot.
     *
     * @return the shot count
     */
    public int size() {
        return shots.size();
    }

    private TowerShot shotOf(TowerRef ref) {
        return shot(ref).orElseThrow(() -> new PlantError(ref + " was not shot on " + taken));
    }
}
