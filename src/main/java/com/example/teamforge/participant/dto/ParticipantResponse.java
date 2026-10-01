package com.example.teamforge.participant.dto;

import com.example.teamforge.participant.entity.Participant;

import java.util.UUID;

public record ParticipantResponse(UUID id, String name) {

    public static ParticipantResponse from(Participant participant) {
        return new ParticipantResponse(participant.getId(), participant.getName());
    }
}
