# TeamForge

롤·오버워치 내전 참가자 10명을 실력과 선호 포지션을 고려해 5:5 팀으로 편성하는 웹 프로젝트입니다. 본인과 지인들이 참가자 정보를 관리하고 내전 편성과 경기 결과를 기록하는 용도로 개발하고 있습니다.

## 주요 기능

- 참가자와 게임별 계정·선호 포지션·점수 관리
- 포지션과 팀 간 점수 차이를 고려한 자동 편성
- 확정 당시 팀 구성과 경기 결과 저장·조회
- 참가자별 최근 내전과 승패 조회
- Riot API를 통한 롤 솔로랭크 조회와 편성용 점수 갱신

현재 프론트엔드는 백엔드 연동 전 데모 단계입니다.

## 개발 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot, Spring MVC, Spring Data JPA |
| Frontend | React, TypeScript, Vite |
| 외부 API | Riot Games API, Spring WebClient |
| Database | H2, PostgreSQL |
| 마이그레이션 | Flyway |
| API 문서 | springdoc OpenAPI |
| 빌드·테스트 | Gradle, JUnit, JaCoCo |
