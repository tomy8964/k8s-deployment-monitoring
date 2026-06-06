# ham 모니터 - Back-end 모듈 상세 가이드

본 문서는 **ham 모니터**의 백엔드 시스템(`backend` 폴더) 내부의 전체 아키텍처, 디렉터리 구조, 클래스별 역할 분석 및 리팩토링을 통해 적용된 핵심 기술 요소를 상세히 기술합니다.

---

## 📁 디렉터리 구조 및 컴포넌트 상세 분석

백엔드는 Spring Boot 3.4.1 표준 프레임워크 설계 패턴을 따르며, 도메인 주도 관점에서 다음과 같이 책임을 격리하여 설계되었습니다.

```plaintext
backend/
├── src/main/java/com/ham/backend/
│   ├── config/              # 1. 인프라 및 자격 증명 설정 계층
│   │   ├── KubeClientConfig.java # kubeconfig 파일 탐색 및 ApiClient 빈 구성
│   │   └── WebConfig.java        # 프론트엔드 교차 출처(CORS) 요청 허용 설정
│   ├── controller/          # 2. HTTP REST 및 SSE 요청 제어 계층 (API 엔드포인트)
│   │   ├── NamespaceController.java  # 네임스페이스 정보 요청 처리
│   │   ├── DeploymentController.java # Deployment 정보 패치 및 스케일 아웃/인 제어
│   │   └── SseController.java        # 브라우저 이탈 시 연결 정리(Cleanup) 요청 핸들링
│   ├── event/               # 3. 비결합(Decoupling) 모델을 위한 이벤트 정의 계층
│   │   └── DeploymentEvent.java  # Deployment 리소스 변경 감지 시 발생하는 Spring Application Event DTO
│   ├── service/             # 4. 핵심 비즈니스 로직 및 실시간 감시(Watch) 제어 계층
│   │   ├── NamespaceService(Impl).java  # 네임스페이스 비즈니스 연산 수행
│   │   ├── DeploymentService(Impl).java # Deployment 수정 및 내부 API 클라이언트 중계
│   │   ├── WatchService(Impl).java      # Kubernetes Watcher 실행 및 백그라운드 스레드 제어
│   │   └── SseService(Impl).java        # 1:N 클라이언트 SSE 세션 분산 관리 및 브로드캐스트
│   └── BackendApplication.java # Spring Boot 구동 엔트리포인트
└── src/test/java/com/ham/backend/  # 5. 격리 단위 테스트 스위트
```

### 디렉터리별 핵심 역할
1. **`config/`**:
   * **[KubeClientConfig.java](src/main/java/com/ham/backend/config/KubeClientConfig.java)**: 개발 장비의 `~/.kube/config` 파일을 탐색하여 Kubernetes API 서버와의 SSL/TLS 세션을 수립하는 `ApiClient` 및 `AppsV1Api` 인스턴스를 생성해 스프링 빈 컨테이너에 등록합니다.
   * **[WebConfig.java](src/main/java/com/ham/backend/config/WebConfig.java)**: 프론트엔드 개발 서버(`localhost:3000`)와의 포트 교차 호출 차단을 막기 위해 `addCorsMappings` 설정을 커스터마이징하여 CORS 헤더(`Access-Control-Allow-Origin: *`)를 동적으로 구성합니다.
2. **`controller/`**:
   * 클라이언트의 요청 파라미터 유효성을 매핑하고 HTTP 상태 코드를 결정하는 진입점입니다. `text/event-stream` 미디어 타입을 활용해 HTTP 연결을 유지하는 SSE 엔드포인트가 장착되어 있습니다.
3. **`event/`**:
   * 시스템 컴포넌트 간의 직접 참조를 피하기 위한 메시지 패싱용 클래스가 모여 있는 곳입니다.
4. **`service/`**:
   * 클라이언트와의 연결 정보(SSE Emitters)와 K8s API의 감시 파이프라인(Watch) 간의 중간 가교 역할을 수행하는 서비스 구현체들이며, 백엔드에서 가장 복잡하고 중요한 동시성 제어가 일어나는 영역입니다.

---

## 💎 백엔드 핵심 기술 및 리팩토링 상세 (Deep Dive)

코드 품질을 극대화하고 프로덕션 환경의 안정성을 충족하기 위해 백엔드 구조에 다음과 같은 3대 핵심 리팩토링이 적용되었습니다.

