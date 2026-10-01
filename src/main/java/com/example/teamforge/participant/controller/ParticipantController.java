package com.example.teamforge.participant.controller;

import com.example.teamforge.participant.service.ParticipantService;
import com.example.teamforge.participant.dto.ParticipantCreateRequest;
import com.example.teamforge.participant.dto.ParticipantResponse;
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
    public List<ParticipantResponse> findAll() {
        return participantService.findAll();
    }

    @Override
    public ParticipantResponse findById(UUID id) {
        return participantService.findById(id);
    }
}
