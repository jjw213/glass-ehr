package com.glassehr.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.auth.AuthUser;
import com.glassehr.message.MessageDtos.Member;
import com.glassehr.message.MessageRepository.MessageRow;
import com.glassehr.message.MessageRepository.ThreadRow;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MessageServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2099, 3, 2, 9, 30);
    private static final String C = "C001";
    private static final AuthUser ME = user(5L);
    private static final AuthUser NO_EMP = user(null);

    private final MessageRepository repo = mock(MessageRepository.class);
    private final MessageService service = new MessageService(repo,
            Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

    private static AuthUser user(Long empId) {
        return new AuthUser("T001", "C001", "C001", "u", empId, Set.of("EMPLOYEE"), Set.of(), Set.of(), "127.0.0.1");
    }

    private void member(long thread) {
        when(repo.threadExists(C, thread)).thenReturn(true);
        when(repo.isMember(C, thread, 5L)).thenReturn(true);
    }

    @Test
    void 대화방_목록에_상대와_안_읽은_수를_붙인다() {
        when(repo.othersInThreadsOf(C, 5L)).thenReturn(Map.of(1L, List.of(new Member(7, "예시사원07", 20L))));
        when(repo.threadsOf(C, 5L)).thenReturn(List.of(new ThreadRow(1, "안녕", NOW, 3), new ThreadRow(2, null, null, 0)));
        assertThat(service.threads(ME)).satisfiesExactly(
                t -> {
                    assertThat(t.members()).extracting(Member::name).containsExactly("예시사원07");
                    assertThat(t.unread()).isEqualTo(3);
                },
                t -> {
                    assertThat(t.preview()).isEmpty();
                    assertThat(t.members()).isEmpty();
                });
    }

    @Test
    void 사원과_연결되지_않은_계정은_대화방이_없고_대화방_API는_403() {
        assertThat(service.threads(NO_EMP)).isEmpty();
        when(repo.threadExists(C, 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.messages(NO_EMP, 1, null, null)).isInstanceOfSatisfying(
                MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void 참여자가_아니면_읽기_보내기_읽음_모두_403() {
        when(repo.threadExists(C, 1L)).thenReturn(true);
        when(repo.isMember(C, 1L, 5L)).thenReturn(false);
        for (Runnable r : List.<Runnable>of(
                () -> service.messages(ME, 1, null, null),
                () -> service.send(ME, 1, "안녕"),
                () -> service.markRead(ME, 1))) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(
                    MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(repo, never()).insertMessage(anyString(), anyLong(), anyLong(), anyString(), any());
        verify(repo, never()).markRead(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void 없는_대화방은_404() {
        assertThatThrownBy(() -> service.send(ME, 99, "안녕")).isInstanceOfSatisfying(
                MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 다른_법인_대화방은_세션_법인으로만_찾아_404() {
        // 같은 대화방 번호가 다른 법인에 있어도 세션 법인(C001)으로만 찾는다.
        when(repo.threadExists("ZZIT1", 1L)).thenReturn(true);
        when(repo.isMember("ZZIT1", 1L, 5L)).thenReturn(true);
        for (Runnable r : List.<Runnable>of(
                () -> service.messages(ME, 1, null, null),
                () -> service.send(ME, 1, "안녕"),
                () -> service.markRead(ME, 1))) {
            assertThatThrownBy(r::run).isInstanceOfSatisfying(
                    MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        }
        verify(repo, never()).insertMessage(anyString(), anyLong(), anyLong(), anyString(), any());
        verify(repo, never()).markRead(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void 빈_메시지와_공백만은_거부한다() {
        member(1);
        for (String text : new String[] {null, "", "   ", "\n\t"}) {
            assertThatThrownBy(() -> service.send(ME, 1, text)).isInstanceOfSatisfying(MessageException.class, e -> {
                assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getMessage()).isEqualTo("메시지가 비어 있어요");
            });
        }
        verify(repo, never()).insertMessage(anyString(), anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void 본문은_앞뒤_공백을_빼고_1000자까지() {
        member(1);
        String max = "가".repeat(1000);
        when(repo.insertMessage(C, 1L, 5L, max, NOW)).thenReturn(10L);
        assertThat(service.send(ME, 1, "  " + max + "  ")).satisfies(v -> {
            assertThat(v.id()).isEqualTo(10L);
            assertThat(v.mine()).isTrue();
            assertThat(v.text()).isEqualTo(max);
            assertThat(v.sentAt()).isEqualTo(NOW);
        });
        assertThatThrownBy(() -> service.send(ME, 1, max + "a")).isInstanceOfSatisfying(MessageException.class, e -> {
            assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(e.getMessage()).contains("1000자");
        });
    }

    @Test
    void 이모지는_한_글자로_센다() {
        member(1);
        String emoji = "😀".repeat(1000); // UTF-16 으로는 2000
        when(repo.insertMessage(eq(C), eq(1L), eq(5L), eq(emoji), any())).thenReturn(11L);
        assertThat(service.send(ME, 1, emoji).id()).isEqualTo(11L);
    }

    @Test
    void 메시지_페이지는_오래된_것부터_다음_페이지_여부와_함께() {
        member(1);
        when(repo.messagesBefore(C, 1L, 50L, 3)).thenReturn(List.of(
                new MessageRow(49, 7, "c", NOW), new MessageRow(48, 5, "b", NOW), new MessageRow(47, 7, "a", NOW)));
        var page = service.messages(ME, 1, 50L, 2);
        assertThat(page.hasMore()).isTrue();
        assertThat(page.messages()).extracting(MessageDtos.MessageView::id).containsExactly(48L, 49L);
        assertThat(page.messages()).extracting(MessageDtos.MessageView::mine).containsExactly(true, false);

        when(repo.messagesBefore(C, 1L, null, MessageService.DEFAULT_PAGE_SIZE + 1))
                .thenReturn(List.of(new MessageRow(1, 7, "a", NOW)));
        assertThat(service.messages(ME, 1, null, null).hasMore()).isFalse();
    }

    @Test
    void 페이지_크기는_1에서_200() {
        member(1);
        for (int size : new int[] {0, 201}) {
            assertThatThrownBy(() -> service.messages(ME, 1, null, size)).isInstanceOfSatisfying(
                    MessageException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void 읽음_처리는_지금_시각으로() {
        member(1);
        service.markRead(ME, 1);
        verify(repo).markRead(C, 1L, 5L, NOW);
    }
}
