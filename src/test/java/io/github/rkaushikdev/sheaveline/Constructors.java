package io.github.rkaushikdev.sheaveline;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/**
 * Checks on what a type's constructors accept, for the rules the compiler now enforces.
 *
 * <p>A measure built from a fraction or a truth value is refused before the program runs, because
 * its constructors take only whole numbers. These checks pin that, so a constructor taking a double
 * cannot be added without a test noticing.
 */
public final class Constructors {

    private Constructors() {}

    /**
     * Assert every public constructor of the type takes exactly these parameter types, and that
     * none of them takes a floating point number or a boolean.
     *
     * @param type the type
     * @param parameters the only constructor signature allowed
     */
    public static void takeOnly(Class<?> type, Class<?>... parameters) {
        List<List<Class<?>>> signatures = Arrays.stream(type.getConstructors())
                .map(Constructor::getParameterTypes)
                .map(List::of)
                .toList();
        assertThat(signatures).containsExactly(List.of(parameters));
        assertThat(signatures.stream().flatMap(List::stream))
                .doesNotContain(double.class, float.class, boolean.class, Double.class, Float.class, Boolean.class);
    }
}
