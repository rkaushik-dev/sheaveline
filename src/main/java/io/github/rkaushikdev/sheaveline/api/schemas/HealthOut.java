package io.github.rkaushikdev.sheaveline.api.schemas;

/**
 * The cheap answer: the process is up. Nothing in it reads a row.
 *
 * @param service what is answering
 * @param version its version
 * @param resort the resort it answers for
 */
public record HealthOut(String service, String version, String resort) {}
