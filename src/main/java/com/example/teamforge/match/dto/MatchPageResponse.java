package com.example.teamforge.match.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record MatchPageResponse(List<MatchResponse> content, int page, int size, long totalElements, int totalPages) {
    public static MatchPageResponse from(Page<MatchResponse> result) {
        return new MatchPageResponse(List.copyOf(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
