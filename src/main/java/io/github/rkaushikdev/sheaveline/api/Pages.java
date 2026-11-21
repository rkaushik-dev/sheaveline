package io.github.rkaushikdev.sheaveline.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Reads a {@link Paging} off the query string for every route that pages: {@code limit} and {@code
 * offset}, each defaulted when absent, held to the bounds the paging declares.
 */
final class Pages implements HandlerMethodArgumentResolver {

    static final String LIMIT = "limit";
    static final String OFFSET = "offset";

    private final Validator validator;

    Pages(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == Paging.class;
    }

    @Override
    public Paging resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binders) {
        Paging paging = new Paging(whole(request, LIMIT, Paging.DEFAULT_LIMIT), whole(request, OFFSET, 0));
        validator.validate(paging).stream()
                .min(Comparator.comparing((ConstraintViolation<Paging> problem) ->
                        problem.getPropertyPath().toString()))
                .ifPresent(problem -> {
                    throw new RequestRejected(problem.getPropertyPath().toString(), problem.getMessage());
                });
        return paging;
    }

    private static int whole(NativeWebRequest request, String name, int absent) {
        String given = request.getParameter(name);
        if (given == null) {
            return absent;
        }
        try {
            return Integer.parseInt(given.strip());
        } catch (NumberFormatException unreadable) {
            throw new RequestRejected(name, "Input should be a valid integer, unable to parse string as an integer");
        }
    }
}
