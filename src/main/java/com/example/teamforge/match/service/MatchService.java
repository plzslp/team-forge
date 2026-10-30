package com.example.teamforge.match.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.GameProfile;
import com.example.teamforge.gameprofile.repository.GameProfileRepository;
import com.example.teamforge.match.dto.request.MatchCreateRequest;
import com.example.teamforge.match.dto.response.MatchCreateResponse;
import com.example.teamforge.match.dto.request.MatchParticipantRequest;
import com.example.teamforge.match.dto.request.MatchListRequest;
import com.example.teamforge.match.dto.response.MatchPageResponse;
import com.example.teamforge.match.dto.response.MatchResponse;
import com.example.teamforge.match.dto.ParticipantVersions;
import com.example.teamforge.match.entity.Match;
import com.example.teamforge.match.entity.MatchParticipant;
import com.example.teamforge.match.repository.MatchRepository;
import com.example.teamforge.participant.entity.Participant;
import com.example.teamforge.participant.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final ParticipantRepository participantRepository;
    private final GameProfileRepository gameProfileRepository;
    private final MatchRepository matchRepository;

    /** 페이지 순서는 ID 조회 결과로 유지하고, 페이지의 참가자 스냅샷은 한 번에 로딩한다. */
    @Transactional(readOnly = true)
    public MatchPageResponse findAll(MatchListRequest request) {
        Integer page = request.getPage();
        Integer size = request.getSize();
        if (request.getGame() == null || page == null || size == null || page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Page<UUID> ids = matchRepository.findPageIdsByGame(request.getGame(), PageRequest.of(page, size));
        if (ids.isEmpty()) {
            return new MatchPageResponse(List.of(), page, size, ids.getTotalElements(), ids.getTotalPages());
        }
        Map<UUID, Match> matches = matchRepository.findAllWithParticipantsByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Match::getId, Function.identity()));
        return MatchPageResponse.from(ids.map(id -> MatchResponse.from(matches.get(id))));
    }

    @Transactional(readOnly = true)
    public MatchResponse findById(UUID id) {
        Match match = matchRepository.findWithParticipantsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return MatchResponse.from(match);
    }

    /**
     * 최종 편성을 현재 DB 정보로 검증하고 이름·점수·포지션·팀을 스냅샷으로 저장한다.
     * 참가자 → 프로필 순서로 잠금을 잡아 검증부터 트랜잭션 종료까지 수정·삭제와의 경쟁을 막는다.
     *
     * @param request 최종 편성과 미리보기 당시 버전, 비선호 배정 허용 여부
     * @return 생성한 경기 ID·시각과 확정 스냅샷
     * @throws BusinessException 입력·역할 구성이 잘못됐거나, 미리보기 정보가 오래됐거나, 비선호 배정이 미허용인 경우
     */
    @Transactional
    public MatchCreateResponse create(MatchCreateRequest request) {
        List<UUID> ids = validateRequest(request);

        // 삭제된 행도 잠근 후 활성 상태를 검사해 미리보기 재생성이 필요한 경우를 409로 통일한다.
        List<Participant> participants = participantRepository.findAllForMatchConfirmation(ids);
        if (participants.size() != 10 || participants.stream().anyMatch(participant -> !participant.active())) {
            throw new BusinessException(ErrorCode.PREVIEW_OUTDATED);
        }
        List<GameProfile> lockedProfiles = gameProfileRepository.findAllForMatchConfirmation(ids, request.game());
        if (lockedProfiles.size() != 10) throw new BusinessException(ErrorCode.PREVIEW_OUTDATED);

        // PostgreSQL에서는 outer join 대상까지 FOR UPDATE로 잠글 수 없으므로 행 잠금과 컬렉션 조회를 분리한다.
        // 이미 잠근 프로필의 포지션만 일괄 조회하며 잠금은 이 트랜잭션이 끝날 때까지 유지된다.
        Map<UUID, GameProfile> profilesByParticipant = gameProfileRepository
                .findAllWithPositionsByParticipantIdsAndGame(ids, request.game()).stream()
                .collect(Collectors.toMap(GameProfile::getParticipantId, Function.identity()));
        Map<UUID, Participant> participantsById = participants.stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));

        List<MatchParticipant> snapshots = new ArrayList<>();
        for (MatchParticipantRequest member : request.participants()) {
            Participant participant = participantsById.get(member.participantId());
            GameProfile profile = profilesByParticipant.get(member.participantId());
            ParticipantVersions expected = request.participantVersions().get(member.participantId());
            if (expected.participantVersion() != participant.getVersion()
                    || expected.profileVersion() != profile.getVersion()) {
                throw new BusinessException(ErrorCode.PREVIEW_OUTDATED);
            }
            snapshots.add(new MatchParticipant(participant.getId(), participant.getName(), profile.effectiveScore(),
                    member.assignedPosition(), member.team()));
        }

        // 자동 미리보기와 달리 수동 교체가 가능하므로 최종 역할 구성과 비선호 허용을 다시 확인한다.
        Match.validateRoleComposition(request.game(), snapshots);
        boolean hasNonPreferredAssignment = snapshots.stream().anyMatch(participant -> !profilesByParticipant
                .get(participant.getParticipantId()).getPreferredPositions().contains(participant.getAssignedPosition()));
        if (hasNonPreferredAssignment && !request.allowNonPreferredPositions()) {
            throw new BusinessException(ErrorCode.NON_PREFERRED_POSITION_NOT_ALLOWED);
        }

        Match match = new Match(UUID.randomUUID(), request.game(), Instant.now(), snapshots, null);
        return MatchCreateResponse.from(matchRepository.saveAndFlush(match));
    }

    private List<UUID> validateRequest(MatchCreateRequest request) {
        if (request.game() == null || request.participants() == null || request.participants().size() != 10
                || request.participants().stream().anyMatch(member -> member == null || member.participantId() == null
                || member.team() == null || member.assignedPosition() == null)) {
            throw new BusinessException(ErrorCode.INVALID_TEAM_COMPOSITION);
        }
        List<UUID> ids = request.participants().stream().map(MatchParticipantRequest::participantId).toList();
        Set<UUID> uniqueIds = Set.copyOf(ids);
        if (uniqueIds.size() != 10) throw new BusinessException(ErrorCode.INVALID_TEAM_COMPOSITION);
        if (request.participantVersions() == null || !request.participantVersions().keySet().equals(uniqueIds)
                || request.participantVersions().values().stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.PREVIEW_PARTICIPANTS_MISMATCH);
        }
        return ids;
    }
}
