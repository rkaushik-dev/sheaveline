package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.Sheaveline;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The http surface the terminal tablets and the office talk to.
 *
 * <p>Started through a factory, {@link #run}, rather than a context that builds its own database,
 * because the tests want a fresh database behind a fresh application on every run, and because the
 * office server and the laptop hand it different databases. The {@link Settings} are handed in and
 * become the one shared thing; every route reads them, and nothing here reaches for a global.
 *
 * <p>Spring Boot's own data source, JPA and migration set up are switched off: with H2 on the class
 * path and no data source of its own, Boot would otherwise build an embedded database and migrate
 * that. The service does not build the schema either; the command line does.
 */
@SpringBootApplication(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public class SheavelineApplication {

    /** What the service calls itself in its document. */
    public static final String TITLE = "sheaveline";

    /** The version it reports. */
    public static final String VERSION = Sheaveline.VERSION;

    /** What it is for, in a line. */
    public static final String SUMMARY = "Lift department records for a passenger ropeway operation";

    static {
        // A route's unit of work and lookup come from the request, not from the caller, so the
        // document does not list them as parameters.
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UnitOfWork.class, Lookup.class);
    }

    /**
     * Start the service.
     *
     * @param settings what this running copy needs to know
     * @param args Spring arguments, such as {@code --server.port=0}
     * @return the running application, which the caller closes
     */
    public static ConfigurableApplicationContext run(Settings settings, String... args) {
        return run(settings, List.of(), args);
    }

    /**
     * Start the service with more configuration beside its own: the routes a test adds, for
     * instance.
     *
     * @param settings what this running copy needs to know
     * @param extra further configuration or controller classes
     * @param args Spring arguments, such as {@code --server.port=0}
     * @return the running application, which the caller closes
     */
    public static ConfigurableApplicationContext run(Settings settings, List<Class<?>> extra, String... args) {
        List<Class<?>> sources = new ArrayList<>();
        sources.add(SheavelineApplication.class);
        sources.addAll(extra);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .initializers(context -> context.getBeanFactory().registerSingleton("settings", settings))
                .build()
                .run(args);
    }

    /**
     * The settings a running application was started with.
     *
     * @param application the application
     * @return its settings
     * @throws IllegalStateException when the application was built without settings
     */
    public static Settings settingsOf(ApplicationContext application) {
        try {
            return application.getBean(Settings.class);
        } catch (NoSuchBeanDefinitionException missing) {
            throw new IllegalStateException("this application was built without settings", missing);
        }
    }

    /**
     * Do some work in one unit of work on an application's database, opened before and closed
     * after whatever happened, the way a request is served.
     *
     * @param application the application
     * @param work what to do in it; it commits for itself
     */
    public static void unitOfWork(ApplicationContext application, Consumer<UnitOfWork> work) {
        try (UnitOfWork unit = UnitOfWork.on(settingsOf(application).database()).open()) {
            work.accept(unit);
        }
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer wireJson() {
        return Json::configure;
    }

    @Bean
    ModelResolver wireSchemas() {
        return Json.modelResolver();
    }

    @Bean
    OpenAPI document() {
        return new OpenAPI().info(new Info().title(TITLE).version(VERSION).summary(SUMMARY));
    }

    /** Every route that needs a key says so in the document, under the header it is read from. */
    @Bean
    OperationCustomizer keyHeader() {
        return (operation, handler) -> {
            Requires needs = handler.getMethodAnnotation(Requires.class);
            if (needs != null) {
                operation.addParametersItem(new HeaderParameter()
                        .name(Gate.API_KEY_HEADER)
                        .required(false)
                        .description("An id and a secret separated by a dot, on a key with the "
                                + needs.value().value() + " role or above.")
                        .schema(new StringSchema()));
            }
            return operation;
        };
    }
}
