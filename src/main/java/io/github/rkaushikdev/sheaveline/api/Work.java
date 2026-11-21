package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * One unit of work a request, rolled back unless the route commits.
 *
 * <p>A route that takes a {@link UnitOfWork} or a {@link Lookup} gets one opened for its request,
 * and every argument of the route that asks gets the same one, so what the lookup reads and what
 * the route writes are one session and one commit. The route commits when it has done its work,
 * before its answer is written; the unit is closed when the request is over, whatever happened,
 * and closing rolls back anything the route did not commit.
 *
 * <p>A route that takes neither, such as the health check, never opens one, so it never touches
 * the database.
 */
public final class Work implements HandlerInterceptor, HandlerMethodArgumentResolver {

    private static final String UNIT = Work.class.getName() + ".unit";

    private final Settings settings;

    /**
     * Units of work on the settings' database.
     *
     * @param settings the running copy's settings
     */
    public Work(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        return type == UnitOfWork.class || type == Lookup.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binders) {
        HttpServletRequest http = request.getNativeRequest(HttpServletRequest.class);
        UnitOfWork unit = unitOf(Objects.requireNonNull(http, "an http request"));
        return parameter.getParameterType() == Lookup.class ? new Lookup(unit) : unit;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
        if (request.getAttribute(UNIT) instanceof UnitOfWork unit) {
            request.removeAttribute(UNIT);
            unit.close();
        }
    }

    private UnitOfWork unitOf(HttpServletRequest request) {
        if (request.getAttribute(UNIT) instanceof UnitOfWork open) {
            return open;
        }
        UnitOfWork unit = UnitOfWork.on(settings.database()).open();
        request.setAttribute(UNIT, unit);
        return unit;
    }
}
