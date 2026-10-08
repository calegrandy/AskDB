package io.github.calegrandy.askdb.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String question) {}
