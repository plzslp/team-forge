package com.example.teamforge.participant.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ParticipantPageResponse(
        List<ParticipantResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ParticipantPageResponse from(Page<ParticipantResponse> result) {
        return new ParticipantPageResponse(List.copyOf(result.getContent()), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
