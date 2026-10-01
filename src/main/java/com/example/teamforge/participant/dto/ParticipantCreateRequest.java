package com.example.teamforge.participant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipantCreateRequest(
        @NotBlank @Size(max = 20) String name) {

    public ParticipantCreateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
