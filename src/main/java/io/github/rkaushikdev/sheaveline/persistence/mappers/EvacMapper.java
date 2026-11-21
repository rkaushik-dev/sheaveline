package io.github.rkaushikdev.sheaveline.persistence.mappers;

import io.github.rkaushikdev.sheaveline.evac.Drill;
import io.github.rkaushikdev.sheaveline.evac.DrillRegister;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.persistence.models.DrillRow;
import io.github.rkaushikdev.sheaveline.persistence.models.DrillTeamRow;
import java.util.List;

/**
 * Drills, both ways.
 *
 * <p>The teams that took part are their own rows rather than a joined string, because the question
 * the register answers is which teams are short of a drill this season, and that is a set operation.
 * It is also the question the state asks in the annual packet, so getting it out of a comma separated
 * column once a year is not the shape to be in.
 *
 * <p>The elapsed time goes down as whole seconds. A drill is timed with a stopwatch by somebody in a
 * harness and the part of a second is noise, but storing it as a fraction would still let a rounding
 * error decide whether a machine cleared inside its allowed time.
 */
public final class EvacMapper {

    private EvacMapper() {}

    /**
     * A drill as a row, with a row for each team in name order.
     *
     * @param drill the drill
     * @return a new row
     */
    public static DrillRow drillToRow(Drill drill) {
        DrillRow row = new DrillRow(
                drill.lift(),
                drill.on(),
                drill.kind(),
                drill.carriersWorked(),
                Math.toIntExact(drill.elapsed().seconds()),
                drill.observer());
        drill.teams().stream().sorted().forEach(team -> row.addTeam(new DrillTeamRow(team)));
        return row;
    }

    /**
     * A drill back out of its row, its teams in name order.
     *
     * @param row the row
     * @return the drill
     */
    public static Drill drillFromRow(DrillRow row) {
        return Drill.of(
                row.getLiftCode(),
                row.getOn(),
                row.getKind(),
                row.getTeams().stream().map(DrillTeamRow::getTeam).sorted().toList(),
                row.getCarriersWorked(),
                Duration.ofSeconds(row.getElapsedSeconds()),
                row.getObserver());
    }

    /**
     * The drill register out of its rows.
     *
     * @param rows the rows
     * @return the register
     */
    public static DrillRegister registerFromRows(List<DrillRow> rows) {
        return DrillRegister.of(rows.stream().map(EvacMapper::drillFromRow).toList());
    }
}
