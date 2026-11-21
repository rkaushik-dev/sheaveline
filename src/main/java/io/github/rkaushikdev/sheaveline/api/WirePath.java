package io.github.rkaushikdev.sheaveline.api;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Where in a request body something went wrong, written the way the client wrote the body: field
 * names as they appear on the wire, list positions as numbers, joined by dots, such as {@code
 * diameters.0.across_crowns}.
 */
final class WirePath {

    private static final PropertyNamingStrategies.NamingBase WIRE_NAMES =
            (PropertyNamingStrategies.NamingBase) PropertyNamingStrategies.SNAKE_CASE;

    private static final Pattern POSITION = Pattern.compile("\\d+");

    private WirePath() {}

    /**
     * Where the JSON reader stopped.
     *
     * @param refusal what it stopped with
     * @return the path, or empty when it stopped before reading any field
     */
    static Optional<String> of(JsonMappingException refusal) {
        String path = refusal.getPath().stream()
                .map(step -> step.getFieldName() != null ? step.getFieldName() : String.valueOf(step.getIndex()))
                .collect(Collectors.joining("."));
        return path.isEmpty() ? Optional.empty() : Optional.of(path);
    }

    /**
     * A bean path as the wire spells it: {@code diameters[0].acrossCrowns} is {@code
     * diameters.0.across_crowns}.
     *
     * @param beanPath the path through the Java objects
     * @return the path through the JSON
     */
    static String of(String beanPath) {
        return steps(beanPath).stream()
                .map(step -> POSITION.matcher(step).matches() ? step : WIRE_NAMES.translate(step))
                .collect(Collectors.joining("."));
    }

    /**
     * Bean paths in the order their fields are declared, which is the order the client reads them
     * on the form, list positions in order within a list.
     *
     * @param root the shape the paths start from
     * @return the ordering
     */
    static Comparator<String> inDeclarationOrder(Class<?> root) {
        return (left, right) -> compare(rank(root, left), rank(root, right));
    }

    private static List<String> steps(String beanPath) {
        List<String> steps = new ArrayList<>();
        for (String part : beanPath.replaceAll("\\[(\\d+)]", ".$1").split("\\.")) {
            if (!part.isEmpty()) {
                steps.add(part);
            }
        }
        return steps;
    }

    private static List<Integer> rank(Class<?> root, String beanPath) {
        List<Integer> rank = new ArrayList<>();
        Type at = root;
        for (String step : steps(beanPath)) {
            if (POSITION.matcher(step).matches()) {
                rank.add(Integer.parseInt(step));
                at = contentOf(at);
                continue;
            }
            int position = Integer.MAX_VALUE;
            Type next = Object.class;
            if (at instanceof Class<?> type && type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                for (int index = 0; index < components.length; index++) {
                    if (components[index].getName().equals(step)) {
                        position = index;
                        next = components[index].getGenericType();
                    }
                }
            }
            rank.add(position);
            at = next instanceof ParameterizedType wrapped && wrapped.getRawType() == Optional.class
                    ? contentOf(wrapped)
                    : next;
        }
        return rank;
    }

    private static Type contentOf(Type container) {
        if (container instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            return arguments[arguments.length - 1];
        }
        return Object.class;
    }

    private static int compare(List<Integer> left, List<Integer> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int order = Integer.compare(left.get(index), right.get(index));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
