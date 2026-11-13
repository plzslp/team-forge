package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.gameprofile.dto.response.GameProfileResponse;
import com.example.teamforge.gameprofile.service.RiotProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/participants/{participantId}/profiles/LOL/refresh")
@RequiredArgsConstructor
public class RiotProfileController extends RiotProfileApi {
    private final RiotProfileService service;

    @Override
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameProfileResponse> refresh(@PathVariable("participantId") UUID participantId) {
        return ResponseEntity.ok(service.refresh(participantId));
    }
}
