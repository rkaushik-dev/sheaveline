package io.github.rkaushikdev.sheaveline.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import io.github.rkaushikdev.sheaveline.MeasureError;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.SheavelineError;
import io.github.rkaushikdev.sheaveline.board.BoardError;
import io.github.rkaushikdev.sheaveline.capacity.CapacityError;
import io.github.rkaushikdev.sheaveline.evac.EvacuationError;
import io.github.rkaushikdev.sheaveline.line.LineError;
import io.github.rkaushikdev.sheaveline.ops.OperationsError;
import io.github.rkaushikdev.sheaveline.people.PeopleError;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.plant.PlantError;
import io.github.rkaushikdev.sheaveline.ropework.RopeworkError;
import io.github.rkaushikdev.sheaveline.schedule.SeasonError;
import io.github.rkaushikdev.sheaveline.upkeep.UpkeepError;
import io.github.rkaushikdev.sheaveline.weather.WeatherError;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * One error shape for the whole surface.
 *
 * <p>Every failure that reaches the http layer comes out of here and looks the same on the wire: a
 * kind, a sentence and, where there is one, the field that caused it. The client on the other end is
 * a tablet in a terminal being read by somebody with gloves on, and a body that changes shape
 * depending on which layer raised is a body nobody writes a screen against.
 *
 * <p>The mapping from exception to status code is a table rather than a chain, because it is read
 * far more often than it is changed and the question it answers is always which one of these is a
 * four hundred and which is a five hundred. A request the framework cannot read into what the route
 * wants, a body or a query value, is a 422 in the same shape.
 */
@RestControllerAdvice
public final class Errors {

    /** The kind every unreadable request is answered with. */
    public static final String UNREADABLE_KIND = "RequestValidationError";

    /** The sentence every unreadable request is answered with. */
    public static final String UNREADABLE = "the request does not read as what the route wants";

    /** The status each family of failure maps to. The most specific family an error is in wins. */
    public static final Map<Class<? extends Exception>, HttpStatus> STATUS_FOR = statuses();

    private static Map<Class<? extends Exception>, HttpStatus> statuses() {
        Map<Class<? extends Exception>, HttpStatus> status = new LinkedHashMap<>();
        status.put(ParseError.class, HttpStatus.BAD_REQUEST);
        status.put(RangeError.class, HttpStatus.BAD_REQUEST);
        status.put(MeasureError.class, HttpStatus.BAD_REQUEST);
        status.put(PlantError.class, HttpStatus.CONFLICT);
        status.put(SeasonError.class, HttpStatus.CONFLICT);
        status.put(RopeworkError.class, HttpStatus.CONFLICT);
        status.put(LineError.class, HttpStatus.CONFLICT);
        status.put(CapacityError.class, HttpStatus.CONFLICT);
        status.put(WeatherError.class, HttpStatus.CONFLICT);
        status.put(OperationsError.class, HttpStatus.CONFLICT);
        status.put(UpkeepError.class, HttpStatus.CONFLICT);
        status.put(EvacuationError.class, HttpStatus.CONFLICT);
        status.put(PeopleError.class, HttpStatus.CONFLICT);
        status.put(BoardError.class, HttpStatus.CONFLICT);
        status.put(StorageError.class, HttpStatus.NOT_FOUND);
        status.put(AuthError.class, HttpStatus.UNAUTHORIZED);
        return Collections.unmodifiableMap(status);
    }

    /**
     * The status for an error: the most specific entry in the table it is an instance of, or a 500
     * when it is in none.
     *
     * @param error the error
     * @return the status code
     */
    public static int statusFor(Throwable error) {
        if (error instanceof NotFound) {
            return HttpStatus.NOT_FOUND.value();
        }
        HttpStatus best = HttpStatus.INTERNAL_SERVER_ERROR;
        int depth = -1;
        for (Map.Entry<Class<? extends Exception>, HttpStatus> entry : STATUS_FOR.entrySet()) {
            if (entry.getKey().isInstance(error)) {
                int here = depthOf(entry.getKey());
                if (here > depth) {
                    depth = here;
                    best = entry.getValue();
                }
            }
        }
        return best.value();
    }

