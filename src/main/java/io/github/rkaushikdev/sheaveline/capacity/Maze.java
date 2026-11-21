package io.github.rkaushikdev.sheaveline.capacity;

/**
 * The corral at the bottom of the lift, and how many people it holds.
 *
 * @param lanes the lanes in it
 * @param peopleALane how many people stand in one lane
 * @param singlesLane whether one lane is kept for people riding alone
 */
public record Maze(int lanes, int peopleALane, boolean singlesLane) {

    /**
     * A maze with lanes that hold people.
     *
     * @throws CapacityError when there is no lane or a lane holds nobody
     */
    public Maze {
        if (lanes < 1) {
            throw new CapacityError("a maze has at least one lane");
        }
        if (peopleALane < 1) {
            throw new CapacityError("a lane holds people");
        }
    }

    /**
     * A maze with no singles lane.
     *
     * @param lanes the lanes in it
     * @param peopleALane how many people stand in one lane
     */
    public Maze(int lanes, int peopleALane) {
        this(lanes, peopleALane, false);
    }

    /**
     * How many people the maze holds.
     *
     * @return the lanes times their depth
     */
    public int holds() {
        return lanes * peopleALane;
    }

    /**
     * The same maze with a singles lane.
     *
     * @return the new maze
     */
    public Maze withSingles() {
        return new Maze(lanes, peopleALane, true);
    }
}
