# TeamForge

**현재 버전** `0.1.0` · 첫 MVP 릴리스 준비 중

TeamForge는 롤·오버워치 내전 참가자 10명을 편성 점수와 선호 포지션에 따라 두 팀으로 나누고, 경기 결과를 기록하는 웹 서비스입니다.

## 주요 기능

- 공유 계정 로그인과 참가자·게임 프로필 관리
- 게임별 수동 티어 입력과 롤 솔로랭크 연동
- 5:5 자동 편성, 참가자 교체 및 팀 확정
- 경기 결과 저장과 참가자별 최근 내전 조회

## 개발 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring MVC, Spring Security, Spring Data JPA |
| Frontend | React, TypeScript, Vite |
| 외부 API | Riot Games API, Spring WebClient |
| Database | H2(개발·테스트), PostgreSQL(운영), Flyway |
| API 문서 | springdoc OpenAPI |
| 빌드·테스트 | Gradle, JUnit, JaCoCo |

## 문서

- [백엔드 구조](docs/BACKEND_STRUCTURE.md)

## 향후 개발 계획

- 운영 PostgreSQL에서 마이그레이션을 검증하고 지인 대상 MVP 배포
- 여러 모임이 참가자와 경기 기록을 각각 관리할 수 있도록 확장
- 모임별 접근 권한과 OAuth 기반 개인 로그인 검토
