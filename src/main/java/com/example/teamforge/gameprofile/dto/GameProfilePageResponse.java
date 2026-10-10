package com.example.teamforge.gameprofile.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record GameProfilePageResponse(List<GameProfileListResponse> content, int page, int size,
                                      long totalElements, int totalPages) {
    public static GameProfilePageResponse from(Page<GameProfileListResponse> result) {
        return new GameProfilePageResponse(List.copyOf(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
