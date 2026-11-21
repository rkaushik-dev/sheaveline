package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.line.Finding;

/**
 * One thing the line walk found. The rope file's findings have a shape of their own, {@link
 * FindingOut}.
 *
 * @param where where on the line
 * @param severity how much it matters
 * @param detail what was found
 */
public record LineFindingOut(String where, String severity, String detail) {

    /**
     * A finding as the tablet shows it.
     *
     * @param finding the finding
     * @return its shape
     */
    public static LineFindingOut of(Finding finding) {
        return new LineFindingOut(finding.where(), finding.severity().value(), finding.detail());
    }
}
