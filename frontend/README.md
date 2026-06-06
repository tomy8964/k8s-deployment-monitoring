# ham 모니터 - Front-end 모듈 상세 가이드

본 문서는 **ham 모니터**의 프론트엔드 시스템(`frontend` 폴더) 내부의 전체 아키텍처, 폴더 구조 분석, 그리고 리팩토링을 통해 적용된 핵심 UI/UX 및 네트워킹 기술 요소를 상세히 다룹니다.

---

## 📁 디렉터리 구조 및 컴포넌트 상세 분석

프론트엔드는 React 18.x + Vite 빌드 툴체인을 기반으로 설계되었으며, 단일 페이지 애플리케이션(SPA) 구조로 유지보수성과 파일 가독성을 고려하여 다음과 같이 분리되어 있습니다.

```plaintext
frontend/
├── public/                      # 빌드 없이 정적 복사되는 에셋 보관소
├── src/
│   ├── api/                     # 1. 외부 백엔드 연동 네트워킹 계층
│   │   ├── apiClient.js         # Axios 인스턴스 공통 설정 및 기본 셋업
│   │   ├── deployments.js       # Deployment 리소스 조회, SSE 구독 및 수정 API
│   │   ├── namespaces.js        # Namespace 리스트 패치 API
│   │   └── sse.js               # 연결 정리(Cleanup) 전송 API
│   ├── components/              # 2. 독립 및 합성 가능 재사용 UI 컴포넌트 계층
│   │   ├── DeploymentCard.jsx   # 개별 Deployment 정보 시각화, 입력 밸리데이터, Toast 알림 탑재
│   │   ├── DeploymentList.jsx   # SSE 이벤트 수신 감지 및 화면 상태 통합 렌더러
│   │   └── NamespaceList.jsx    # 네임스페이스 링크 메뉴 렌더링 및 페이지 이탈 감시자
│   ├── pages/                   # 3. 라우팅 가상 페이지 계층
│   │   ├── NamespacePage.jsx    # 네임스페이스 선택 메인 뷰
│   │   └── DeploymentListPage.jsx # Deployment 목록 실시간 모니터링 대시보드 뷰
│   ├── style/                   # 4. 모듈러 CSS 스타일 계층
│   │   ├── DeploymentCard.module.css
│   │   ├── DeploymentList.module.css
│   │   └── NamespaceList.module.css
│   ├── App.jsx                  # 전역 헤더 레이아웃 구성 및 SPA 라우팅 경로 매핑
│   └── index.jsx                # DOM 컨텍스트 마운트 및 라우터 주입
├── package.json                 # 의존 모듈 명세 및 실행 스크립트 정의
└── vite.config.js               # Vite 번들러, HMR 개발용 서버 구성 파일
```

### 디렉터리별 핵심 역할
1. **`api/`**:
   * 백엔드 API와의 I/O 스펙을 관리하는 계층입니다. Axios를 이용한 REST 요청과 HTML5 표준 API인 `EventSource`를 통한 SSE 실시간 스트림 객체를 공급합니다.
2. **`components/`**:
   * 비즈니스 단위를 표현하는 독립적인 재사용 UI 조각들입니다. 스타일은 컴포넌트별 CSS 파일(`.module.css`)과 바인딩되어 스타일 전파 충돌을 원천 차단합니다.
3. **`pages/`**:
   * React Router DOM이 참조하는 최상위 Route 타겟 뷰 컴포넌트들입니다.

---

## 💎 프론트엔드 핵심 기술 및 리팩토링 상세 (Deep Dive)

프론트엔드 코드 품질을 고도화하고 사용자 만족도를 극대화하기 위해 다음과 같은 핵심 프론트엔드 리팩토링 요소들이 적용되었습니다.

### 1) 프론트엔드 HTTP 모듈 공통화 (`apiClient.js`)
* **배경 및 문제점**:
  기존에는 개별 API 파일(`deployments.js`, `namespaces.js`, `sse.js`)마다 `axios.post('http://localhost:8080/api/...')`처럼 API 주소 호스트를 각각 하드코딩하여 사용하고 있었습니다. 이는 서버의 포트가 변경되거나 프로덕션 배포 서버 주소로 마이그레이션할 때 모든 자바스크립트 파일을 열어 주소를 직접 고쳐야 하는 심각한 하드코딩 중복 문제를 안고 있었습니다.
