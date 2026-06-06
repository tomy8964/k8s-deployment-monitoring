package com.ham.backend.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Kubernetes 클라이언트 설정을 정의합니다.
 */
@Configuration
public class KubeClientConfig {

    @Value("${kube.configPath}")
    private String configPath;

    /**
     * Kubernetes 클라이언트를 생성합니다.
     */
    @Bean
    public ApiClient apiClient() throws IOException {
        String kubeConfigPath = resolveConfigPath(configPath);
        ApiClient client = ClientBuilder
                .kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath)))
                .build();
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        return client;
    }

    @Bean
    public AppsV1Api appsV1Api(ApiClient apiClient) {
        return new AppsV1Api(apiClient);
    }

    @Bean
    public CoreV1Api coreV1Api(ApiClient apiClient) {
        return new CoreV1Api(apiClient);
    }

    /**
     * 환경 변수를 통해 지정된 경로를 해석합니다.
     */
    String resolveConfigPath(String configPath) {
        String homeDir = System.getenv("HOME");
        if (homeDir == null) {
            homeDir = System.getenv("USERPROFILE");
        }

        if (homeDir == null) {
            throw new IllegalStateException("HOME 또는 USERPROFILE 환경 변수가 정의되지 않았습니다.");
        }

        return Paths.get(homeDir, configPath).toString();
    }

}
