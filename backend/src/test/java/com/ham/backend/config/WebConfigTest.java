package com.ham.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.Mockito.*;

/**
 * WebConfig의 addCorsMappings 메서드를 테스트합니다.
 */
class WebConfigTest {

    private WebConfig webConfig;
    private CorsRegistry mockRegistry;

    @BeforeEach
    void setUp() {
        // 테스트 대상 객체 생성
        webConfig = new WebConfig();
        // Mock 객체 생성
        mockRegistry = mock(CorsRegistry.class);
    }

    @Test
    void testAddCorsMappings_Success() {
        CorsRegistration mockRegistration = mock(CorsRegistration.class);
        when(mockRegistry.addMapping("/api/**")).thenReturn(mockRegistration);
        when(mockRegistration.allowedOrigins("http://localhost:3000")).thenReturn(mockRegistration);
        when(mockRegistration.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")).thenReturn(mockRegistration);
        when(mockRegistration.allowedHeaders("*")).thenReturn(mockRegistration);

        // When: addCorsMappings 호출
        webConfig.addCorsMappings(mockRegistry);

        // Then: 적절한 동작이 수행되었는지 검증
        verify(mockRegistry, times(1)).addMapping("/api/**");
        verify(mockRegistration, times(1)).allowedOrigins("http://localhost:3000");
        verify(mockRegistration, times(1)).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        verify(mockRegistration, times(1)).allowedHeaders("*");
    }
}
