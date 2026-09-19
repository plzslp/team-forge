# TeamForge Frontend

React · TypeScript · Vite 기반 내전 팀 편성 데모입니다.

## 실행

Node.js와 npm이 필요합니다. 저장소 루트에서 실행하는 Windows 명령입니다.

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

접속: [http://127.0.0.1:5173](http://127.0.0.1:5173)

데모는 백엔드 없이 실행할 수 있습니다. 종료는 해당 터미널에서 `Ctrl+C`를 누릅니다.

## 빌드와 미리보기

`frontend` 폴더에서 실행합니다.

```powershell
npm.cmd run build
npm.cmd run preview -- --port 4173 --strictPort
```

빌드 결과는 `dist/`에 생성됩니다. [http://127.0.0.1:4173](http://127.0.0.1:4173)에서 확인할 수 있으며, 소스 변경 후에는 다시 빌드해야 합니다.

## 데모 안내

- 실제 Riot API·DB와 연결되지 않은 샘플 데이터를 사용합니다.
- 참여자·편성 기록·경기 결과는 새로고침하면 초기화되고, 테마 선택만 유지됩니다.
- 팀 생성은 총점 차이만 고려하며 포지션 배정을 보장하지 않습니다.
- 프론트 빌드 결과는 아직 백엔드 JAR에 포함되지 않습니다.

프로젝트 전체 안내는 [루트 README](../README.md)를 참고하세요.
