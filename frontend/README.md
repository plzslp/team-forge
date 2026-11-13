# TeamForge Frontend

React · TypeScript · Vite 기반 내전 팀 편성 화면입니다. Spring Boot API와 세션 로그인을 사용합니다.

## 실행

Node.js와 npm이 필요합니다. 먼저 공유 계정 환경 변수를 설정하고 백엔드를 8080 포트에서 실행합니다. 계정 설정은 [백엔드 구조 문서](../docs/BACKEND_STRUCTURE.md)를 참고하세요.

저장소 루트에서 실행하는 Windows 명령입니다.

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --port 5173 --strictPort
```

접속: [http://127.0.0.1:5173](http://127.0.0.1:5173)

공유 계정으로 로그인합니다. Vite는 `/api`, `/login`, `/logout` 요청을 백엔드로 전달합니다. 종료는 해당 터미널에서 `Ctrl+C`를 누릅니다.

## 빌드와 미리보기

`frontend` 폴더에서 실행합니다.

```powershell
npm.cmd run build
npm.cmd run preview -- --port 4173 --strictPort
```

빌드 결과는 `dist/`에 생성됩니다. [http://127.0.0.1:4173](http://127.0.0.1:4173)에서 확인할 수 있습니다. 미리보기에서도 8080 포트의 백엔드가 필요합니다.

프로젝트 전체 안내는 [루트 README](../README.md)를 참고하세요.
