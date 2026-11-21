package io.github.rkaushikdev.sheaveline.persistence;

import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The session plumbing every repository would otherwise repeat.
 *
 * <p>There is one repository a thing the department talks about. Each takes a session and hands
 * back domain records rather than rows, so nothing above this package reads a row. Everything that
 * reads a lift reads it through here, which is the only way the mapper stays the single place the
 * two shapes meet.
 *
 * <p>The fetch and complain pattern lives here once, with the sentence for the failure handed in, so
 * what is left in each repository is the part that is about ropes or incidents rather than about
 * sessions.
 */
public class Repository {

    private final Session session;

    /**
     * A repository over a session somebody else commits.
     *
     * @param session the session
     */
    public Repository(Session session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /**
     * The session this repository reads and writes in.
     *
     * @return the session
     */
    public final Session session() {
        return session;
    }

    /**
     * A row by its key, or say which one was asked for.
     *
     * @param kind the row's class
     * @param key its key
     * @param missing the sentence to fail with when there is no such row
     * @param <T> the row's class
     * @return the row
     * @throws StorageError with that sentence when there is no such row
     */
    protected final <T> T fetch(Class<T> kind, Object key, String missing) {
        return session.find(kind, key).orElseThrow(() -> new StorageError(missing));
    }

    /**
     * A row by its key, or nothing rather than a complaint.
     *
     * @param kind the row's class
     * @param key its key
     * @param <T> the row's class
     * @return the row, or empty
     */
    protected final <T> Optional<T> find(Class<T> kind, Object key) {
        return session.find(kind, key);
    }

    /**
     * The first row a query finds, or say what was asked for.
     *
     * @param query the query
     * @param missing the sentence to fail with when it finds nothing
     * @param <T> what it finds
     * @return the first row
     * @throws StorageError with that sentence when the query finds nothing
     */
    protected final <T> T one(TypedQuery<T> query, String missing) {
        return first(query).orElseThrow(() -> new StorageError(missing));
    }

    /**
     * The first row a query finds, if it finds any.
     *
     * @param query the query
     * @param <T> what it finds
     * @return the first row, or empty
     */
    protected final <T> Optional<T> first(TypedQuery<T> query) {
        return query.setMaxResults(1).getResultList().stream().findFirst();
    }

    /**
     * Every row a query finds, in the order it asks for.
     *
     * @param query the query
     * @param <T> what it finds
     * @return the rows
     */
    protected final <T> List<T> many(TypedQuery<T> query) {
        return List.copyOf(query.getResultList());
    }

    /**
     * A query in the session. The writes still pending are pushed first, so it sees them.
     *
     * @param statement the query, in the persistence query language
     * @param kind what it finds
     * @param <T> what it finds
     * @return the query, ready for its parameters
     */
    protected final <T> TypedQuery<T> query(String statement, Class<T> kind) {
        return session.entityManager().createQuery(statement, kind);
    }
}
