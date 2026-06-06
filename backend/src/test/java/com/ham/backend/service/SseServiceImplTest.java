package com.ham.backend.service;

import com.ham.backend.event.DeploymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SseServiceImplTest {

    @Mock
    private WatchService watchService;

    @InjectMocks
    private SseServiceImpl sseService;

    @BeforeEach
    void setUp() throws Exception {
        // Reflection을 사용해 private 필드에 접근
        Field namespaceEmittersField = SseServiceImpl.class.getDeclaredField("namespaceEmitters");
        Field emitterToClientIdMapField = SseServiceImpl.class.getDeclaredField("emitterToClientIdMap");

        // private 필드 수정을 허용
        namespaceEmittersField.setAccessible(true);
        emitterToClientIdMapField.setAccessible(true);

        // 초기화
        namespaceEmittersField.set(sseService, new ConcurrentHashMap<>());
        emitterToClientIdMapField.set(sseService, new ConcurrentHashMap<>());
    }

    @Test
    void testWatchDeployments() throws Exception {
        // Given: namespace와 clientId를 지정
        String namespace = "test-namespace";
        String clientId = "test-client-id";

        // When: watchDeployments 호출
        SseEmitter emitter = sseService.watchDeployments(namespace, clientId);

        // Then: 예상된 동작 검증

        // Reflection을 사용해서 private 필드에 접근
        Field emitterToClientIdMapField = SseServiceImpl.class.getDeclaredField("emitterToClientIdMap");
        emitterToClientIdMapField.setAccessible(true);

        // emitterToClientIdMap 값 검증
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<SseEmitter, String> emitterToClientIdMap = (ConcurrentHashMap<SseEmitter, String>) emitterToClientIdMapField
                .get(sseService);

        // Emitter와 clientId의 매핑 확인
        assertEquals(clientId, emitterToClientIdMap.get(emitter));

        // 다른 검증
        assertEquals(600_000L, emitter.getTimeout());
        verify(watchService, times(1)).startNamespaceWatch(namespace);
    }

    @Test
    void testHandleDeploymentEvent() throws Exception {
        // Given: 네임스페이스와 이벤트 설정
        String namespace = "test-namespace";
        DeploymentEvent event = new DeploymentEvent(this, namespace, "test-data");
        SseEmitter emitter = mock(SseEmitter.class); // Mock Emitter 생성

        // Reflection으로 namespaceEmitters 필드에 값 설정
        Field namespaceEmittersField = SseServiceImpl.class.getDeclaredField("namespaceEmitters");
        namespaceEmittersField.setAccessible(true);
        ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> namespaceEmitters = (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>) namespaceEmittersField
                .get(sseService);
        namespaceEmitters.put(namespace, new CopyOnWriteArrayList<>(Collections.singletonList(emitter)));

        // When: 이벤트 처리 실행
        sseService.handleDeploymentEvent(event);
    }

    @Test
    void testNotifyEmitters() throws Exception {
        // Given: namespace와 data 설정
        String namespace = "test-namespace";
        String data = "test-data";
        SseEmitter emitter1 = mock(SseEmitter.class);
        SseEmitter emitter2 = mock(SseEmitter.class);

        // Reflection으로 namespaceEmitters 필드에 값 설정
        Field namespaceEmittersField = SseServiceImpl.class.getDeclaredField("namespaceEmitters");
        namespaceEmittersField.setAccessible(true);
        ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> namespaceEmitters = (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>) namespaceEmittersField
                .get(sseService);
        namespaceEmitters.put(namespace, new CopyOnWriteArrayList<>(List.of(emitter1, emitter2)));

        // When: Emitter에 데이터 알림 전송
        sseService.notifyEmitters(namespace, data);
    }

    @Test
    void testCleanupSseConnections() throws Exception {
        // Given: 클라이언트 ID와 Emitter 설정
        String clientId = "test-client-id";
        SseEmitter emitter = mock(SseEmitter.class);

        // Reflection으로 필드에 값 설정
        Field namespaceEmittersField = SseServiceImpl.class.getDeclaredField("namespaceEmitters");
        Field emitterToClientIdMapField = SseServiceImpl.class.getDeclaredField("emitterToClientIdMap");
        namespaceEmittersField.setAccessible(true);
        emitterToClientIdMapField.setAccessible(true);

        ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> namespaceEmitters = (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>) namespaceEmittersField
                .get(sseService);
        ConcurrentHashMap<SseEmitter, String> emitterToClientIdMap = (ConcurrentHashMap<SseEmitter, String>) emitterToClientIdMapField
                .get(sseService);

        namespaceEmitters.put("test-namespace", new CopyOnWriteArrayList<>(List.of(emitter)));
        emitterToClientIdMap.put(emitter, clientId);

        // When: cleanup 실행
        int cleanedCount = sseService.cleanupSseConnections(clientId);

        // Then: Emitter가 제대로 정리되었는지 확인
        verify(emitter, times(1)).complete(); // Emitter가 종료되었는지 확인
    }

    @Test
    void testHandleIOException() throws Exception {
        // Given: namespace와 Emitter 설정
        String namespace = "test-namespace";
        SseEmitter emitter = mock(SseEmitter.class);

        // Reflection으로 namespaceEmitters 필드에 값 설정
        Field namespaceEmittersField = SseServiceImpl.class.getDeclaredField("namespaceEmitters");
        namespaceEmittersField.setAccessible(true);
        ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> namespaceEmitters = (ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>) namespaceEmittersField
                .get(sseService);
        namespaceEmitters.put(namespace, new CopyOnWriteArrayList<>(List.of(emitter)));

        // Emitter에서 IOException 발생
        doThrow(new IOException("Connection reset by peer")).when(emitter).send(any(Object.class));

        // When: notifyEmitters 호출
        sseService.notifyEmitters(namespace, "test-data");
    }

    @Test
    void testRemoveExistingEmitter() throws Exception {
        // Given: 기존 Emitter 설정
        String clientId = "test-client-id";
        SseEmitter existingEmitter = mock(SseEmitter.class);

        // Reflection으로 필드에 값 설정
        Field sseEmitterRegistryField = SseServiceImpl.class.getDeclaredField("sseEmitterRegistry");
        sseEmitterRegistryField.setAccessible(true);
        ConcurrentHashMap<String, SseEmitter> sseEmitterRegistry = (ConcurrentHashMap<String, SseEmitter>) sseEmitterRegistryField
                .get(sseService);
        sseEmitterRegistry.put(clientId, existingEmitter);

        // When: 새로운 연결 생성
        sseService.watchDeployments("test-namespace", clientId);

        // Then: 기존 Emitter가 종료되었는지 확인
        verify(existingEmitter, times(1)).complete(); // 기존 Emitter 종료
        assertEquals(1, sseEmitterRegistry.size()); // 새로운 Emitter로 대체되었는지 확인
    }
}
