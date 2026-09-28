package com.example.logicomposer.model;

import com.example.logicomposer.enums.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateConnectionRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String url,
        @NotNull ConnectionType type
) {}
