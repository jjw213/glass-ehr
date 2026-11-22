package com.glassehr.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.glassehr.auth.TestAuth;
import com.glassehr.message.MessageDtos.Member;
import com.glassehr.message.MessageDtos.MessagePage;
import com.glassehr.message.MessageDtos.MessageView;
import com.glassehr.message.MessageDtos.ThreadSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 메시지 API 응답 모양·상태 코드. 참여 여부 검사는 MessageServiceTest·MessageOracleTest. */
@WebMvcTest(MessageController.class)
@Import(TestAuth.class)
class MessageControllerTest {

    private static final LocalDateTime T = LocalDateTime.of(2099, 3, 2, 9, 30);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MessageService service;

    @Test
    void 대화방_목록은_상대와_안_읽은_수를_준다() throws Exception {
        when(service.threads(any())).thenReturn(List.of(
                new ThreadSummary(3, List.of(new Member(7, "예시사원07", 20L)), "안녕하세요", T, 2)));
        mvc.perform(get("/api/threads").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].members[0].name").value("예시사원07"))
                .andExpect(jsonPath("$[0].members[0].deptId").value(20))
                .andExpect(jsonPath("$[0].preview").value("안녕하세요"))
                .andExpect(jsonPath("$[0].lastSentAt").value("2099-03-02T09:30:00"))
                .andExpect(jsonPath("$[0].unread").value(2));
    }

    @Test
    void 메시지_목록은_페이지_인자를_넘긴다() throws Exception {
        when(service.messages(any(), eq(3L), eq(40L), eq(20)))
                .thenReturn(new MessagePage(List.of(new MessageView(39, 5, true, "보냄", T)), true));
        mvc.perform(get("/api/threads/3/messages?beforeId=40&size=20").header("X-Actor-Id", "emp1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages[0].id").value(39))
                .andExpect(jsonPath("$.messages[0].mine").value(true))
                .andExpect(jsonPath("$.messages[0].text").value("보냄"))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void 보내기는_201과_보낸_메시지() throws Exception {
        when(service.send(any(), eq(3L), eq("안녕"))).thenReturn(new MessageView(41, 5, true, "안녕", T));
        mvc.perform(post("/api/threads/3/messages").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"안녕\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.sentAt").value("2099-03-02T09:30:00"));
    }

    @Test
    void 읽음은_204() throws Exception {
        mvc.perform(post("/api/threads/3/read").header("X-Actor-Id", "emp1"))
                .andExpect(status().isNoContent());
        verify(service).markRead(any(), eq(3L));
    }

    @Test
    void 서비스_오류는_error_본문으로() throws Exception {
        doThrow(MessageException.forbidden()).when(service).markRead(any(), anyLong());
        mvc.perform(post("/api/threads/9/read").header("X-Actor-Id", "emp1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("이 대화에 참여하고 있지 않아요"));
        when(service.send(any(), anyLong(), any())).thenThrow(MessageException.badRequest("메시지가 비어 있어요"));
        mvc.perform(post("/api/threads/9/messages").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("메시지가 비어 있어요"));
    }

    @Test
    void 숫자가_아닌_대화방_ID와_빈_본문은_400() throws Exception {
        mvc.perform(get("/api/threads/TH-1/messages").header("X-Actor-Id", "emp1"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/threads/3/messages").header("X-Actor-Id", "emp1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verify(service, never()).send(any(), anyLong(), any());
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mvc.perform(get("/api/threads").header("X-Actor-Id", "nobody"))
                .andExpect(status().isUnauthorized());
        verify(service, never()).threads(any());
    }
}
