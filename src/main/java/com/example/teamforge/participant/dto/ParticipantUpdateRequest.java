package com.example.teamforge.participant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ParticipantUpdateRequest(
        @NotBlank @Size(max = 20) String name) {

    public ParticipantUpdateRequest {
        if (name != null) {
            name = name.strip();
        }
    }
}
