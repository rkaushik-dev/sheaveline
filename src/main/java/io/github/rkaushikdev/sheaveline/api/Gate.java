package io.github.rkaushikdev.sheaveline.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * The door every route with a {@link Requires} goes through: a key that may do the job, or a 401.
 *
 * <p>It runs before Spring resolves a single argument of the route, so a caller who may not be here
 * is told so before anybody reads what they sent, and before a unit of work is opened for them.
 * Health and readiness carry no {@code Requires} and need no key, because a service that will not
 * say whether it is alive without being authenticated is a service nobody can monitor.
 */
public final class Gate implements HandlerInterceptor {

    /** The header a client puts its key in. */
    public static final String API_KEY_HEADER = "x-api-key";

    private final Settings settings;

    /**
     * A gate checking keys against the settings' ring, on the settings' today.
     *
     * @param settings the running copy's settings
     */
    public Gate(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            Requires needs = method.getMethodAnnotation(Requires.class);
            if (needs != null) {
                keyOf(request, needs.value());
            }
        }
        return true;
    }

    /**
     * The key on a request, checked for a role.
     *
     * @param request the request
     * @param role the role the job needs
     * @return the key
     * @throws AuthError when no key was presented, or the key will not do
     */
    public ApiKey keyOf(HttpServletRequest request, Role role) {
        String presented = request.getHeader(API_KEY_HEADER);
        if (presented == null || presented.isEmpty()) {
            throw new AuthError("no key was presented");
        }
        return settings.keys().require(presented, settings.today(), role);
    }
}
