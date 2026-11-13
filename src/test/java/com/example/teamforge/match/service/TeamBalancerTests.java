package com.example.teamforge.match.service;

import com.example.teamforge.common.exception.BusinessException;
import com.example.teamforge.common.exception.ErrorCode;
import com.example.teamforge.gameprofile.entity.Game;
import com.example.teamforge.gameprofile.entity.Position;
import com.example.teamforge.match.dto.PreviewCandidate;
import com.example.teamforge.match.entity.MatchParticipant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TeamBalancerTests {
    private final TeamBalancer balancer = new TeamBalancer();

    @ParameterizedTest
    @EnumSource(Game.class)
    void matchesIndependentExhaustiveRoleAssignmentForMixedPreferences(Game game) {
        // given: 고정 시드로 복수·단일 선호 포지션과 점수를 섞는다.
        Random random = new Random(20261010);
        List<Position> roles = java.util.Arrays.stream(Position.values()).filter(position -> position.supports(game)).toList();
        List<Position> slots = game == Game.LOL ? roles : List.of(Position.OVERWATCH_TANK,
                Position.OVERWATCH_DAMAGE, Position.OVERWATCH_DAMAGE, Position.OVERWATCH_SUPPORT, Position.OVERWATCH_SUPPORT);
        List<PreviewCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Set<Position> preferred = new java.util.HashSet<>();
            preferred.add(roles.get(random.nextInt(roles.size())));
            preferred.add(roles.get(random.nextInt(roles.size())));
            candidates.add(candidate(index, random.nextInt(1000), preferred));
        }
        // given: DP를 사용하지 않는 완전 순열 탐색으로 비교할 기준값도 준비한다.
        int expectedViolations = Integer.MAX_VALUE;
        long expectedDifference = Long.MAX_VALUE;
        for (int mask = 0; mask < 1024; mask++) {
            if (Integer.bitCount(mask) != 5) continue;
            List<PreviewCandidate> a = new ArrayList<>();
            List<PreviewCandidate> b = new ArrayList<>();
            for (int index = 0; index < 10; index++) {
                ((mask & (1 << index)) == 0 ? a : b).add(candidates.get(index));
            }
            int cost = exhaustiveRoleCost(a, slots, new boolean[5], 0) + exhaustiveRoleCost(b, slots, new boolean[5], 0);
            long difference = Math.abs(a.stream().mapToLong(PreviewCandidate::score).sum()
                    - b.stream().mapToLong(PreviewCandidate::score).sum());
            if (cost < expectedViolations || (cost == expectedViolations && difference < expectedDifference)) {
                expectedViolations = cost;
                expectedDifference = difference;
            }
        }
        // when
        List<MatchParticipant> result = balancer.balance(game, candidates, true);
        // then
        assertEquals(expectedViolations, violations(candidates, result));
        assertEquals(expectedDifference, difference(result));
    }

    private int exhaustiveRoleCost(List<PreviewCandidate> candidates, List<Position> slots, boolean[] used, int slot) {
        if (slot == 5) return 0;
        int best = Integer.MAX_VALUE;
        for (int index = 0; index < 5; index++) {
            if (used[index]) continue;
            used[index] = true;
            int cost = candidates.get(index).preferredPositions().contains(slots.get(slot)) ? 0 : 1;
            best = Math.min(best, cost + exhaustiveRoleCost(candidates, slots, used, slot + 1));
            used[index] = false;
        }
        return best;
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void findsGloballyMinimumScoreDifferenceWithAllRolesPreferred(Game game) {
        // given: 모든 포지션을 선호하는 참가자와 고르지 않은 점수.
        int[] scores = {12, 87, 34, 61, 120, 5, 44, 99, 13, 73};
        List<PreviewCandidate> candidates = candidates(game);
        List<PreviewCandidate> scored = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            PreviewCandidate candidate = candidates.get(index);
            scored.add(new PreviewCandidate(candidate.participantId(), candidate.name(), scores[index], candidate.preferredPositions()));
        }
        // when
        List<MatchParticipant> result = balancer.balance(game, scored, false);
        // then: 독립적으로 계산한 모든 5인 점수 합의 최솟값과 일치한다.
        long total = scored.stream().mapToLong(PreviewCandidate::score).sum();
        long expected = Long.MAX_VALUE;
        for (int mask = 0; mask < 1024; mask++) {
            if (Integer.bitCount(mask) != 5) continue;
            long sum = 0;
            for (int index = 0; index < 10; index++) {
                if ((mask & (1 << index)) != 0) sum += scores[index];
            }
            expected = Math.min(expected, Math.abs(2 * sum - total));
        }
        assertEquals(expected, difference(result));
        assertTeamRoles(game, result);
        assertEquals(10, result.stream().map(MatchParticipant::getParticipantId).distinct().count());
    }

    @Test
    void prioritizesPreferredPositionsOverPerfectScoreBalanceEvenWhenFallbackAllowed() {
        // given: 탑 2명은 각각 100점, 서포터는 0·200점이고 나머지는 0점이다.
        List<PreviewCandidate> candidates = new ArrayList<>();
        Position[] positions = {Position.LOL_TOP, Position.LOL_JUNGLE, Position.LOL_MID,
                Position.LOL_BOTTOM, Position.LOL_SUPPORT};
        for (int index = 0; index < 10; index++) {
            int score = index < 2 ? 100 : index == 9 ? 200 : 0;
            candidates.add(candidate(index, score, Set.of(positions[index / 2])));
        }
        // when: 비선호를 허용하더라도 선호 충족이 우선이다.
        List<MatchParticipant> result = balancer.balance(Game.LOL, candidates, true);
        // then: 점수 차이 0인 비선호 편성 대신 차이 200인 선호 편성을 선택한다.
        assertEquals(0, violations(candidates, result));
        assertEquals(200, difference(result));
    }

    @Test
    void assignsFlexiblePlayersWithoutTakingTheOnlyRoleOfOtherPlayers() {
        // given: 팀마다 탑 전용과 탑·정글 겸임 참가자가 필요하다.
        List<PreviewCandidate> candidates = new ArrayList<>();
        for (int team = 0; team < 2; team++) {
            candidates.add(candidate(team * 5, 0, Set.of(Position.LOL_TOP, Position.LOL_JUNGLE)));
            candidates.add(candidate(team * 5 + 1, 0, Set.of(Position.LOL_TOP)));
            candidates.add(candidate(team * 5 + 2, 0, Set.of(Position.LOL_MID)));
            candidates.add(candidate(team * 5 + 3, 0, Set.of(Position.LOL_BOTTOM)));
            candidates.add(candidate(team * 5 + 4, 0, Set.of(Position.LOL_SUPPORT)));
        }
        // when
        List<MatchParticipant> result = balancer.balance(Game.LOL, candidates, false);
        // then
        assertEquals(0, violations(candidates, result));
        assertTeamRoles(Game.LOL, result);
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void refusesImpossiblePreferredPositionsUnlessExplicitlyAllowed(Game game) {
        // given: 한 포지션만 선호하는 10명.
        List<PreviewCandidate> candidates = singleRoleCandidates(game);
        // when / then
        BusinessException error = assertThrows(BusinessException.class, () -> balancer.balance(game, candidates, false));
        assertEquals(ErrorCode.PREFERRED_POSITION_UNAVAILABLE, error.getErrorCode());
    }

    @ParameterizedTest
    @EnumSource(Game.class)
    void fallbackMinimizesViolationsAndThenScoreDifference(Game game) {
        // given: 탑 또는 탱커만 선호하면 각 팀 4명씩 비선호 배정이 필요하다.
        List<PreviewCandidate> candidates = singleRoleCandidates(game);
        // when
        List<MatchParticipant> result = balancer.balance(game, candidates, true);
        // then
        assertEquals(8, violations(candidates, result));
        assertEquals(1, difference(result));
        assertTeamRoles(game, result);
    }

    @Test
    void resultDoesNotDependOnRequestOrder() {
        // given
        List<PreviewCandidate> candidates = candidates(Game.LOL);
        List<PreviewCandidate> reversed = new ArrayList<>(candidates);
        Collections.reverse(reversed);
        // when
        List<MatchParticipant> first = balancer.balance(Game.LOL, candidates, false);
        List<MatchParticipant> second = balancer.balance(Game.LOL, reversed, false);
        // then: 생성되는 스냅샷 ID를 제외한 실제 편성은 같다.
        for (int index = 0; index < 10; index++) {
            assertEquals(first.get(index).getParticipantId(), second.get(index).getParticipantId());
            assertEquals(first.get(index).getAssignedPosition(), second.get(index).getAssignedPosition());
            assertEquals(first.get(index).getTeam(), second.get(index).getTeam());
        }
    }

    @Test
    void supportsScoresWhoseTeamSumExceedsIntegerRange() {
        // given
        List<PreviewCandidate> candidates = candidates(Game.LOL).stream()
                .map(candidate -> new PreviewCandidate(candidate.participantId(), candidate.name(), Integer.MAX_VALUE,
                        candidate.preferredPositions())).toList();
        // when
        List<MatchParticipant> result = balancer.balance(Game.LOL, candidates, false);
        // then
        assertEquals(0, difference(result));
        assertEquals(5L * Integer.MAX_VALUE, result.stream().filter(p -> p.getTeam() == MatchParticipant.Team.A)
                .mapToLong(MatchParticipant::getScore).sum());
    }

    @Test
    void rejectsDuplicateParticipantsAndWrongGameRoles() {
        // given: 같은 참가자가 두 번 포함된 편성 입력을 준비한다.
        List<PreviewCandidate> duplicate = new ArrayList<>(candidates(Game.LOL));
        duplicate.set(9, duplicate.get(0));
        // when / then: 중복 참가자는 거절한다.
        BusinessException error = assertThrows(BusinessException.class, () -> balancer.balance(Game.LOL, duplicate, false));
        assertEquals(ErrorCode.INVALID_TEAM_COMPOSITION, error.getErrorCode());
        // when / then: 롤 포지션을 가진 참가자로 오버워치 편성을 요청하면 거절한다.
        BusinessException roles = assertThrows(BusinessException.class,
                () -> balancer.balance(Game.OVERWATCH, candidates(Game.LOL), false));
        assertEquals(ErrorCode.INVALID_PREFERRED_POSITION, roles.getErrorCode());
    }

    private List<PreviewCandidate> candidates(Game game) {
        Set<Position> roles = java.util.Arrays.stream(Position.values()).filter(position -> position.supports(game))
                .collect(Collectors.toSet());
        List<PreviewCandidate> result = new ArrayList<>();
        for (int index = 0; index < 10; index++) result.add(candidate(index, index, roles));
        return result;
    }

    private List<PreviewCandidate> singleRoleCandidates(Game game) {
        return candidates(game).stream().map(candidate -> new PreviewCandidate(candidate.participantId(), candidate.name(),
                candidate.score(), Set.of(game == Game.LOL ? Position.LOL_TOP : Position.OVERWATCH_TANK))).toList();
    }

    private PreviewCandidate candidate(int index, int score, Set<Position> roles) {
        return new PreviewCandidate(new UUID(0, index + 1), "참가자" + index, score, roles);
    }

    private int violations(List<PreviewCandidate> candidates, List<MatchParticipant> result) {
        return (int) result.stream().filter(participant -> candidates.stream()
                .filter(candidate -> candidate.participantId().equals(participant.getParticipantId()))
                .noneMatch(candidate -> candidate.preferredPositions().contains(participant.getAssignedPosition()))).count();
    }

    private long difference(List<MatchParticipant> result) {
        long a = result.stream().filter(p -> p.getTeam() == MatchParticipant.Team.A).mapToLong(MatchParticipant::getScore).sum();
        long b = result.stream().filter(p -> p.getTeam() == MatchParticipant.Team.B).mapToLong(MatchParticipant::getScore).sum();
        return Math.abs(a - b);
    }

    private void assertTeamRoles(Game game, List<MatchParticipant> result) {
        for (MatchParticipant.Team team : MatchParticipant.Team.values()) {
            List<MatchParticipant> members = result.stream().filter(p -> p.getTeam() == team).toList();
            assertEquals(5, members.size());
            if (game == Game.LOL) {
                assertEquals(5, members.stream().map(MatchParticipant::getAssignedPosition).distinct().count());
                assertTrue(members.stream().allMatch(p -> p.getAssignedPosition().supports(game)));
            } else {
                assertEquals(1, members.stream().filter(p -> p.getAssignedPosition() == Position.OVERWATCH_TANK).count());
                assertEquals(2, members.stream().filter(p -> p.getAssignedPosition() == Position.OVERWATCH_DAMAGE).count());
                assertEquals(2, members.stream().filter(p -> p.getAssignedPosition() == Position.OVERWATCH_SUPPORT).count());
            }
        }
    }
}
