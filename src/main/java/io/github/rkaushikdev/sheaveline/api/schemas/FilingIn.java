package io.github.rkaushikdev.sheaveline.api.schemas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * What a manager says about a filing that went out.
 *
 * @param filed when it went out
 * @param filedBy who sent it
 */
public record FilingIn(@NotNull LocalDateTime filed, @NotNull @Size(min = 1) String filedBy) {}
