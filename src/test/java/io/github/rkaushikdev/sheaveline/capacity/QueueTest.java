package io.github.rkaushikdev.sheaveline.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QueueTest {

    private static UphillCapacity capacity(int carriers, int seats, String occupancy) {
        LineSpacing line = new LineSpacing(Length.ofFeet(10000), carriers, Speed.ofFeetPerMinute(1000));
        return new UphillCapacity(line, seats, Decimals.of(occupancy));
    }

    private static UphillCapacity capacity(String occupancy) {
        return capacity(100, 4, occupancy);
    }

    private static UphillCapacity capacity() {
        return capacity("0.85");
    }

    private static Maze maze(int lanes, int people, boolean singles) {
        return new Maze(lanes, people, singles);
    }

    private static Maze maze(int lanes, int people) {
        return maze(lanes, people, false);
    }

    private static Maze maze() {
        return maze(6, 40);
    }

    private static Queue queue(int arrivals) {
        return new Queue(capacity(), maze(), arrivals);
    }

    private static Queue queue() {
        return queue(1500);
    }

    @Test
    void aMazeHoldsItsLanesTimesItsDepth() {
        assertThat(maze(6, 40).holds()).isEqualTo(240);
        assertThatThrownBy(() -> maze(0, 40)).isInstanceOf(CapacityError.class);
        assertThatThrownBy(() -> maze(6, 0)).isInstanceOf(CapacityError.class);
    }

    @Test
    void aSinglesLaneIsAPropertyOfTheMaze() {
        assertThat(maze().singlesLane()).isFalse();
        assertThat(maze().withSingles().singlesLane()).isTrue();
    }

    @Test
    void peopleCannotArriveAtANegativeRate() {
        assertThatThrownBy(() -> queue(-1)).isInstanceOf(CapacityError.class);
    }

    @Test
    void theThroughputIsTheCapacityAtTheLoadingEfficiency() {
        assertThat(queue().throughput()).isEqualTo(capacity().personsPerHour());
    }

    @Test
    void aSinglesLaneLiftsTheThroughputWithoutTouchingTheMachine() {
        Queue plain = queue();
        Queue singles = plain.withMaze(maze().withSingles());
        assertThat(singles.throughput()).isGreaterThan(plain.throughput());
        assertThat(singles.capacity().spacing().speed())
                .isEqualTo(plain.capacity().spacing().speed());
    }

    @Test
    void theSinglesLiftIsCappedAtAFullChair() {
        Queue full = new Queue(capacity("0.95"), maze().withSingles(), 1000);
        assertThat(full.occupancy()).isLessThanOrEqualTo(Decimals.of("0.98"));
    }

    @Test
    void aMachineKeepingUpHasNoQueueAtAll() {
        Queue quiet = queue(1000);
        assertThat(quiet.keepsUp()).isTrue();
        assertThat(quiet.surplusAnHour()).isZero();
        assertThat(quiet.lengthAfter(Duration.ofHours(3))).isZero();
        assertThat(quiet.waitAfter(Duration.ofHours(3))).isEqualTo(new Duration(0));
    }

    @Test
    void aMachineKeepingUpReportsItsSpare() {
        Queue quiet = queue(1000);
        assertThat(quiet.spareAnHour()).isEqualTo(quiet.throughput() - 1000);
    }

    @Test
    void aQueueGrowsByTheDifferenceEveryHour() {
        Queue busy = queue(2500);
        int surplus = busy.surplusAnHour();
        assertThat(surplus).isPositive();
        assertThat(busy.lengthAfter(Duration.ofHours(1))).isEqualTo(surplus);
        assertThat(busy.lengthAfter(Duration.ofHours(2))).isEqualTo(surplus * 2);
    }

    @Test
    void theWaitIsTheQueueOverTheThroughput() {
        Queue busy = queue(2500);
        int standing = busy.lengthAfter(Duration.ofHours(1));
        Duration expected =
                Duration.ofSeconds(Decimals.divide(Decimals.multiply(Decimals.of(standing), 3600), busy.throughput())
                        .toBigInteger()
                        .longValueExact());
        assertThat(busy.waitAfter(Duration.ofHours(1))).isEqualTo(expected);
    }

    @Test
    void theWaitGrowsWithTheTimeAtTheRate() {
        Queue busy = queue(2500);
        assertThat(busy.waitAfter(Duration.ofHours(2))).isGreaterThan(busy.waitAfter(Duration.ofHours(1)));
    }

    @Test
    void aSinglesLaneShortensTheWait() {
        Queue busy = queue(2500);
        Queue singles = busy.withMaze(maze().withSingles());
        assertThat(singles.waitAfter(Duration.ofHours(1))).isLessThan(busy.waitAfter(Duration.ofHours(1)));
    }

    @Test
    void theQueueLeavesTheMazeEventually() {
        Queue busy = queue(2500);
        assertThat(busy.overflowsAfter(Duration.ofMinutes(10))).isFalse();
        assertThat(busy.overflowsAfter(Duration.ofHours(4))).isTrue();
    }

    @Test
    void theMazeFillsAtATimeThatCanBeWorkedOut() {
        Queue busy = queue(2500);
        Optional<Duration> fills = busy.fillsIn();
        assertThat(fills).isPresent();
        long seconds = fills.orElseThrow().seconds();
        assertThat(busy.overflowsAfter(Duration.ofSeconds(seconds - 60))).isFalse();
        assertThat(busy.overflowsAfter(Duration.ofSeconds(seconds + 60))).isTrue();
    }

    @Test
    void aMachineKeepingUpNeverFillsItsMaze() {
        assertThat(queue(1000).fillsIn()).isEmpty();
    }

    @Test
    void aBiggerMazeTakesLongerToFill() {
        Queue busy = queue(2500);
        Queue bigger = busy.withMaze(maze(12, 40));
        Optional<Duration> small = busy.fillsIn();
        Optional<Duration> large = bigger.fillsIn();
        assertThat(small).isPresent();
        assertThat(large).isPresent();
        assertThat(large.orElseThrow()).isGreaterThan(small.orElseThrow());
    }

    @Test
    void clearingAQueueTakesTheQueueOverTheThroughput() {
        Queue busy = queue(2500);
        assertThat(busy.clearsIn(0)).isEqualTo(new Duration(0));
        Duration cleared = busy.clearsIn(busy.throughput());
        assertThat(cleared).isEqualTo(Duration.ofHours(1));
        assertThatThrownBy(() -> busy.clearsIn(-1)).isInstanceOf(CapacityError.class);
    }

    @Test
    void changingTheArrivalsMakesANewQueue() {
        Queue quiet = queue(1000);
        Queue busy = quiet.withArrivals(3000);
        assertThat(quiet.keepsUp()).isTrue();
        assertThat(busy.keepsUp()).isFalse();
    }

    @Test
    void aQueuePrintsWhetherItIsKeepingUp() {
        assertThat(queue(1000).toString()).contains("keeping up");
        assertThat(queue(3000).toString()).contains("behind");
    }

    @Test
    void aLineWithAlmostNoCapacityStillAnswersRatherThanDividingByNothing() {
        Queue thin = new Queue(capacity(1, 1, "0.01"), maze(), 500);
        assertThat(thin.throughput()).isGreaterThanOrEqualTo(0);
        if (thin.throughput() == 0) {
            assertThatThrownBy(() -> thin.clearsIn(10)).isInstanceOf(CapacityError.class);
        }
    }
}
