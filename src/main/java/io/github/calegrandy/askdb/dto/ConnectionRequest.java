package io.github.calegrandy.askdb.dto;

import io.github.calegrandy.askdb.enums.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectionRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String url,
        @NotNull ConnectionType type
) {}
