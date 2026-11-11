package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CommonCodeServiceTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 12, 9, 0);

    private CodeGroupRepository groups;
    private CommonCodeRepository codes;
    private CommonCodeService service;

    @BeforeEach
    void setUp() {
        groups = mock(CodeGroupRepository.class);
        codes = mock(CommonCodeRepository.class);
        // UTC 00:00 = 서울 09:00 → 서울 기준 시각이 저장되는지 본다
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneId.of("UTC"));
        service = new CommonCodeService(groups, codes, clock);
        when(codes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CommonCode code(String group, String code, int sort, boolean active) {
        return new CommonCode(group, code, code + "명", null, sort, active, T0);
    }

    private static CodeGroup group(String g, boolean active) {
        return new CodeGroup(g, g + "명", null, 0, active);
    }

    @Test
    void 관리_목록은_사용중지_포함_여부를_따른다() {
        when(groups.existsById("JOB_GRADE")).thenReturn(true);
        when(codes.findByGroupCodeOrderBySortOrderAscCodeAsc("JOB_GRADE"))
                .thenReturn(List.of(code("JOB_GRADE", "A", 1, true), code("JOB_GRADE", "B", 2, false)));

        assertThat(service.listCodes("JOB_GRADE", true)).extracting(CodeView::code).containsExactly("A", "B");
        assertThat(service.listCodes("JOB_GRADE", false)).extracting(CodeView::code).containsExactly("A");
    }

    @Test
    void 없는_그룹_조회는_404() {
        when(groups.existsById("NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.listCodes("NOPE", true))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 여러_그룹_조회는_요청_순서이고_코드가_없는_그룹도_빈_목록으로_있다() {
        when(groups.findAllById(any())).thenReturn(List.of(group("JOB_GRADE", true), group("EMPLOYMENT_TYPE", true)));
        when(codes.findByGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(any(), any()))
                .thenReturn(List.of(code("EMPLOYMENT_TYPE", "REGULAR", 10, true), code("EMPLOYMENT_TYPE", "FIXED_TERM", 20, true)));

        Map<String, List<CodeView>> r = service.activeCodes(List.of("EMPLOYMENT_TYPE", "JOB_GRADE", "EMPLOYMENT_TYPE"));

        assertThat(r.keySet()).containsExactly("EMPLOYMENT_TYPE", "JOB_GRADE");
        assertThat(r.get("EMPLOYMENT_TYPE")).extracting(CodeView::code).containsExactly("REGULAR", "FIXED_TERM");
        assertThat(r.get("JOB_GRADE")).isEmpty();
    }

    @Test
    void 사용중지된_그룹의_코드는_훅에_내보내지_않는다() {
        when(groups.findAllById(any())).thenReturn(List.of(group("WORK_LOCATION", false)));
        when(codes.findByGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(any(), any()))
                .thenReturn(List.of(code("WORK_LOCATION", "HQ", 10, true)));

        assertThat(service.activeCodes(List.of("WORK_LOCATION")).get("WORK_LOCATION")).isEmpty();
    }

    @Test
    void 여러_그룹_조회에_없는_그룹이_섞이면_404() {
        when(groups.findAllById(any())).thenReturn(List.of(group("JOB_GRADE", true)));
        assertThatThrownBy(() -> service.activeCodes(List.of("JOB_GRADE", "NOPE")))
                .isInstanceOfSatisfying(CodeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).contains("NOPE");
                });
    }

    @Test
    void 여러_그룹_조회는_빈_요청과_너무_많은_그룹을_거부한다() {
        assertThatThrownBy(() -> service.activeCodes(Collections.emptyList()))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        List<String> many = IntStream.rangeClosed(0, CommonCodeService.MAX_GROUPS_PER_REQUEST).mapToObj(i -> "G" + i).toList();
        assertThatThrownBy(() -> service.activeCodes(many))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 추가는_이름을_다듬고_빈_설명은_null로_서울_시각으로_저장한다() {
        when(groups.existsById("JOB_GRADE")).thenReturn(true);
        when(codes.existsById(new CommonCodeId("JOB_GRADE", "INTERN"))).thenReturn(false);

        CodeView v = service.create("JOB_GRADE", new CodeCreateRequest("INTERN", "  인턴 ", "   ", 5, true));

        assertThat(v.name()).isEqualTo("인턴");
        assertThat(v.description()).isNull();
        assertThat(v.sortOrder()).isEqualTo(5);
        verify(codes).saveAndFlush(org.mockito.ArgumentMatchers.argThat(c -> c.getUpdatedAt().equals(T0)));
    }

    @Test
    void 중복_코드값_추가는_409이고_저장하지_않는다() {
        when(groups.existsById("JOB_GRADE")).thenReturn(true);
        when(codes.existsById(new CommonCodeId("JOB_GRADE", "STAFF"))).thenReturn(true);

        assertThatThrownBy(() -> service.create("JOB_GRADE", new CodeCreateRequest("STAFF", "사원", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(codes, never()).saveAndFlush(any());
    }

    @Test
    void 없는_그룹에_추가는_404() {
        when(groups.existsById("NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.create("NOPE", new CodeCreateRequest("A", "에이", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 수정은_코드값을_유지하고_속성만_바꾼다() {
        when(groups.existsById("JOB_GRADE")).thenReturn(true);
        when(codes.findById(new CommonCodeId("JOB_GRADE", "STAFF"))).thenReturn(Optional.of(code("JOB_GRADE", "STAFF", 10, true)));

        CodeView v = service.update("JOB_GRADE", "STAFF", new CodeUpdateRequest("사원(구)", "개편 전 직급", 99, false));

        assertThat(v.code()).isEqualTo("STAFF");
        assertThat(v.name()).isEqualTo("사원(구)");
        assertThat(v.description()).isEqualTo("개편 전 직급");
        assertThat(v.sortOrder()).isEqualTo(99);
        assertThat(v.active()).isFalse();
    }

    @Test
    void 없는_코드_수정은_404() {
        when(groups.existsById("JOB_GRADE")).thenReturn(true);
        when(codes.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("JOB_GRADE", "NOPE", new CodeUpdateRequest("x", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 사용여부_변환기는_Y_N만_받는다() {
        YnConverter c = new YnConverter();
        assertThat(c.convertToDatabaseColumn(true)).isEqualTo("Y");
        assertThat(c.convertToDatabaseColumn(false)).isEqualTo("N");
        assertThat(c.convertToEntityAttribute("Y")).isTrue();
        assertThat(c.convertToEntityAttribute("N")).isFalse();
        assertThatThrownBy(() -> c.convertToEntityAttribute("y")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> c.convertToDatabaseColumn(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
