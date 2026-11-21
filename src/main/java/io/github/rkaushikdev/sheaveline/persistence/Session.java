package io.github.rkaushikdev.sheaveline.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import java.util.Optional;

/**
 * One persistence context and the transaction it is in.
 *
 * <p>A session always has a transaction open. A commit makes the work so far stick and begins the
 * next transaction straight away, so work can carry on in the same session and the rows it loaded
 * stay loaded. A rollback undoes the work since the last commit, forgets every row the session
 * loaded, and begins again. Closing rolls back whatever was not committed: the failure mode of an
 * implicit commit is a half written record that looks finished.
 *
 * <pre>{@code
 * try (Session session = database.session()) {
 *     session.persist(row);
 *     session.commit();
 * }
 * }</pre>
 *
 * <p>Everything that reads or writes in one piece of work shares the one entity manager, so a row
 * written by one part of it is visible to the next without a commit. Queries go through {@link
 * #entityManager()}.
 */
public final class Session implements AutoCloseable {

    private final EntityManager entityManager;

    /**
     * A session over an entity manager somebody opened, its transaction begun here.
     *
     * @param entityManager the entity manager, which this session closes
     */
    Session(EntityManager entityManager) {
        this.entityManager = entityManager;
        entityManager.getTransaction().begin();
    }

    /**
     * The entity manager the session's work shares.
     *
     * @return the entity manager
     */
    public EntityManager entityManager() {
        return entityManager;
    }

    /**
     * Add a row, and whatever its cascades reach, to the session. A row whose key the database
     * generates is written straight away, so a rule it breaks is refused here rather than at the
     * commit.
     *
     * @param row the row
     */
    public void persist(Object row) {
        entityManager.persist(row);
    }

    /**
     * A row by its key.
     *
     * @param type the row's class
     * @param key its key
     * @param <T> the row's class
     * @return the row, or empty when there is none
     */
    public <T> Optional<T> find(Class<T> type, Object key) {
        return Optional.ofNullable(entityManager.find(type, key));
    }

    /**
     * Delete a row the session holds, and whatever its cascades reach.
     *
     * @param row the row
     */
    public void remove(Object row) {
        entityManager.remove(row);
    }

    /** Push the pending writes so the database can refuse them now. */
    public void flush() {
        entityManager.flush();
    }

    /** Make the work so far stick, and begin the next transaction. */
    public void commit() {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.commit();
        transaction.begin();
    }

    /** Undo the work since the last commit, forget every row loaded, and begin again. */
    public void rollback() {
        EntityTransaction transaction = entityManager.getTransaction();
        if (transaction.isActive()) {
            transaction.rollback();
        }
        entityManager.clear();
        transaction.begin();
    }

    /** Roll back whatever was not committed and close. Safe to call twice. */
    @Override
    public void close() {
        if (!entityManager.isOpen()) {
            return;
        }
        try {
            EntityTransaction transaction = entityManager.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            entityManager.close();
        }
    }
}
