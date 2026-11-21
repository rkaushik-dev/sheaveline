package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.ropework.Finding;

/**
 * One thing the rope file found, and what it says about the rope.
 *
 * @param source which part of the file found it
 * @param verdict what it says
 * @param detail what was found
 */
public record FindingOut(String source, String verdict, String detail) {

    /**
     * The finding as the tablet shows it.
     *
     * @param finding the finding
     * @return its shape
     */
    public static FindingOut of(Finding finding) {
        return new FindingOut(finding.source(), finding.verdict().value(), finding.detail());
    }
}
