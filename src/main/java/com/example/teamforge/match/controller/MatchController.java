package com.example.teamforge.match.controller;

import com.example.teamforge.match.dto.request.MatchCreateRequest;
import com.example.teamforge.match.dto.response.MatchCreateResponse;
import com.example.teamforge.match.dto.request.MatchListRequest;
import com.example.teamforge.match.dto.response.MatchPageResponse;
import com.example.teamforge.match.dto.response.MatchResponse;
import com.example.teamforge.match.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController extends MatchApi {
    private final MatchService matchService;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchCreateResponse> create(@Valid @RequestBody MatchCreateRequest request) {
        MatchCreateResponse response = matchService.create(request);
        return ResponseEntity.created(URI.create("/api/matches/" + response.id())).body(response);
    }

    @Override
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchPageResponse> findAll(@Valid @ModelAttribute MatchListRequest request) {
        return ResponseEntity.ok(matchService.findAll(request));
    }

    @Override
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MatchResponse> findById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(matchService.findById(id));
    }
}