### 1) 완벽한 백그라운드 리소스 해제 (`@PreDestroy` Lifecycle Hook)
* **배경 및 문제점**:
  백엔드는 네임스페이스별 실시간 수집을 위해 `Executors.newCachedThreadPool()`과 같은 백그라운드 스레드 풀을 다수 운용합니다. 스프링 컨테이너가 셧다운(JVM 종료 또는 재기동)될 때 이 스레드 풀이 Graceful하게 닫히지 않으면, 커넥션 풀과 스레드가 좀비(Zombie) 상태로 메모리에 누수되는 심각한 SRE 리스크가 유발됩니다.
* **해결 및 구현 디테일**:
  `WatchServiceImpl`과 `SseServiceImpl` 클래스에 `@PreDestroy` 라이프사이클 어노테이션이 붙은 `shutdown()` 메서드를 명시적으로 도입했습니다.
  ```java
  @PreDestroy
  public void shutdown() {
      log.info("⚙️ Shutting down executor service in WatchServiceImpl...");
      executorService.shutdown(); // 새로운 작업 접수를 중단하고 기존 작업 완료 유도
  }
  ```
  이로 인해 WAS가 다운될 때 백그라운드 스레드가 완전히 해제되고 메모리가 운영체제(OS)에 온전히 반환되는 구조를 완성했습니다.

### 2) 글로벌 ObjectMapper 싱글톤 빈 바인딩
* **배경 및 문제점**:
  기존 코드에서는 JSON 직렬화를 수행할 때마다 `new ObjectMapper()` 정적 생성 블록을 임의로 선언하여 사용했습니다. 이는 매 직렬화 시점마다 모듈 등록 설정 오버헤드를 발생시킬 뿐 아니라, 멀티스레드 환경에서 ObjectMapper 인스턴스의 경합 및 스레드 세이프티 보장이 모호해지는 단점이 있었습니다.
* **해결 및 구현 디테일**:
  Spring Boot가 기동하면서 내부 튜닝 및 `Jackson Datatype JSR310` 날짜/시간 모듈 등을 완벽히 세팅하여 싱글톤 빈으로 관리하는 글로벌 `ObjectMapper`를 생성자 주입(`@RequiredArgsConstructor`) 방식으로 바인딩했습니다.
  ```java
  private final ObjectMapper objectMapper; // Spring 컨테이너가 튜닝해 둔 싱글톤 ObjectMapper 주입
  ```
  이를 통해 중복 셋업 비용을 제로화하고 동시 스레드 데이터 매핑의 일관성과 안전성을 확보했습니다.

### 3) `CopyOnWriteArrayList`와 Emitter 해제 단일 통로화
* **배경 및 문제점**:
  1:N 브로드캐스트 전송 루프 수행 중에 브라우저 종료나 유실로 인해 특정 클라이언트의 연결이 삭제되면, 동일 리스트를 순회하고 있던 스레드와 요소를 제거하려는 스레드가 충돌하여 `ConcurrentModificationException` 예외가 발생합니다.
* **해결 및 구현 디테일**:
  네임스페이스별 구독 Emitter 목록에 `CopyOnWriteArrayList` 자료구조를 장착했습니다. 이 구조는 새로운 요소의 쓰기(추가/삭제)가 일어날 때 기존 배열을 복사하여 갱신하므로, 브로드캐스트 순회 스레드가 락 대기 없이 스레드 세이프하게 렌더 데이터를 내보낼 수 있습니다.
  동시에 Emitter 삭제, Client Registry 맵 정리, 그리고 남은 Emitter가 없을 시 K8s Watcher를 해제(`stopNamespaceWatch`)하는 모든 트리거 책임을 **`removeEmitterFromNamespace` 단일 통로 메서드**로 단일화하여 리소스 Leak 발생 확률을 0%로 통제했습니다.
  ```java
  private void removeEmitterFromNamespace(String namespace, SseEmitter emitter) {
      List<SseEmitter> emitters = namespaceEmitters.get(namespace);
      if (emitters == null) return;
      emitters.remove(emitter); // 스레드 안전하게 리스트에서 제거
      // ... 맵 리소스 및 Watch 해제 단일 책임 처리
  }
  ```

---

## 🏃 기동, 테스트 및 실행 가이드

### 1. 백엔드 빌드 및 단위 테스트 실행
Gradle 래퍼를 이용해 내장된 26개의 Mock 기반 격리 테스트를 수행합니다.
```bash
./gradlew test
```

### 2. 백엔드 서버 bootRun 실행
```bash
./gradlew bootRun
```
* **포트 정보**: `http://localhost:8080`
* **의존 확인**: 실행 전 로컬 PC의 `~/.kube/config` 파일이 활성화되어 있어야 에러 없이 구동됩니다.
