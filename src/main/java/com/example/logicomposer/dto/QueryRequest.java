package com.example.logicomposer.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String question) {}
