package com.example.logicomposer.dto;

import com.example.logicomposer.enums.ConnectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConnectionRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String url,
        @NotNull ConnectionType type
) {}
