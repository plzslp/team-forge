package com.example.teamforge.match.controller;

import com.example.teamforge.match.dto.MatchCreateRequest;
import com.example.teamforge.match.dto.MatchCreateResponse;
import com.example.teamforge.match.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController extends MatchApi {
    private final MatchService matchService;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchCreateResponse> create(@Valid @RequestBody MatchCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(matchService.create(request));
    }
}
