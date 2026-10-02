package com.example.teamforge.participant.controller;

import com.example.teamforge.participant.service.ParticipantService;
import com.example.teamforge.participant.dto.ParticipantCreateRequest;
import com.example.teamforge.participant.dto.ParticipantPageRequest;
import com.example.teamforge.participant.dto.ParticipantResponse;
import com.example.teamforge.participant.dto.ParticipantPageResponse;
import com.example.teamforge.participant.dto.ParticipantUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/participants")
@RequiredArgsConstructor
public class ParticipantController extends ParticipantApi {

    private final ParticipantService participantService;

    @Override
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ParticipantResponse> register(@Valid @RequestBody ParticipantCreateRequest request) {
        ParticipantResponse participant = participantService.register(request.name());
        return ResponseEntity.created(URI.create("/api/participants/" + participant.id())).body(participant);
    }

    @Override
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ParticipantPageResponse> findAll(@Valid @ModelAttribute ParticipantPageRequest request) {
        return ResponseEntity.ok(participantService.findAll(request.getPage(), request.getSize()));
    }

    @Override
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ParticipantResponse> findById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(participantService.findById(id));
    }

    @Override
    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ParticipantResponse> update(@PathVariable("id") UUID id,
                                                     @Valid @RequestBody ParticipantUpdateRequest request) {
        return ResponseEntity.ok(participantService.update(id, request.name()));
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) {
        participantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
