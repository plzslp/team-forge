# TeamForge frontend

React + TypeScript + Vite 기반 MVP 화면입니다.

## 실행

```sh
cd frontend
npm ci
npm run dev
```

개발 주소는 Vite가 출력하는 localhost 주소입니다. `/api` 요청은 `localhost:8080`의 Spring Boot로 프록시됩니다.

```sh
npm run build
npm run preview
```

빌드 결과는 `dist`에 생성됩니다. Spring Boot 통합 패키징은 아직 연결하지 않았습니다. 추후 Gradle `processResources`의 입력으로 `dist`를 등록하면 소스 폴더를 오염시키지 않고 JAR에 포함할 수 있습니다.

## 구현된 화면

- 게임 선택, 닉네임 검색, 포지션 필터
- 참여자 수동 등록, 최대 10명 선택 및 해제
- 정확히 10명 선택 후 데모 팀 생성
- 반대 팀 선수 두 명 교체, 점수 재계산
- 팀 확정과 세션 내 편성 기록 조회
- PC / 모바일 대응

## 데모 범위

모든 데이터는 메모리에서 관리하며 새로고침하면 초기화됩니다. Riot API, DB, 경기 승패 기록은 연결하지 않았습니다. 오버워치 점수는 수동 점수로 표시합니다.

`src/domain.ts`의 `balance`는 총점 차이만 최소화하는 UI 데모 알고리즘입니다. 포지션 배정·고정 조건을 보장하지 않습니다. 실제 팀 계산과 검증은 Spring Boot application/domain 계층에서 구현한 뒤 응답으로 대체합니다.

`src/App.tsx`는 화면 상호작용, `src/style.css`는 공통 스타일과 반응형 레이아웃을 담당합니다. API 확정 후 기능별 컴포넌트와 API 클라이언트로 분리할 예정입니다.
