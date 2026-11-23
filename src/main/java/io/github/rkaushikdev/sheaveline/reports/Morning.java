package io.github.rkaushikdev.sheaveline.reports;

import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The morning report, which is the one page the mountain manager reads at seven.
 *
 * <p>It answers one question a machine: is it opening, and if not, what is stopping it. Everything
 * else on the page is there because somebody asked why once and having the answer already printed
 * saved a telephone call.
 *
 * <p>The page renders gathered facts rather than reaching for a database. That is not tidiness for
 * its own sake: the report has to be printable from last Tuesday's records, and a renderer that goes
 * and looks things up can only ever print today.
 *
 * @param day the day
 * @param resort who the page is printed for
 * @param snow what the patrol reported overnight
 * @param machines every machine, in the order the page lists them
 */
public record Morning(LocalDate day, String resort, String snow, List<MachineLine> machines) {

    /** A morning. */
    public Morning {
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(resort, "resort");
        Objects.requireNonNull(snow, "snow");
        machines = List.copyOf(machines);
    }

    /**
     * Everything the office gathered for one day.
     *
     * @param day the day
     * @param resort who the page is printed for
     * @param snow what the patrol reported overnight
     * @param machines every machine
     * @return the morning
     */
    public static Morning of(LocalDate day, String resort, String snow, List<MachineLine> machines) {
        return new Morning(day, resort, snow, machines);
    }

    /**
     * The machines that open.
     *
     * @return them, in page order
     */
    public List<MachineLine> opening() {
        return machines.stream().filter(MachineLine::opening).toList();
    }

    /**
     * The machines that do not.
     *
     * @return them, in page order
     */
    public List<MachineLine> held() {
        return machines.stream().filter(one -> !one.opening()).toList();
    }

    /**
     * Every open work order that keeps a machine from carrying the public this morning, an urgent
     * order past its deadline included.
     *
     * @return the orders, machine by machine
     */
    public List<WorkOrder> urgentOrders() {
        return machines.stream()
                .flatMap(machine -> machine.orders().stream())
                .filter(order -> order.stopsTheMachine(day))
                .toList();
    }

    /**
     * The page.
     *
     * @return the report
     */
    public Report render() {
        Report page = new Report();
        page.heading(resort + " morning report");
        page.field("day", day.toString());
        page.field("settled snow", snow);
        page.field("machines opening", opening().size() + " of " + machines.size());

        page.section("the machines");
        page.table(Table.of(
                List.of(
                        new Column("lift", 5),
                        new Column("name", 20),
                        new Column("status", 15),
                        new Column("verdict", 12),
                        new Column("orders", 6, Align.RIGHT),
                        new Column("due", 6, Align.RIGHT)),
                machines.stream()
                        .map(one -> List.of(
                                one.lift().toString(),
                                one.name(),
                                one.status().value(),
                                one.verdict(),
                                one.orders().size(),
                                one.upkeepDue().size()))
                        .toList()));

        List<MachineLine> stopped = held();
        if (!stopped.isEmpty()) {
            page.section("what is stopping them");
            for (MachineLine machine : stopped) {
                page.line(machine.lift() + " " + machine.name());
                for (String reason : machine.reasons()) {
                    page.line("    " + reason);
                }
            }
        }

        List<WorkOrder> urgent = urgentOrders();
        if (!urgent.isEmpty()) {
            page.section("work orders that stop a machine");
            page.table(Table.of(
                    List.of(
                            new Column("no", 4, Align.RIGHT),
                            new Column("lift", 5),
                            new Column("where", 12),
                            new Column("raised", 11),
                            new Column("detail", 42)),
                    urgent.stream()
                            .map(order -> List.of(
                                    order.number(),
                                    order.lift().toString(),
                                    order.where().orElse(""),
                                    order.raised().toString(),
                                    order.detail()))
                            .toList()));
        }

        List<List<Object>> due = new ArrayList<>();
        for (MachineLine machine : machines) {
            for (String code : machine.upkeepDue()) {
                due.add(List.of(machine.lift().toString(), code));
            }
        }
        if (!due.isEmpty()) {
            page.section("falling due");
            page.table(Table.of(List.of(new Column("lift", 5), new Column("job", 6)), due));
        }

        page.blank();
        page.line("printed for " + resort + ", " + day);
        return page;
    }

    /**
     * The page as text.
     *
     * @return every line, each ended by a newline
     */
    public String text() {
        return render().text();
    }
}
