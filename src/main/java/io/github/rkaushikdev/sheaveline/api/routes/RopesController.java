package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Paged;
import io.github.rkaushikdev.sheaveline.api.Paging;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.schemas.FindingOut;
import io.github.rkaushikdev.sheaveline.api.schemas.RopeFileOut;
import io.github.rkaushikdev.sheaveline.api.schemas.RopeOut;
import io.github.rkaushikdev.sheaveline.api.schemas.WearSurveyIn;
import io.github.rkaushikdev.sheaveline.api.schemas.WearSurveyOut;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.RopeFile;
import io.github.rkaushikdev.sheaveline.ropework.Verdict;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rope file, read and written over http.
 *
 * <p>The write here is a mechanic recording a walk, which is the one thing this surface is
 * genuinely used for rather than consulted about. It takes a mechanic's key, and the verdict comes
 * back in the same reply, because standing next to the rope is exactly when somebody wants to know
 * whether it stays on.
 */
@RestController
@RequestMapping("/ropes")
@Tag(name = "ropes")
public class RopesController {

    /**
     * Every rope on file, on a machine or on the shelf.
     *
     * @param work the request's unit of work
     * @return the ropes
     */
    @GetMapping
    @Requires(Role.READER)
    public List<RopeOut> listRopes(UnitOfWork work) {
        return work.ropes().all().stream().map(RopeOut::of).toList();
    }

    /**
     * The ropes with no machine under them.
     *
     * @param work the request's unit of work
     * @return the spares
     */
    @GetMapping("/spares")
    @Requires(Role.READER)
    public List<RopeOut> listSpares(UnitOfWork work) {
        return work.ropes().spares().stream().map(RopeOut::of).toList();
    }

    /**
     * One rope.
     *
     * @param serial its serial
     * @param look the request's lookup
     * @return the rope
     */
    @GetMapping("/{serial}")
    @Requires(Role.READER)
    public RopeOut readRope(@PathVariable String serial, Lookup look) {
        return RopeOut.of(look.rope(serial));
    }

    /**
     * The walks on a rope, oldest first. Paged, because a rope is walked monthly and lives eight
     * years.
     *
     * @param serial its serial
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/{serial}/surveys")
    @Requires(Role.READER)
    public Paged<WearSurveyOut> listSurveys(
            @PathVariable String serial, UnitOfWork work, Lookup look, @ParameterObject Paging paging) {
        HaulRope rope = look.rope(serial);
        return Paged.of(
                work.wearSurveys().forRope(rope).stream()
                        .map(survey -> WearSurveyOut.of(
                                survey, Verdict.assessWear(survey).value()))
                        .toList(),
                paging);
    }

    /**
     * Record a walk, and hear at once what it says about the rope.
     *
     * @param serial the rope walked
     * @param body the walk
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the walk with its verdict
     */
    @PostMapping("/{serial}/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.MECHANIC)
    public WearSurveyOut recordSurvey(
            @PathVariable String serial, @Valid @RequestBody WearSurveyIn body, UnitOfWork work, Lookup look) {
        HaulRope rope = look.rope(serial);
        WearSurvey survey = body.toDomain(rope);
        work.wearSurveys().add(survey);
        work.commit();
        return WearSurveyOut.of(survey, Verdict.assessWear(survey).value());
    }

    /**
     * What the rope's file says about it, read off the latest walk and the latest broken wire count.
     *
     * @param serial its serial
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the verdict and what it rests on
     */
    @GetMapping("/{serial}/file")
    @Requires(Role.READER)
    public RopeFileOut readFile(@PathVariable String serial, UnitOfWork work, Lookup look) {
        HaulRope rope = look.rope(serial);
        RopeFile held = new RopeFile();
        List<WearSurvey> surveys = work.wearSurveys().forRope(rope);
        if (!surveys.isEmpty()) {
            held = held.withWear(surveys.getLast());
        }
        List<BreakSurvey> counts = work.breakSurveys().forRope(rope);
        if (!counts.isEmpty()) {
            held = held.withBreaks(counts.getLast());
        }
        return new RopeFileOut(
                rope.serial(),
                held.verdict().value(),
                held.mayRun(),
                held.sources().stream().sorted().toList(),
                held.findings().stream().map(FindingOut::of).toList());
    }
}
