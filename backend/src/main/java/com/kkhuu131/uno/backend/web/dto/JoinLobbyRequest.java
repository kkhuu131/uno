package com.kkhuu131.uno.backend.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinLobbyRequest(
    @NotBlank @Size(min = 1, max = 24) String displayName
) {}
