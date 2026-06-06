package com.ham.backend.controller;

import com.ham.backend.service.SseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SseController.class)
class SseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SseService sseService;

    @Test
    void cleanupSseConnections_ShouldReturnSuccessMessage() throws Exception {
        // Given: Mock 서비스의 결과 설정
        String clientId = "test-client-id";
        int cleanedCount = 5; // 정리된 SSE 연결 개수
        when(sseService.cleanupSseConnections(clientId)).thenReturn(cleanedCount);

        // When & Then: 요청 실행 및 기대 결과 검증
        mockMvc.perform(post("/api/sse/cleanup/" + clientId))
                .andExpect(status().isOk())
                .andExpect(content().string("SSE connections cleaned: " + cleanedCount));

        // Verify: 서비스 호출 여부 확인
        verify(sseService).cleanupSseConnections(clientId);
    }

    @Test
    void cleanupSseConnections_ShouldThrowException_WhenClientIdIsMissing() throws Exception {
        // When & Then: 클라이언트 ID가 없는 상태로 호출하고 예외 메시지 확인
        mockMvc.perform(post("/api/sse/cleanup/"))
                .andExpect(status().isNotFound()); // URL이 잘못되어 404 응답을 예상
    }

    @Test
    void cleanupSseConnections_ShouldThrowBadRequest_WhenClientIdIsEmpty() throws Exception {
        // When & Then: 빈 클라이언트 ID로 API 호출
        mockMvc.perform(post("/api/sse/cleanup/ ")) // 공백 클라이언트 ID 포함
                .andExpect(status().isBadRequest())
                .andExpect(content().string("clientId is required."));
    }
}