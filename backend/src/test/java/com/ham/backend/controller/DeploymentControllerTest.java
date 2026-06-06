package com.ham.backend.controller;

import com.ham.backend.service.DeploymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DeploymentController의 동작을 검증하는 테스트 클래스입니다.
 */
@WebMvcTest(DeploymentController.class)
class DeploymentControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private DeploymentService deploymentService;

        @Test
        void testWatchDeployments() throws Exception {
                // Given: Mock된 SseEmitter 반환
                SseEmitter mockEmitter = new SseEmitter();
                when(deploymentService.watchDeployments("test-namespace", "test-client"))
                                .thenReturn(mockEmitter);

                // When & Then: 엔드포인트 호출 및 결과 검증
                mockMvc.perform(get("/api/deployments/test-namespace/watch/test-client")
                                .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                                .andExpect(status().isOk());

                // Verify that the service was called
                verify(deploymentService, times(1)).watchDeployments("test-namespace", "test-client");
        }

        @Test
        void testListDeployments() throws Exception {
                // Given: Mock된 Deployment 목록 반환
                List<Map<String, Object>> mockDeployments = List.of(
                                Map.of("name", "deployment1", "replicas", 2),
                                Map.of("name", "deployment2", "replicas", 5));
                when(deploymentService.listDeployments(eq("test-namespace"))).thenReturn(mockDeployments);

                // When & Then: 엔드포인트 호출 및 결과 검증
                mockMvc.perform(get("/api/deployments/test-namespace")
                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].name").value("deployment1"))
                                .andExpect(jsonPath("$[0].replicas").value(2))
                                .andExpect(jsonPath("$[1].name").value("deployment2"))
                                .andExpect(jsonPath("$[1].replicas").value(5));

                // Verify that the service was called
                verify(deploymentService, times(1)).listDeployments(eq("test-namespace"));
        }

        @Test
        void testUpdateDeploymentReplicas() throws Exception {
                // Given: Mock 동작 설정
                doNothing().when(deploymentService).updateReplicaCount(eq("test-namespace"), eq("test-deployment"),
                                eq(3));

                // When & Then: 엔드포인트 호출 및 결과 검증
                mockMvc.perform(post("/api/deployments/test-namespace/test-deployment/replicas/3"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Updated successfully"));

                // Verify that the service was called
                verify(deploymentService, times(1))
                                .updateReplicaCount(eq("test-namespace"), eq("test-deployment"), eq(3));
        }

        @Test
        void testUpdateDeploymentImageTag() throws Exception {
                // Given: Mock 동작 설정
                doNothing().when(deploymentService).updateImageTag(eq("test-namespace"), eq("test-deployment"),
                                eq("test-image:latest"));

                // When & Then: 엔드포인트 호출 및 결과 검증
                mockMvc.perform(post("/api/deployments/test-namespace/test-deployment/image/test-image:latest"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("Updated successfully"));

                // Verify that the service was called
                verify(deploymentService, times(1))
                                .updateImageTag(eq("test-namespace"), eq("test-deployment"), eq("test-image:latest"));
        }
}