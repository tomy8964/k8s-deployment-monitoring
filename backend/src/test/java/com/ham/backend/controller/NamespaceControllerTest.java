package com.ham.backend.controller;

import com.ham.backend.service.NamespaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NamespaceController.class)
class NamespaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NamespaceService namespaceService;

    @Test
    void listNamespaces_ShouldReturnNamespaces() throws Exception {
        // Given: Mock된 네임스페이스 리스트
        List<String> mockNamespaces = List.of("default", "kube-system", "dev-namespace");
        when(namespaceService.listNamespaces()).thenReturn(mockNamespaces);

        // When & Then: GET 요청을 보내고 기대하는 결과 확인
        mockMvc.perform(get("/api/namespaces")) // 엔드포인트 호출
                .andExpect(status().isOk()) // HTTP 200 상태 확인
                .andExpect(content().contentType("application/json")) // 반환된 내용 타입 확인
                .andExpect(jsonPath("$[0]").value("default")) // JSON 데이터 검증
                .andExpect(jsonPath("$[1]").value("kube-system"))
                .andExpect(jsonPath("$[2]").value("dev-namespace"));
    }
}