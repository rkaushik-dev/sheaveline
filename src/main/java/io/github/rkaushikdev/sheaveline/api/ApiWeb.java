package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.api.schemas.WireText;
import io.github.rkaushikdev.sheaveline.util.Valued;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.format.FormatterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The service's additions to Spring MVC: the {@link Gate} in front of every route, the {@link Work}
 * a route asks for, the {@link Paging} a list route asks for, and the enumerations read from a query
 * string by the words the department writes them in.
 */
@Component
public class ApiWeb implements WebMvcConfigurer {

    private final Gate gate;
    private final Work work;
    private final Pages pages;

    /**
     * The additions for one running copy.
     *
     * @param settings its settings
     * @param validator the validator paging is checked with
     */
    public ApiWeb(Settings settings, Validator validator) {
        this.gate = new Gate(settings);
        this.work = new Work(settings);
        this.pages = new Pages(validator);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gate);
        registry.addInterceptor(work);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(work);
        resolvers.add(pages);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new ValuedText());
    }

    /** An enumeration from the words it is written as, such as {@code ?priority=immediate}. */
    private static final class ValuedText implements ConditionalGenericConverter {

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return Set.of(new ConvertiblePair(String.class, Valued.class));
        }

        @Override
        public boolean matches(TypeDescriptor source, TypeDescriptor target) {
            return target.getType().isEnum();
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }
            for (Object constant : targetType.getType().getEnumConstants()) {
                if (((Valued) constant).value().equals(source)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(WireText.expected(targetType.getType()));
        }
    }
}
