package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import java.util.List;

/**
 * What a rope's file says about it as it stands.
 *
 * @param serial the rope
 * @param verdict the worst any part of the file says
 * @param mayRun whether the rope may carry the public
 * @param sources the parts of the file that found something, sorted
 * @param findings what they found
 */
public record RopeFileOut(
        @RopeSerialText RopeSerial serial,
        String verdict,
        boolean mayRun,
        List<String> sources,
        List<FindingOut> findings) {

    /** A file's shape. */
    public RopeFileOut {
        sources = List.copyOf(sources);
        findings = List.copyOf(findings);
    }
}
