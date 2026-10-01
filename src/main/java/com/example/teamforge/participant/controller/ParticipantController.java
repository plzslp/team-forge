package com.example.teamforge.participant.controller;

import com.example.teamforge.participant.service.ParticipantService;
import com.example.teamforge.participant.dto.ParticipantCreateRequest;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.dto.ParticipantUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
public class ParticipantController extends ParticipantApi {

    private final ParticipantService participantService;

    @Override
    public ResponseEntity<ParticipantResponse> register(ParticipantCreateRequest request) {
        ParticipantResponse participant = participantService.register(request.name());
        return ResponseEntity.created(URI.create("/api/participants/" + participant.id())).body(participant);
    }

    @Override
    public ResponseEntity<List<ParticipantResponse>> findAll() {
        return ResponseEntity.ok(participantService.findAll());
    }

    @Override
    public ResponseEntity<ParticipantResponse> findById(UUID id) {
        return ResponseEntity.ok(participantService.findById(id));
    }

    @Override
    public ResponseEntity<ParticipantResponse> update(UUID id, ParticipantUpdateRequest request) {
        return ResponseEntity.ok(participantService.update(id, request.name()));
    }

    @Override
    public ResponseEntity<Void> delete(UUID id) {
        participantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
