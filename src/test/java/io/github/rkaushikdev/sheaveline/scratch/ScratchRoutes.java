package io.github.rkaushikdev.sheaveline.scratch;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.PlantError;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes a test adds beside the service's own, to see what the service does with a failure raised
 * in a route. Kept outside the service's packages so nothing picks them up unless a test names them.
 */
@RestController
public class ScratchRoutes {

    @GetMapping("/scratch/refuse")
    public Map<String, String> refuse() {
        throw new PlantError("a quad does not take a six place");
    }

    @GetMapping("/scratch/measure")
    public Map<String, String> measure() {
        Length.parse("nine");
        return Map.of();
    }

    @GetMapping("/scratch/needs")
    public Map<String, Integer> needs(@RequestParam int count) {
        return Map.of("count", count);
    }
}
