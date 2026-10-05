package dev.let.agentic.api;

import dev.let.agentic.domain.RunType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartRunRequest(@NotBlank @Size(max = 2000) String requirement, RunType type) {

    public RunType typeOrDefault() {
        return type == null ? RunType.GREENFIELD : type;
    }
}
