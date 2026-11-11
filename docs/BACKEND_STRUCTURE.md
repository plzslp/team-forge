# 백엔드 구조

TeamForge 백엔드는 Java 17과 Spring Boot를 사용하는 단일 Gradle 프로젝트입니다. HTTP 요청은 Controller → Service → Repository 순으로 처리하고, Service에서 트랜잭션을 관리합니다. 각 도메인의 `Api` 추상 클래스에 Swagger 계약을, `dto/request`와 `dto/response`에 요청·응답 모델을 둡니다.

| 패키지 | 책임 |
| --- | --- |
| `participant` | 참가자 등록, 이름 수정, 조회, 논리 삭제 |
| `gameprofile` | 게임별 계정·선호 포지션·점수 관리 |
| `match` | 팀 편성, 경기 확정과 스냅샷 저장, 결과 입력·조회 |
| `external.riot` | WebClient 기반 Riot 계정·솔로랭크 조회 |
| `common.exception` | 공통 오류 응답 |

기본 `dev` 프로필은 메모리 H2와 로컬 H2 Console을 사용합니다. `prod`는 환경 변수로 PostgreSQL에 연결합니다. Flyway가 스키마를 관리하고 JPA는 매핑을 검증합니다. 운영 PostgreSQL의 실제 연결과 잠금 검증은 환경 구성 후 수행합니다.

## API

요청과 응답의 전체 스키마는 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html)에서 확인할 수 있습니다.

| 기능 | 경로 |
| --- | --- |
| 참가자 등록·목록 | `POST /api/participants`, `GET /api/participants` |
| 참가자 상세·이름 수정·삭제 | `GET/PATCH/DELETE /api/participants/{id}` |
| 게임 프로필 조회·전체 수정 | `GET/PUT /api/participants/{id}/profiles/{game}` |
| 롤 솔로랭크 갱신 | `POST /api/participants/{participantId}/profiles/LOL/refresh` |
| 게임별 프로필 목록 | `GET /api/game-profiles?game=LOL` |
| 팀 편성 미리보기 | `POST /api/team-previews` |
| 경기 확정 | `POST /api/matches` |
| 경기 목록·상세 | `GET /api/matches?game=LOL`, `GET /api/matches/{id}` |
| 경기 결과 입력·전체 수정 | `PUT /api/matches/{id}/result` |
| 참가자 최근 내전 | `GET /api/participants/{id}/matches?game=LOL` |

`game`은 `LOL` 또는 `OVERWATCH`입니다. 목록은 `page=0`, `size=20`이 기본이며 최대 100건까지 요청할 수 있습니다. 참가자·프로필 목록은 이름·ID 오름차순, 경기 목록은 생성 시각·ID 내림차순입니다. 프로필 목록은 `keyword`와 `position`으로 필터링할 수 있습니다.

참가자 최근 내전은 `game`이 필수이고 기본 페이지 크기는 10입니다. 확정 기록을 생성 시각·ID 내림차순으로 조회하고 경기 ID, 생성 시각, 소속 팀, A/B 스코어와 소속 팀 기준 승패를 반환합니다. 결과 미입력 시 스코어와 승패는 `null`입니다. 참가자가 없거나 삭제됐으면 404를 반환합니다.

## 편성부터 결과 입력까지

1. **프로필 준비** · 활성 참가자의 게임 프로필을 사용합니다. 수동 점수는 환산 점수보다 우선하며, `0`도 유효한 수동 점수입니다.
2. **미리보기** · 중복 없는 참가자 10명을 5:5로 나눕니다. 비선호 배정을 최소화한 뒤 팀 점수 차이를 최소화합니다. 비선호 배정이 필요하면 `allowNonPreferredPositions: true`를 명시해야 합니다. 응답의 `participantVersions`는 확정 요청에 사용합니다. [편성 알고리즘 설명](TEAM_BALANCER_GUIDE.md)
3. **확정** · 최종 팀·포지션과 미리보기 버전을 전달합니다. 서버는 참가자 행, 게임 프로필 행 순으로 잠그고 최신 버전과 역할 구성을 확인합니다. 오래된 미리보기는 409로 거절합니다. 성공하면 확정 당시 이름·점수·포지션을 스냅샷으로 저장하고 상세 조회 `Location`을 반환합니다.
4. **결과 입력** · `scoreA`, `scoreB`는 0 이상의 JSON 정수이며 `memo`는 최대 2000자입니다. 메모를 생략하거나 `null`로 보내면 빈 문자열로 저장합니다. 미입력은 `result: null`, 입력된 0:0은 `DRAW`입니다. 참가자 수정·삭제 후에도 경기 스냅샷을 기준으로 조회하고 결과를 수정할 수 있습니다.

결과 수정은 같은 버전을 읽은 트랜잭션이 서로 다른 결과를 저장하려 할 때 낙관적 잠금으로 충돌을 감지합니다. 충돌 요청은 409 `CONCURRENT_MODIFICATION`을 반환합니다. 이전 트랜잭션이 완료된 뒤 새로 읽고 쓰는 요청은 기존 결과를 교체합니다.

오류 응답은 `code`와 `message`를 반환합니다. 잘못된 입력은 400, 리소스가 없으면 404, 버전·잠금 충돌은 409입니다.

## Riot 연동

롤 프로필의 `accountId`에 Riot ID(`이름#태그`)를 등록한 뒤 갱신 API를 호출합니다. 한국 서버 기준으로 Account API(`asia`)에서 PUUID를 얻고 League API(`kr`)에서 `RANKED_SOLO_5x5`를 조회합니다. 프로필 응답의 `riotRank`에 티어·디비전·LP·조회 시각을 반환합니다. 조회 전은 `riotRank: null`, 정상 조회된 언랭크는 티어·디비전·LP가 null입니다.

환산 점수는 Iron~Diamond에서 `티어 인덱스 × 400 + 디비전 단계 × 100 + LP`입니다. 티어 인덱스는 Iron 0부터 Diamond 6까지, 디비전 단계는 IV 0부터 I 3까지입니다. Master·Grandmaster·Challenger는 각각 2800·3000·3200에 LP를 더합니다. 정상 조회된 언랭크의 환산 점수는 0이며 수동 점수는 유지합니다. 계정 변경 시 기존 Riot 조회 정보는 비워지고 환산 점수는 다음 정상 갱신 때 교체됩니다.

외부 호출은 DB 트랜잭션 밖에서 수행하고 저장 시 버전·활성 상태를 재검증합니다. 인증·호출 제한은 503, 통신·응답 오류와 5초 시간 초과는 502입니다. 외부 실패 시 기존 조회 정보와 점수를 보존합니다. 429 응답 이후에는 해당 서버 프로세스가 `Retry-After` 동안 Riot 호출을 중단합니다.
