package dev.let.agentic.api;

import jakarta.validation.constraints.Size;

public record DecisionRequest(@Size(max = 200) String actor, @Size(max = 2000) String note) {
}
