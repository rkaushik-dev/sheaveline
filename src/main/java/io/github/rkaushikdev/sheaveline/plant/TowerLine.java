package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Length;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The towers of one lift, in order from the drive terminal up the hill.
 *
 * <p>The towers are numbered from one without gaps, and each stands further up the line than the
 * one below it. Towers are looked up by number, counting from one.
 *
 * @param towers the towers in number order
 */
public record TowerLine(List<Tower> towers) implements Iterable<Tower> {

    /**
     * A line of towers, put in number order and checked.
     *
     * @throws PlantError when there are none, they belong to more than one lift, their numbers have
     *     a gap, or their stations do not climb
     */
    public TowerLine {
        towers = towers.stream().sorted(Comparator.comparingInt(Tower::number)).toList();
        if (towers.isEmpty()) {
            throw new PlantError("a line has at least one tower");
        }
        if (towers.stream().map(Tower::lift).collect(Collectors.toSet()).size() > 1) {
            throw new PlantError("a tower line belongs to one lift");
        }
        for (int position = 1; position <= towers.size(); position++) {
            Tower tower = towers.get(position - 1);
            if (tower.number() != position) {
                throw new PlantError("tower numbers run from one without gaps, found " + tower.ref());
            }
        }
        for (int at = 1; at < towers.size(); at++) {
            Tower earlier = towers.get(at - 1);
            Tower later = towers.get(at);
            if (later.station().compareTo(earlier.station()) <= 0) {
                throw new PlantError(later.ref() + " is not further up the line than " + earlier.ref());
            }
        }
    }

    /**
     * The lift the line belongs to.
     *
     * @return its code
     */
    public LiftCode lift() {
        return towers.get(0).lift();
    }

    /**
     * How many towers there are.
     *
     * @return the count
     */
    public int size() {
        return towers.size();
    }

    @Override
    public Iterator<Tower> iterator() {
        return towers.iterator();
    }

    /**
     * A tower by number.
     *
     * @param number counting from one at the drive
     * @return the tower
     * @throws PlantError when the line has no such tower
     */
    public Tower tower(int number) {
        if (number < 1 || number > towers.size()) {
            throw new PlantError(lift() + " has no tower " + number);
        }
        return towers.get(number - 1);
    }

    /**
     * The tower nearest the drive.
     *
     * @return tower one
     */
    public Tower first() {
        return towers.get(0);
    }

    /**
     * The tower furthest up the line.
     *
     * @return the highest numbered tower
     */
    public Tower last() {
        return towers.get(towers.size() - 1);
    }

    /**
     * The towers that hold the rope down on either side.
     *
     * @return those towers in order
     */
    public List<Tower> holdDowns() {
        return towers.stream().filter(Tower::isHoldDown).toList();
    }

    /**
     * How many sheaves the whole line carries.
     *
     * @return the total
     */
    public int sheaveCount() {
        return towers.stream().mapToInt(Tower::sheaveCount).sum();
    }

    /**
     * The tower that bends the rope hardest, which is the one to watch. On a tie, the lower one.
     *
     * @return that tower
     */
    public Tower steepest() {
        Tower worst = towers.get(0);
        for (Tower tower : towers) {
            if (tower.totalDeviation().abs().millidegrees()
                    > worst.totalDeviation().abs().millidegrees()) {
                worst = tower;
            }
        }
        return worst;
    }

    /**
     * Where each tower stands.
     *
     * @return the stations in order
     */
    public List<Length> spanStations() {
        return towers.stream().map(Tower::station).toList();
    }
}
