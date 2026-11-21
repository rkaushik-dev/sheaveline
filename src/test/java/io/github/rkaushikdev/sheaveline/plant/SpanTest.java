package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class SpanTest {

    private static final LiftCode JP = new LiftCode("JP");

    private static Support support(String label, long station, long elevation) {
        return Support.terminal(label, Length.ofFeet(station), Length.ofFeet(elevation));
    }

    private static Tower tower(int number, long station, long base, long height) {
        SheaveAssembly assembly = new SheaveAssembly(AssemblyKind.SUPPORT, 4, Angle.ofDegrees("1.2"));
        return new Tower(
                JP.tower(number),
                Length.ofFeet(station),
                Length.ofFeet(base),
                Length.ofFeet(height),
                assembly,
                assembly);
    }

    private static Tower tower(int number, long station, long base) {
        return tower(number, station, base, 32);
    }

    private static final Support BOTTOM = support("bottom terminal", 0, 6400);
    private static final Support TOP = support("top terminal", 4800, 8000);

    private static SpanLine buildLine() {
        TowerLine towers = new TowerLine(
                List.of(tower(1, 600, 6480), tower(2, 1600, 6900), tower(3, 2600, 7100), tower(4, 3800, 7600)));
        return SpanLine.between(BOTTOM, towers, TOP);
    }

    private static BigDecimal grade(Angle angle) {
        return Decimals.quantize(angle.percentGrade(), 3);
    }

    @Test
    void aSpanMeasuresAlongTheGroundAndUp() {
        Span span = new Span(1, BOTTOM, support("T1", 600, 6500));
        assertThat(span.horizontal()).isEqualTo(Length.ofFeet(600));
        assertThat(span.rise()).isEqualTo(Length.ofFeet(100));
        assertThat(span.isDownhill()).isFalse();
    }

    @Test
    void aSpanThatDropsHasANegativeRise() {
        Span span = new Span(1, support("T3", 1800, 7000), support("T4", 2400, 6960));
        assertThat(span.rise()).isEqualTo(Length.ofFeet(-40));
        assertThat(span.isDownhill()).isTrue();
        assertThat(span.slope().millidegrees()).isNegative();
    }

    @Test
    void theChordIsLongerThanTheGroundDistance() {
        Span span = new Span(1, support("a", 0, 0), support("b", 400, 300));
        assertThat(span.chord()).isEqualTo(Length.ofFeet(500));
        assertThat(span.chord()).isGreaterThan(span.horizontal());
    }

    @Test
    void theSlopeReadsAsAGrade() {
        Span span = new Span(1, support("a", 0, 0), support("b", 400, 100));
        assertThat(grade(span.slope())).isEqualByComparingTo("25");
    }

    @Test
    void theMidpointSitsHalfwayAlong() {
        Span span = new Span(1, support("a", 600, 0), support("b", 1400, 0));
        assertThat(span.midpointStation()).isEqualTo(Length.ofFeet(1000));
    }

    @Test
    void theChordElevationReadsAnywhereInsideTheSpan() {
        Span span = new Span(1, support("a", 0, 6400), support("b", 800, 6800));
        assertThat(span.chordElevationAt(Length.ofFeet(0))).isEqualTo(Length.ofFeet(6400));
        assertThat(span.chordElevationAt(Length.ofFeet(400))).isEqualTo(Length.ofFeet(6600));
        assertThat(span.chordElevationAt(Length.ofFeet(800))).isEqualTo(Length.ofFeet(6800));
    }

    @Test
    void theChordElevationRefusesAStationOutsideTheSpan() {
        Span span = new Span(1, support("a", 0, 6400), support("b", 800, 6800));
        assertThatThrownBy(() -> span.chordElevationAt(Length.ofFeet(900)))
                .isInstanceOf(PlantError.class)
                .hasMessage("station 900 is outside span 1");
    }

    @Test
    void aSpanHasToRunUpTheHill() {
        assertThatThrownBy(() -> new Span(1, TOP, BOTTOM))
                .isInstanceOf(PlantError.class)
                .hasMessage("span 1 does not run up the hill");
        assertThatThrownBy(() -> new Span(1, BOTTOM, BOTTOM)).isInstanceOf(PlantError.class);
    }

    @Test
    void spansAreNumberedFromOne() {
        assertThatThrownBy(() -> new Span(0, BOTTOM, TOP)).isInstanceOf(PlantError.class);
    }

    @Test
    void aSpanNamesTheSupportsItRunsBetween() {
        Span span = new Span(1, BOTTOM, TOP);
        assertThat(span.label()).isEqualTo("bottom terminal to top terminal");
        assertThat(span).hasToString("span 1, bottom terminal to top terminal, 4800 ft");
    }

    @Test
    void aSupportBuiltFromATowerReadsTheCrossarm() {
        Tower seven = tower(7, 2400, 7400, 32);
        Support holder = Support.ofTower(seven);
        assertThat(holder.kind()).isSameAs(SupportKind.TOWER);
        assertThat(holder.isTower()).isTrue();
        assertThat(holder.ropeElevation()).isEqualTo(Length.ofFeet(7432));
        assertThat(holder).hasToString("JP-T7");
    }

    @Test
    void aLineCutsIntoOneMoreSpanThanItHasTowers() {
        SpanLine line = buildLine();
        assertThat(line.size()).isEqualTo(5);
        assertThat(line.supports()).hasSize(6);
    }

    @Test
    void spansComeOutInOrderWithTheTerminalsOnTheEnds() {
        SpanLine line = buildLine();
        assertThat(line.span(1).lower()).isSameAs(BOTTOM);
        assertThat(line.span(5).upper()).isSameAs(TOP);
        assertThat(StreamSupport.stream(line.spliterator(), false).map(Span::index))
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void askingForASpanThatIsNotThere() {
        SpanLine line = buildLine();
        assertThatThrownBy(() -> line.span(6)).isInstanceOf(PlantError.class).hasMessage("there is no span 6");
        assertThatThrownBy(() -> line.span(0)).isInstanceOf(PlantError.class);
    }

    @Test
    void aLineNeedsTwoSupports() {
        assertThatThrownBy(() -> SpanLine.through(List.of(BOTTOM)))
                .isInstanceOf(PlantError.class)
                .hasMessage("a line has at least two supports");
    }

    @Test
    void theLineReportsItsLengthAndRise() {
        SpanLine line = buildLine();
        assertThat(line.horizontalLength()).isEqualTo(Length.ofFeet(4800));
        assertThat(line.verticalRise()).isEqualTo(Length.ofFeet(1600));
        assertThat(line.slopeLength()).isGreaterThan(line.horizontalLength());
        assertThat(grade(line.averageSlope())).isGreaterThan(new BigDecimal(30));
    }

    @Test
    void theLongestAndSteepestSpansAreFound() {
        SpanLine line = buildLine();
        assertThat(line.longest().index()).isEqualTo(4);
        assertThat(line.steepest().index()).isIn(2, 4, 5);
    }

    @Test
    void theDownhillSpansArePickedOut() {
        SpanLine dipping = SpanLine.through(List.of(
                support("a", 0, 6400), support("b", 800, 6800), support("c", 1600, 6700), support("d", 2400, 7200)));
        assertThat(dipping.downhillSpans().stream().map(Span::index)).containsExactly(2);
    }

    @Test
    void theSpanAStationFallsIn() {
        SpanLine line = buildLine();
        assertThat(line.containing(Length.ofFeet(2000)).index()).isEqualTo(3);
        assertThat(line.containing(Length.ofFeet(0)).index()).isEqualTo(1);
        assertThat(line.containing(Length.ofFeet(4800)).index()).isEqualTo(5);
        assertThatThrownBy(() -> line.containing(Length.ofFeet(5200)))
                .isInstanceOf(PlantError.class)
                .hasMessage("station 5200 is off the line");
    }
}
