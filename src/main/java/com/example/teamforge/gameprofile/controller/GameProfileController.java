package com.example.teamforge.gameprofile.controller;

import com.example.teamforge.gameprofile.dto.GameProfileRequest;
import com.example.teamforge.gameprofile.dto.GameProfileResponse;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.service.GameProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/participants/{participantId}/profiles/{game}")
@RequiredArgsConstructor
public class GameProfileController extends GameProfileApi {
    private final GameProfileService gameProfileService;

    @Override
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameProfileResponse> upsert(@PathVariable("participantId") UUID participantId,
                                                     @PathVariable("game") Game game,
                                                     @Valid @RequestBody GameProfileRequest request) {
        GameProfileService.UpsertResult result = gameProfileService.upsert(participantId, game, request);
        if (result.created()) {
            return ResponseEntity.created(URI.create("/api/participants/" + participantId + "/profiles/" + game))
                    .body(result.profile());
        }
        return ResponseEntity.ok(result.profile());
    }

    @Override
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GameProfileResponse> findByParticipantIdAndGame(
            @PathVariable("participantId") UUID participantId, @PathVariable("game") Game game) {
        return ResponseEntity.ok(gameProfileService.findByParticipantIdAndGame(participantId, game));
    }
}
