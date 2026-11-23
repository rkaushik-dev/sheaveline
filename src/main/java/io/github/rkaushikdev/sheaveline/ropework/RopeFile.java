package io.github.rkaushikdev.sheaveline.ropework;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Everything known about one rope, read together.
 *
 * <p>Each source is optional, and a file with nothing in it has nothing against the rope. The magnetic
 * retest schedule is read only when the question names a day, because whether a test is overdue
 * depends on when it is asked.
 *
 * @param wear the latest wear survey
 * @param breaks the latest broken wire survey
 * @param magnetic the latest magnetic test
 * @param splices the splice register
 * @param tension the tension system
 * @param retest the magnetic retest schedule
 */
public record RopeFile(
        Optional<WearSurvey> wear,
        Optional<BreakSurvey> breaks,
        Optional<MagneticTest> magnetic,
        Optional<SpliceRegister> splices,
        Optional<TensionSystem> tension,
        Optional<RetestSchedule> retest) {

    /** A file. */
    public RopeFile {
        Objects.requireNonNull(wear, "wear");
        Objects.requireNonNull(breaks, "breaks");
        Objects.requireNonNull(magnetic, "magnetic");
        Objects.requireNonNull(splices, "splices");
        Objects.requireNonNull(tension, "tension");
        Objects.requireNonNull(retest, "retest");
    }

    /** A file with nothing in it yet. */
    public RopeFile() {
        this(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    /**
     * The same file with a wear survey in it.
     *
     * @param survey the survey
     * @return the new file
     */
    public RopeFile withWear(WearSurvey survey) {
        return new RopeFile(Optional.of(survey), breaks, magnetic, splices, tension, retest);
    }

    /**
     * The same file with a broken wire survey in it.
     *
     * @param survey the survey
     * @return the new file
     */
    public RopeFile withBreaks(BreakSurvey survey) {
        return new RopeFile(wear, Optional.of(survey), magnetic, splices, tension, retest);
    }

    /**
     * The same file with a magnetic test in it.
     *
     * @param test the test
     * @return the new file
     */
    public RopeFile withMagnetic(MagneticTest test) {
        return new RopeFile(wear, breaks, Optional.of(test), splices, tension, retest);
    }

    /**
     * The same file with a splice register in it.
     *
     * @param register the register
     * @return the new file
     */
    public RopeFile withSplices(SpliceRegister register) {
        return new RopeFile(wear, breaks, magnetic, Optional.of(register), tension, retest);
    }

    /**
     * The same file with a tension system in it.
     *
     * @param system the system
     * @return the new file
     */
    public RopeFile withTension(TensionSystem system) {
        return new RopeFile(wear, breaks, magnetic, splices, Optional.of(system), retest);
    }

    /**
     * The same file with a retest schedule in it.
     *
     * @param schedule the schedule
     * @return the new file
     */
    public RopeFile withRetest(RetestSchedule schedule) {
        return new RopeFile(wear, breaks, magnetic, splices, tension, Optional.of(schedule));
    }

    /**
     * Every row that fires, leaving the retest schedule unread.
     *
     * @return the findings, source by source
     */
    public List<Finding> findings() {
        return findings(Optional.empty());
    }

    /**
     * Every row that fires, as at a day.
     *
     * @param on the day asked about
     * @return the findings, source by source
     */
    public List<Finding> findings(LocalDate on) {
        return findings(Optional.of(on));
    }

    /**
     * The worst verdict in the file, leaving the retest schedule unread.
     *
     * @return the verdict
     */
    public WearVerdict verdict() {
        return Verdict.worstOf(findings());
    }

    /**
     * The worst verdict in the file, as at a day.
     *
     * @param on the day asked about
     * @return the verdict
     */
    public WearVerdict verdict(LocalDate on) {
        return Verdict.worstOf(findings(on));
    }

    /**
     * The sources with something against the rope, leaving the retest schedule unread.
     *
     * @return the source names, in the order they were read
     */
    public Set<String> sources() {
        return sourcesOf(findings());
    }

    /**
     * The sources with something against the rope, as at a day.
     *
     * @param on the day asked about
     * @return the source names, in the order they were read
     */
    public Set<String> sources(LocalDate on) {
        return sourcesOf(findings(on));
    }

    /**
     * The first finding at the file's verdict, leaving the retest schedule unread.
     *
     * @return the finding
     * @throws RopeworkError when nothing in the file has anything against the rope
     */
    public Finding worst() {
        return worstOf(findings());
    }

    /**
     * The first finding at the file's verdict, as at a day.
     *
     * @param on the day asked about
     * @return the finding
     * @throws RopeworkError when nothing in the file has anything against the rope
     */
    public Finding worst(LocalDate on) {
        return worstOf(findings(on));
    }

    /**
     * Whether the rope may carry passengers, leaving the retest schedule unread.
     *
     * @return true unless something condemns it
     */
    public boolean mayRun() {
        return verdict().mayRun();
    }

    /**
     * Whether the rope may carry passengers on a day.
     *
     * @param on the day asked about
     * @return true unless something condemns it
     */
    public boolean mayRun(LocalDate on) {
        return verdict(on).mayRun();
    }

    private List<Finding> findings(Optional<LocalDate> on) {
        List<Finding> found = new ArrayList<>();
        wear.ifPresent(survey -> found.addAll(Verdict.wearFindings(survey)));
        breaks.ifPresent(survey -> found.addAll(Verdict.breakFindings(survey)));
        magnetic.ifPresent(test -> found.addAll(Verdict.testFindings(test)));
        splices.ifPresent(register -> register.problems()
                .forEach(problem -> found.add(new Finding("splices", WearVerdict.CONDEMNED, problem))));
        if (tension.isPresent() && !tension.get().hasReserve()) {
            found.add(new Finding("tension", WearVerdict.MONITOR, "carriage has no travel in reserve"));
        }
        if (on.isPresent() && retest.isPresent() && retest.get().isOverdue(on.get())) {
            found.add(new Finding("magnetic test", WearVerdict.CONDEMNED, "magnetic test overdue"));
        }
        return List.copyOf(found);
    }

    private static Set<String> sourcesOf(List<Finding> findings) {
        Set<String> names = new LinkedHashSet<>();
        findings.forEach(found -> names.add(found.source()));
        return Collections.unmodifiableSet(names);
    }

    private static Finding worstOf(List<Finding> findings) {
        if (findings.isEmpty()) {
            throw new RopeworkError("nothing in this rope file has anything against it");
        }
        WearVerdict worst = Verdict.worstOf(findings);
        return findings.stream()
                .filter(one -> one.verdict() == worst)
                .findFirst()
                .orElseThrow();
    }
}
