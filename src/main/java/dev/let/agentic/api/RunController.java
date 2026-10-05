package dev.let.agentic.api;

import dev.let.agentic.api.RunViews.AuditView;
import dev.let.agentic.api.RunViews.RunSummary;
import dev.let.agentic.api.RunViews.RunView;
import dev.let.agentic.domain.Run;
import dev.let.agentic.domain.StageId;
import dev.let.agentic.engine.Orchestrator;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/runs")
public class RunController {

    private static final String DEFAULT_ACTOR = "human";

    private final Orchestrator orchestrator;

    public RunController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** Starts a run. It executes until it finishes or reaches a human approval gate. */
    @PostMapping
    public ResponseEntity<RunView> start(@Valid @RequestBody StartRunRequest request) {
        Run run = orchestrator.start(request.requirement(), request.typeOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(RunView.from(run));
    }

    @GetMapping
    public List<RunSummary> list() {
        return orchestrator.allRuns().stream().map(RunSummary::from).toList();
    }

    @GetMapping("/{id}")
    public RunView get(@PathVariable UUID id) {
        return RunView.from(find(id));
    }

    @PostMapping("/{id}/stages/{stage}/approve")
    public RunView approve(@PathVariable UUID id, @PathVariable StageId stage,
                           @Valid @RequestBody(required = false) DecisionRequest body) {
        return RunView.from(orchestrator.approve(id, stage, actor(body), note(body)));
    }

    @PostMapping("/{id}/stages/{stage}/reject")
    public RunView reject(@PathVariable UUID id, @PathVariable StageId stage,
                          @Valid @RequestBody(required = false) DecisionRequest body) {
        return RunView.from(orchestrator.reject(id, stage, actor(body), note(body)));
    }

    /** Safe-stop: nothing new starts, work in flight finishes cleanly. */
    @PostMapping("/{id}/stop")
    public RunView stop(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest body) {
        return RunView.from(orchestrator.stop(id, actor(body)));
    }

    @GetMapping("/{id}/audit")
    public List<AuditView> audit(@PathVariable UUID id) {
        return find(id).getAuditLog().stream().map(AuditView::from).toList();
    }

    private Run find(UUID id) {
        return orchestrator.find(id).orElseThrow(() -> new NoSuchElementException("Run not found: " + id));
    }

    private static String actor(DecisionRequest body) {
        return body == null || body.actor() == null || body.actor().isBlank() ? DEFAULT_ACTOR : body.actor();
    }

    private static String note(DecisionRequest body) {
        return body == null ? null : body.note();
    }
}
