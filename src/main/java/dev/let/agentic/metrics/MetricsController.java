package dev.let.agentic.metrics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

    private final MetricsService service;

    public MetricsController(MetricsService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public MetricsService.Metrics metrics() {
        return service.snapshot();
    }
}
