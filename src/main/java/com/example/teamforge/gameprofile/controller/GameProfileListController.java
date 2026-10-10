package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.gameprofile.dto.GameProfileListRequest;
import com.example.teamforge.gameprofile.dto.GameProfilePageResponse;
import com.example.teamforge.gameprofile.service.GameProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game-profiles")
@RequiredArgsConstructor
public class GameProfileListController extends GameProfileListApi {
    private final GameProfileService gameProfileService;

    @Override
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameProfilePageResponse> findAll(@Valid @ModelAttribute GameProfileListRequest request) {
        return ResponseEntity.ok(gameProfileService.findAll(request));
    }
}
