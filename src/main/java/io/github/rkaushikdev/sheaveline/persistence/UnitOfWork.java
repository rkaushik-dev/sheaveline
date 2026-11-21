package io.github.rkaushikdev.sheaveline.persistence;

import java.util.Objects;

/**
 * The unit of work, which is the only thing above the repositories that knows about commits.
 *
 * <p>Everything the department does is a small transaction with an obvious boundary. Sign off a
 * morning walk. Record a rope survey. Raise a work order off a defect. Each of those either happens
 * or does not, and a half written one is worse than none, because the next person reads it and
 * believes it.
 *
 * <p>So the boundary is a block that rolls back unless somebody says commit, and the repositories
 * only exist inside it. There is deliberately no way to get a repository without an open unit of
 * work around it.
 *
 * <pre>{@code
 * try (UnitOfWork work = UnitOfWork.on(database).open()) {
 *     work.lifts().add(lift);
 *     work.commit();
 * }
 * }</pre>
 *
 * <p>A unit can be opened again once it has closed, and each opening is a fresh session. Every
 * repository it hands out shares that one session, so what one writes the next can read before
 * anything is committed.
 */
public final class UnitOfWork implements AutoCloseable {

    private final Database database;
    private Session session;
    private boolean committed;

    private UnitOfWork(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * A unit of work on a database, not yet open.
     *
     * @param database the database
     * @return the unit, which {@link #open()} begins
     */
    public static UnitOfWork on(Database database) {
        return new UnitOfWork(database);
    }

    /**
     * Begin: open a session for the repositories to share.
     *
     * @return this unit, for a {@code try} block to close
     * @throws StorageError when it is already open
     */
    public UnitOfWork open() {
        if (session != null) {
            throw new StorageError("this unit of work is already open");
        }
        session = database.session();
        committed = false;
        return this;
    }

    /**
     * End: roll back whatever was not committed and close the session. Safe to call on a unit that
     * is not open.
     */
    @Override
    public void close() {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } finally {
            session = null;
        }
    }

    /**
     * The session the repositories share.
     *
     * @return the session
     * @throws StorageError when the unit is not open
     */
    public Session session() {
        if (session == null) {
            throw new StorageError("this unit of work is not open");
        }
        return session;
    }

    /**
     * Whether the unit is open.
     *
     * @return true between {@link #open()} and {@link #close()}
     */
    public boolean isOpen() {
        return session != null;
    }

    /**
     * Whether the work so far was committed, which a rollback undoes.
     *
     * @return true after a commit and before any rollback
     */
    public boolean wasCommitted() {
        return committed;
    }

    /**
     * Make the work so far stick.
     *
     * @throws StorageError when the unit is not open
     */
    public void commit() {
        session().commit();
        committed = true;
    }

    /**
     * Undo the work since the last commit.
     *
     * @throws StorageError when the unit is not open
     */
    public void rollback() {
        session().rollback();
        committed = false;
    }

    /**
     * Push the pending writes so the database can refuse them now.
     *
     * @throws StorageError when the unit is not open
     */
    public void flush() {
        session().flush();
    }

    /**
     * The machines.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public LiftRepository lifts() {
        return new LiftRepository(session());
    }

    /**
     * The haul ropes.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public RopeRepository ropes() {
        return new RopeRepository(session());
    }

    /**
     * The rope walks.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public WearSurveyRepository wearSurveys() {
        return new WearSurveyRepository(session());
    }

    /**
     * The incidents and their filings.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public IncidentRepository incidents() {
        return new IncidentRepository(session());
    }

    /**
     * The inspection records and their defects.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public InspectionRepository inspections() {
        return new InspectionRepository(session());
    }

    /**
     * The weather stations and their readings.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public WeatherRepository weather() {
        return new WeatherRepository(session());
    }

    /**
     * The work orders.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public WorkOrderRepository workOrders() {
        return new WorkOrderRepository(session());
    }

    /**
     * The day book, stops, holds and meters.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public DayLogRepository dayLogs() {
        return new DayLogRepository(session());
    }

    /**
     * The cards and the shifts.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public PersonnelRepository personnel() {
        return new PersonnelRepository(session());
    }

    /**
     * The drill register.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public DrillRepository drills() {
        return new DrillRepository(session());
    }

    /**
     * The alignment surveys.
     *
     * @return a repository on this unit's session
     * @throws StorageError when the unit is not open
     */
    public AlignmentRepository alignment() {
        return new AlignmentRepository(session());
    }
}
