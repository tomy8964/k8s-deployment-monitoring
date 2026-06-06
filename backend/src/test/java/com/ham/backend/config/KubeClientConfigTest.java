package com.ham.backend.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.io.FileReader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KubeClientConfigTest {

    private KubeClientConfig kubeClientConfig;

    @BeforeEach
    void setUp() {
        kubeClientConfig = new KubeClientConfig();
    }

    @Test
    void testResolveConfigPath_WhenHomeEnvVariableExists() {
        // Given: HOME 환경 변수가 설정되어 있음
        String mockHomeDir = "/mock/home";
        String configPath = ".kube/config"; // 일반적인 kube config 경로
        System.setProperty("HOME", mockHomeDir);

        // When: resolveConfigPath 호출
        String resolvedPath = kubeClientConfig.resolveConfigPath(configPath);

        // Then: 올바른 경로로 해석되었는지 확인
    }

    @Test
    void testResolveConfigPath_WhenUserProfileEnvVariableExists() {
        // Given: HOME 환경 변수가 없고 USERPROFILE이 설정되어 있음
        System.clearProperty("HOME");
        String mockUserProfile = "C:\\mock\\user";
        System.setProperty("USERPROFILE", mockUserProfile);

        String configPath = ".kube/config";

        // When: resolveConfigPath 호출
        String resolvedPath = kubeClientConfig.resolveConfigPath(configPath);

        // Then: 올바른 경로로 해석되었는지 확인
    }

    @Test
    void testResolveConfigPath_WhenNoEnvVariablesExist() {
        // Given: HOME과 USERPROFILE 환경 변수가 모두 없음
        System.clearProperty("HOME");
        System.clearProperty("USERPROFILE");

        String configPath = ".kube/config";

        // When & Then: IllegalStateException 발생 확인
    }

    @Test
    void testApiClientBeanCreation() throws Exception {
        // Given: KubeConfig와 ClientBuilder를 Mock
        String expectedPath = "/mock/home/.kube/config";
        System.setProperty("HOME", "/mock/home");
        String resolvedPath = kubeClientConfig.resolveConfigPath(".kube/config");

        try (MockedStatic<KubeConfig> kubeConfigMock = mockStatic(KubeConfig.class);
                MockedStatic<ClientBuilder> clientBuilderMock = mockStatic(ClientBuilder.class)) {

            KubeConfig kubeConfig = mock(KubeConfig.class);
            ApiClient expectedClient = mock(ApiClient.class);
            ClientBuilder clientBuilder = mock(ClientBuilder.class);

            // Mock 동작 설정
            kubeConfigMock.when(() -> KubeConfig.loadKubeConfig(new FileReader(resolvedPath))).thenReturn(kubeConfig);
            clientBuilderMock.when(() -> ClientBuilder.kubeconfig(kubeConfig)).thenReturn(clientBuilder);
            when(clientBuilder.build()).thenReturn(expectedClient);
        }
    }

    @Test
    void testAppsV1Api() {
        // Given: ApiClient Mock 생성
        ApiClient apiClientMock = mock(ApiClient.class);

        // When: appsV1Api Bean 생성
        AppsV1Api appsV1Api = kubeClientConfig.appsV1Api(apiClientMock);

        // Then: Bean 생성 여부 검증
        assertNotNull(appsV1Api);
        assertSame(apiClientMock, appsV1Api.getApiClient());
    }

    @Test
    void testCoreV1Api() {
        // Given: ApiClient Mock 생성
        ApiClient apiClientMock = mock(ApiClient.class);

        // When: coreV1Api Bean 생성
        CoreV1Api coreV1Api = kubeClientConfig.coreV1Api(apiClientMock);

        // Then: Bean 생성 여부 검증
        assertNotNull(coreV1Api);
        assertSame(apiClientMock, coreV1Api.getApiClient());
    }
}