* **해결 및 구현 디테일**:
  공통 Axios 인스턴스인 **[apiClient.js](file:///c:/Users/HamGeonwook/Desktop/Git/k8s-deployment-monitoring/frontend/src/api/apiClient.js)**를 구축하여 네트워킹을 중앙 집중화했습니다.
  ```javascript
  const apiClient = axios.create({
      baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
      timeout: 10000,
  });
  ```
  Vite 번들러의 환경 변수 시스템(`import.meta.env.VITE_API_URL`)을 내장 바인딩하여 소스 코드의 수정 없이 배포 환경 설정만으로 서버 타겟 도메인을 다이내믹하게 조정할 수 있게 변경했습니다.

### 2) 입력 데이터 폼 유효성 검증(Validator) 장착
* **배경 및 문제점**:
  서버로 잘못된 값이 전송되면 백엔드 내부의 Kubernetes Java Client 혹은 API 서버 자체에서 예외(HTTP 400 Bad Request, API Exception)가 터지고, UI가 일시적으로 깨질 수 있습니다.
* **해결 및 구현 디테일**:
  `DeploymentCard.jsx` 컴포넌트에서 수정 값이 백엔드로 전송되기 전에 1차적으로 폼 입력 데이터를 정밀 검증하는 **Front-end 밸리데이터**를 구현했습니다.
  * **음수 복제본 제한**: Replicas 입력 `input` 요소에 `min="0"` 속성을 주입하고, 입력 값을 `parseInt(val, 10)`로 정수 변환하여 `0` 미만의 정수나 NaN(숫자가 아닌 값)의 제출을 1차 필터링합니다.
  * **공백 태그 전송 차단**: 이미지 태그 수정 폼에서 입력 문자열의 `.trim()`을 검사하여 빈 공백 태그나 아예 값이 비어 있는 문자열을 수정하려 시도할 경우 백엔드 API 요청 호출을 미리 차단하고 사용자에게 피드백을 전달합니다.

### 3) Premium UX를 충족하는 커스텀 Toast UI 알림 시스템 (alert 제거)
* **배경 및 문제점**:
  기존 코드에서는 통신 완료 알림을 브라우저의 기본 `alert()` 창으로 처리했습니다. 동기식 `alert()`는 브라우저의 렌더링 스레드를 블로킹하여 화면을 일시적으로 정지시키며, 디자인 톤앤매너를 깨뜨려 사용자 경험을 크게 저해합니다.
* **해결 및 구현 디테일**:
  부드러운 인터랙션을 제공하는 논블로킹 커스텀 **Toast Alert** 모듈을 직접 개발하여 `DeploymentCard.jsx` 컴포넌트에 장착했습니다.
  * **동적 상태 관리**: React Hook인 `useState`를 통해 `{ show: false, message: '', type: 'success' }` 상태를 관리합니다.
  * **자동 소멸 제어**: 수정 성공 혹은 에러 응답 수신 시 Toast 상태를 노출시키고, `setTimeout`을 연동하여 정확히 **3초(3000ms)**가 지나면 상태를 다시 숨김 처리합니다.
  * **CSS 애니메이션**: `@keyframes` 페이드인 및 위에서 아래로 미끄러지는 듯한 트랜지션 모션 처리를 적용하여 화면 멈춤(Blocking) 현상 없이 깔끔하게 동작 완료 피드백을 전달합니다.

---

## 🏃 기동 및 개발 서버 실행 가이드

### 1. 패키지 설치
`frontend` 폴더 루트로 진입하여 의존 모듈을 설치합니다.
```bash
npm install
```

### 2. HMR 개발 서버 구동
Vite 로컬 개발 HMR 서버를 실행합니다.
```bash
npm run dev
```
* **구동 포트**: `http://localhost:3000`
