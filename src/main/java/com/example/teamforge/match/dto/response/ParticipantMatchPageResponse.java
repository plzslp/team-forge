package com.example.teamforge.match.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record ParticipantMatchPageResponse(List<ParticipantMatchResponse> content, int page, int size,
                                           long totalElements, int totalPages) {
    public static ParticipantMatchPageResponse from(Page<ParticipantMatchResponse> page) {
        return new ParticipantMatchPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
