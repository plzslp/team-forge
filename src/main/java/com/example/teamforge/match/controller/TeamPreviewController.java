package com.example.teamforge.match.controller;

import com.example.teamforge.match.dto.request.TeamPreviewRequest;
import com.example.teamforge.match.dto.response.TeamPreviewResponse;
import com.example.teamforge.match.service.TeamPreviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/team-previews")
@RequiredArgsConstructor
public class TeamPreviewController extends TeamPreviewApi {
    private final TeamPreviewService teamPreviewService;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TeamPreviewResponse> create(@Valid @RequestBody TeamPreviewRequest request) {
        return ResponseEntity.ok(teamPreviewService.create(request));
    }
}
