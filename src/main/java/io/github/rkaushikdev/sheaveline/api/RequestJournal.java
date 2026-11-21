package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.journal.Journal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * One journal line a request, with an id the caller can quote back.
 *
 * <p>The id goes out on the response as well as into the journal, because the useful version of a
 * bug report is somebody reading twelve characters off a tablet screen rather than describing what
 * they were doing at the time. A caller that sends its own id in {@value #REQUEST_ID_HEADER} keeps
 * it.
 *
 * <p>The line is written after the response, not before, so it can carry the status and the
 * milliseconds. A request that raises still gets its line, because the one that fell over is the
 * one somebody is going to come looking for.
 */
@Component
public final class RequestJournal extends OncePerRequestFilter {

    /** The header the id travels in, both ways. */
    public static final String REQUEST_ID_HEADER = "x-request-id";

    /** The request attribute the id is kept in while the request runs. */
    public static final String REQUEST_ID_STATE = "request_id";

    private static final double NANOS_PER_MILLISECOND = 1_000_000.0;

    private final Journal journal;

    /**
     * A filter writing to the settings' journal.
     *
     * @param settings the running copy's settings
     */
    public RequestJournal(Settings settings) {
        this.journal = settings.journal();
    }

    /**
     * The id of the request being served.
     *
     * @param request the request
     * @return its id, or empty text outside a request this filter saw
     */
    public static String requestIdOf(HttpServletRequest request) {
        return request.getAttribute(REQUEST_ID_STATE) instanceof String held ? held : "";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(REQUEST_ID_HEADER);
        String wanted = given == null || given.isEmpty() ? Journal.newRequestId() : given;
        request.setAttribute(REQUEST_ID_STATE, wanted);
        response.setHeader(REQUEST_ID_HEADER, wanted);
        Journal book = journal.about("request", wanted);
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException wentWrong) {
            book.error(
                    "request",
                    "method",
                    request.getMethod(),
                    "path",
                    path,
                    "ms",
                    millisecondsSince(started),
                    "outcome",
                    "raised",
                    "error",
                    causeOf(wentWrong).getClass().getSimpleName());
            throw wentWrong;
        }
        book.info(
                "request",
                "method",
                request.getMethod(),
                "path",
                path,
                "status",
                response.getStatus(),
                "ms",
                millisecondsSince(started));
    }

    private static long millisecondsSince(long started) {
        return (long) Math.rint((System.nanoTime() - started) / NANOS_PER_MILLISECOND);
    }

    /** What actually went wrong, out from under the wrapping the dispatcher puts round it. */
    private static Throwable causeOf(Throwable wentWrong) {
        return wentWrong instanceof ServletException && wentWrong.getCause() != null ? wentWrong.getCause() : wentWrong;
    }
}