    /**
     * The body for an error: its kind, its sentence, and the field a measure was refused for.
     *
     * @param error the error
     * @return the body
     */
    public static ErrorBody bodyFor(Throwable error) {
        Optional<String> field = Optional.empty();
        if (error instanceof RangeError range) {
            field = Optional.of(range.what());
        } else if (error instanceof ParseError parse) {
            field = Optional.of(parse.wanted());
        }
        return new ErrorBody(error.getClass().getSimpleName(), Objects.toString(error.getMessage(), ""), field);
    }

    /**
     * Every domain failure leaves through here and nowhere else.
     *
     * @param error the failure
     * @return its status and body
     */
    @ExceptionHandler(SheavelineError.class)
    public ResponseEntity<ErrorBody> domain(SheavelineError error) {
        return ResponseEntity.status(statusFor(error)).body(bodyFor(error));
    }

    /**
     * A body that did not read: not JSON, not the shape, or a value that would not parse.
     *
     * @param error the refusal
     * @return a 422 naming where in the body it stopped
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException error) {
        return rejected(
                error.getCause() instanceof JsonMappingException mapping ? WirePath.of(mapping) : Optional.empty());
    }

    /**
     * A body or a set of query values that read and broke a rule of their shape.
     *
     * @param error the refusal
     * @return a 422 naming the first field, in the order the shape declares them
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException error) {
        BindingResult result = error.getBindingResult();
        return rejected(firstField(result.getFieldErrors(), result.getTarget()));
    }

    /**
     * A query or path value that broke a rule on the route.
     *
     * @param error the refusal
     * @return a 422 naming the first value refused, in the order the route takes them
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorBody> outOfBounds(HandlerMethodValidationException error) {
        return rejected(error.getParameterValidationResults().stream()
                .min(Comparator.comparingInt(
                        result -> result.getMethodParameter().getParameterIndex()))
                .map(Errors::fieldOf));
    }

    /**
     * A query or path value of the wrong kind.
     *
     * @param error the refusal
     * @return a 422 naming the value
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorBody> mismatched(MethodArgumentTypeMismatchException error) {
        return rejected(Optional.of(error.getName()));
    }

    /**
     * A query value the route needs and did not get.
     *
     * @param error the refusal
     * @return a 422 naming the value
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorBody> missing(MissingServletRequestParameterException error) {
        return rejected(Optional.of(error.getParameterName()));
    }

    /**
     * A value the service's own readers refused.
     *
     * @param error the refusal
     * @return a 422 naming the value
     */
    @ExceptionHandler(RequestRejected.class)
    public ResponseEntity<ErrorBody> refused(RequestRejected error) {
        return rejected(Optional.of(error.field()));
    }

    private static ResponseEntity<ErrorBody> rejected(Optional<String> field) {
        return ResponseEntity.unprocessableEntity().body(new ErrorBody(UNREADABLE_KIND, UNREADABLE, field));
    }

    private static Optional<String> firstField(List<FieldError> errors, Object target) {
        Comparator<String> order =
                target == null ? Comparator.naturalOrder() : WirePath.inDeclarationOrder(target.getClass());
        return errors.stream().map(FieldError::getField).min(order).map(WirePath::of);
    }

    private static String fieldOf(ParameterValidationResult result) {
        if (result instanceof ParameterErrors errors) {
            Optional<String> inside = firstField(errors.getFieldErrors(), errors.getArgument());
            if (inside.isPresent()) {
                return inside.get();
            }
        }
        return nameOf(result.getMethodParameter());
    }

    private static String nameOf(MethodParameter parameter) {
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null && !query.name().isEmpty()) {
            return query.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        return Objects.toString(parameter.getParameterName(), "");
    }

    private static int depthOf(Class<?> type) {
        int depth = 0;
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            depth++;
        }
        return depth;
    }
}
