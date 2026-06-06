package com.ham.backend.service;

import com.ham.backend.event.DeploymentEvent;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.util.Watch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchServiceImplTest {

    @Mock
    private ApiClient apiClient;

    @Mock
    private AppsV1Api api;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ExecutorService executorService;

    @Mock
    private Watch<V1Deployment> mockWatch;

    @Mock
    private ConcurrentMap<String, Watch<V1Deployment>> namespaceWatchRegistry;

    private WatchServiceImpl watchService;

    @BeforeEach
    void setUp() {
        // 실제 구현체 객체를 생성하고 Spy로 감쌈
        watchService = spy(new WatchServiceImpl(apiClient, api, eventPublisher, objectMapper));

        // Mock 객체로 final 필드들을 강제 치환
        ReflectionTestUtils.setField(watchService, "namespaceWatchRegistry", namespaceWatchRegistry);
        ReflectionTestUtils.setField(watchService, "executorService", executorService);

        // ExecutorService가 Runnable을 실행하도록 설정
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(executorService).submit(any(Runnable.class));
    }

    @Test
    void startNamespaceWatch_withValidMockedWatch() throws Exception {
        // Given
        String namespace = "test-namespace";

        Watch<V1Deployment> mockWatch = mock(Watch.class);
        Watch.Response<V1Deployment> mockResponse = new Watch.Response<>();
        mockResponse.type = "ADDED";

        V1Deployment mockDeployment = mock(V1Deployment.class);
        io.kubernetes.client.openapi.models.V1ObjectMeta mockMeta = mock(io.kubernetes.client.openapi.models.V1ObjectMeta.class);
        when(mockMeta.getName()).thenReturn("test-deployment");
        when(mockDeployment.getMetadata()).thenReturn(mockMeta);

        io.kubernetes.client.openapi.models.V1DeploymentSpec mockSpec = mock(io.kubernetes.client.openapi.models.V1DeploymentSpec.class);
        when(mockSpec.getReplicas()).thenReturn(3);
        when(mockDeployment.getSpec()).thenReturn(mockSpec);

        io.kubernetes.client.openapi.models.V1DeploymentStatus mockStatus = mock(io.kubernetes.client.openapi.models.V1DeploymentStatus.class);
        when(mockStatus.getAvailableReplicas()).thenReturn(3);
        when(mockStatus.getReadyReplicas()).thenReturn(3);
        when(mockDeployment.getStatus()).thenReturn(mockStatus);

        io.kubernetes.client.openapi.models.V1PodTemplateSpec mockTemplate = mock(io.kubernetes.client.openapi.models.V1PodTemplateSpec.class);
        io.kubernetes.client.openapi.models.V1PodSpec mockPodSpec = mock(io.kubernetes.client.openapi.models.V1PodSpec.class);
        io.kubernetes.client.openapi.models.V1Container mockContainer = mock(io.kubernetes.client.openapi.models.V1Container.class);
        when(mockContainer.getImage()).thenReturn("nginx:latest");
        when(mockPodSpec.getContainers()).thenReturn(java.util.List.of(mockContainer));
        when(mockTemplate.getSpec()).thenReturn(mockPodSpec);
        when(mockSpec.getTemplate()).thenReturn(mockTemplate);

        mockResponse.object = mockDeployment;

        java.util.Iterator<Watch.Response<V1Deployment>> mockIterator = mock(java.util.Iterator.class);
        when(mockIterator.hasNext()).thenReturn(true, false);
        when(mockIterator.next()).thenReturn(mockResponse);
        when(mockWatch.iterator()).thenReturn(mockIterator);

        // Mock된 createDeploymentWatch 반환값 설정
        doReturn(mockWatch).when(watchService).createDeploymentWatch(namespace);

        // When
        watchService.startNamespaceWatch(namespace);

        // Then
        verify(namespaceWatchRegistry).putIfAbsent(eq(namespace), eq(mockWatch));
        verify(eventPublisher).publishEvent(any(DeploymentEvent.class));
    }

    @Test
    void startNamespaceWatch_shouldNotPublishEvent_whenHasNoNext() throws Exception {
        // Given
        String namespace = "test-namespace";

        Watch<V1Deployment> mockWatch = mock(Watch.class);
        java.util.Iterator<Watch.Response<V1Deployment>> mockIterator = mock(java.util.Iterator.class);
        when(mockIterator.hasNext()).thenReturn(false);
        when(mockWatch.iterator()).thenReturn(mockIterator);

        // Mock된 createDeploymentWatch 반환값 설정
        doReturn(mockWatch).when(watchService).createDeploymentWatch(namespace);

        // When
        watchService.startNamespaceWatch(namespace);

        // Then
        verify(namespaceWatchRegistry).putIfAbsent(eq(namespace), eq(mockWatch));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void startNamespaceWatch_shouldHandleExceptionGracefully() throws Exception {
        // Given
        String namespace = "test-namespace";

        // createDeploymentWatch 호출 시 예외 발생하도록 설정
        doThrow(new RuntimeException("Test Exception")).when(watchService).createDeploymentWatch(namespace);

        // When & Then
        assertDoesNotThrow(() -> watchService.startNamespaceWatch(namespace));

        // Exception이 발생했더라도 registry에 추가되지 않아야 함
        verify(namespaceWatchRegistry, never()).putIfAbsent(eq(namespace), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}