package io.github.rkaushikdev.sheaveline.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FormsTest {

    @Test
    void aColumnPlacesItsTextTheWayItSays() {
        assertThat(new Column("code", 6).cell("JP")).isEqualTo("JP    ");
        assertThat(new Column("count", 6, Align.RIGHT).cell(84)).isEqualTo("    84");
        assertThat(new Column("name", 7, Align.CENTRE).cell("abc")).isEqualTo("  abc  ");
    }

    @Test
    void nothingBecomesAnEmptyCellRatherThanTheWordNone() {
        assertThat(new Column("code", 4).cell(Optional.empty())).isEqualTo("    ");
    }

    @Test
    void aCellTooWideIsTruncatedRatherThanWrapped() {
        assertThat(new Column("name", 6).cell("John Paul")).isEqualTo("John …");
        assertThat(new Column("x", 1).cell("abc")).isEqualTo("a");
    }

    @Test
    void aColumnNeedsAWidthItsHeadingFitsIn() {
        assertThatThrownBy(() -> new Column("code", 0)).isInstanceOf(ReportError.class);
        assertThatThrownBy(() -> new Column("a rather long heading", 4)).isInstanceOf(ReportError.class);
    }

    @Test
    void aTableLinesUpItsColumns() {
        Table table = Table.of(
                List.of(new Column("code", 4), new Column("carriers", 8, Align.RIGHT)),
                List.of(List.of("JP", 84), List.of("SB", 46)));
        List<String> lines = table.lines();
        assertThat(lines.get(0)).isEqualTo("code carriers");
        assertThat(lines.get(1)).isEqualTo("---- --------");
        assertThat(lines.get(2)).isEqualTo("JP         84");
        assertThat(lines.get(3)).isEqualTo("SB         46");
        assertThat(lines.stream().map(String::length).distinct()).hasSize(1);
    }

    @Test
    void aTableReportsItsOwnWidth() {
        Table table = Table.of(List.of(new Column("a", 4), new Column("b", 5)));
        assertThat(table.width()).isEqualTo(10);
    }

    @Test
    void aTableWiderThanThePageIsRefused() {
        assertThatThrownBy(() -> Table.of(List.of(new Column("a", 60), new Column("b", 40))))
                .isInstanceOf(ReportError.class);
    }

    @Test
    void aTableNeedsColumnsAndRowsThatFitThem() {
        assertThatThrownBy(() -> Table.of(List.of())).isInstanceOf(ReportError.class);
        assertThatThrownBy(() -> Table.of(List.of(new Column("a", 4)), List.of(List.of("one", "two"))))
                .isInstanceOf(ReportError.class);
    }

    @Test
    void aRowCanBeAddedToATable() {
        Table table = Table.of(List.of(new Column("code", 4))).withRow(List.of("JP"));
        assertThat(table.rows()).hasSize(1);
        assertThat(table.lines().get(table.lines().size() - 1)).isEqualTo("JP  ");
    }

    @Test
    void proseBreaksAtSpacesAndNowhereElse() {
        List<String> lines = Forms.wrap("the rope is not on the chord so work out where it hangs", 20);
        assertThat(lines).allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(20));
        assertThat(String.join(" ", lines)).isEqualTo("the rope is not on the chord so work out where it hangs");
    }

    @Test
    void aWordLongerThanTheLineIsCut() {
        assertThat(Forms.wrap("supercalifragilistic", 8)).containsExactly("supercal");
    }

    @Test
    void wrappingNothingGivesOneEmptyLine() {
        assertThat(Forms.wrap("   ", 20)).containsExactly("");
        assertThatThrownBy(() -> Forms.wrap("anything", 0)).isInstanceOf(ReportError.class);
    }

    @Test
    void thePageFurniture() {
        assertThat(Forms.rule("=", 5)).isEqualTo("=====");
        assertThat(Forms.centre("JP", 6)).isEqualTo("  JP");
        assertThat(Forms.field("lift", "John Paul", 10)).isEqualTo("lift       John Paul");
    }

    @Test
    void aReportBuildsUpAPage() {
        Report page = new Report();
        page.heading("morning report");
        page.field("lift", "John Paul");
        page.blank();
        page.line("clear to open");
        List<String> lines = page.lines();
        assertThat(lines.get(0).strip()).isEqualTo("MORNING REPORT");
        assertThat(lines.get(1)).isEqualTo("=".repeat(Forms.PAGE_WIDTH));
        assertThat(lines.get(2)).startsWith("lift");
        assertThat(lines.get(3)).isEmpty();
        assertThat(lines.get(4)).isEqualTo("clear to open");
        assertThat(page.size()).isEqualTo(5);
    }

    @Test
    void aReportRefusesALineThatDoesNotFit() {
        Report page = new Report(20);
        assertThatThrownBy(() -> page.line("a".repeat(21))).isInstanceOf(ReportError.class);
    }

    @Test
    void aPageNarrowerThanTwentyColumnsIsNotAPage() {
        assertThatThrownBy(() -> new Report(10)).isInstanceOf(ReportError.class);
    }

    @Test
    void aSectionUnderlinesOnlyItsOwnHeading() {
        Report page = new Report();
        page.section("rope file");
        List<String> lines = page.lines();
        assertThat(lines.get(0)).isEmpty();
        assertThat(lines.get(1)).isEqualTo("rope file");
        assertThat(lines.get(2)).isEqualTo("-".repeat("rope file".length()));
    }

    @Test
    void proseInAReportIsWrappedToThePage() {
        Report page = new Report(30);
        page.prose("the rope is not on the chord so work out where it actually hangs");
        assertThat(page.lines()).allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(30));
        assertThat(page.size()).isGreaterThan(1);
    }

    @Test
    void aTableCanBePrintedIntoAReport() {
        Report page = new Report();
        page.table(Table.of(List.of(new Column("code", 4)), List.of(List.of("JP"), List.of("SB"))));
        assertThat(page.lines().get(page.size() - 1)).isEqualTo("SB  ");
    }

    @Test
    void theTextOfAReportEndsWithANewline() {
        Report page = new Report();
        page.line("one").line("two");
        assertThat(page.text()).isEqualTo("one\ntwo\n");
    }

    @Test
    void nothingInAReportIsEverWiderThanThePage() {
        Report page = new Report();
        page.heading("season summary");
        page.section("the machines");
        page.table(Table.of(
                List.of(new Column("code", 6), new Column("name", 24), new Column("carriers", 8, Align.RIGHT)),
                List.of(List.of("JP", "John Paul", 84))));
        page.prose("a".repeat(200));
        assertThat(page.lines()).allSatisfy(one -> assertThat(one.length()).isLessThanOrEqualTo(Forms.PAGE_WIDTH));
    }

    @Test
    void buildingAReportHandsTheReportBackSoCallsChain() {
        Report page = new Report().line("one").blank().line("two");
        assertThat(page.size()).isEqualTo(3);
    }
}
