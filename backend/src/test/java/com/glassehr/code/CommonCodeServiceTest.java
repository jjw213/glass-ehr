package com.glassehr.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeListItem;
import com.glassehr.code.CodeDtos.GroupCreateRequest;
import com.glassehr.code.CodeDtos.GroupUpdateRequest;
import com.glassehr.code.CodeDtos.UsagePage;
import com.glassehr.code.CodeDtos.UsageSummary;
import com.glassehr.code.CodeDtos.UsageTable;
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
    private static final String E = "C001";

    private CodeGroupRepository groups;
    private CommonCodeRepository codes;
    private CodeUsageRepository usages;
    private CommonCodeService service;

    @BeforeEach
    void setUp() {
        groups = mock(CodeGroupRepository.class);
        codes = mock(CommonCodeRepository.class);
        usages = mock(CodeUsageRepository.class);
        // UTC 00:00 = 서울 09:00 → 서울 기준 시각이 저장되는지 본다
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneId.of("UTC"));
        service = new CommonCodeService(groups, codes, usages, clock);
        when(codes.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CommonCode code(String group, String code, int sort, boolean active) {
        return new CommonCode(E, group, code, code + "명", null, sort, active, T0);
    }

    private static CodeGroup group(String g, boolean active) {
        return new CodeGroup(E, g, g + "명", null, 0, active, false, T0);
    }

    @Test
    void 관리_목록은_사용중지_포함_여부를_따른다() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, "JOB_GRADE"))
                .thenReturn(List.of(code("JOB_GRADE", "A", 1, true), code("JOB_GRADE", "B", 2, false)));

        assertThat(service.listCodes(E, "JOB_GRADE", true)).extracting(CodeListItem::code).containsExactly("A", "B");
        assertThat(service.listCodes(E, "JOB_GRADE", false)).extracting(CodeListItem::code).containsExactly("A");
    }

    @Test
    void 관리_목록은_참조_테이블별_건수를_코드마다_더한다() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, "JOB_GRADE"))
                .thenReturn(List.of(code("JOB_GRADE", "STAFF", 1, true), code("JOB_GRADE", "NEW", 2, true)));
        when(usages.countsByCode(E, ref("JOB_GRADE", "employee"))).thenReturn(Map.of("STAFF", 12L));
        when(usages.countsByCode(E, ref("JOB_GRADE", "hr_appointment"))).thenReturn(Map.of("STAFF", 3L));

        List<CodeListItem> list = service.listCodes(E, "JOB_GRADE", true);

        assertThat(list.get(0).inUse()).isTrue();
        assertThat(list.get(0).usageCount()).isEqualTo(15);
        assertThat(list.get(1).inUse()).isFalse();
        assertThat(list.get(1).usageCount()).isZero();
    }

    private static CodeReferences.Ref ref(String group, String table) {
        return CodeReferences.find(group, table).orElseThrow();
    }

    private void codeExists(String group, CommonCode c) {
        when(groups.existsByEnterCdAndGroupCode(E, group)).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeAndCode(E, group, c.getCode())).thenReturn(Optional.of(c));
    }

    @Test
    void 참조_없는_사용자_코드는_물리_삭제한다() {
        CommonCode c = code("JOB_GRADE", "UNUSED", 1, true);
        codeExists("JOB_GRADE", c);

        service.deleteCode(E, "JOB_GRADE", "UNUSED");

        verify(codes).delete(c);
    }

    @Test
    void 사원_발령이_참조하는_코드_삭제는_409이고_사용처_건수를_알려준다() {
        CommonCode c = code("JOB_GRADE", "STAFF", 1, true);
        codeExists("JOB_GRADE", c);
        when(usages.count(E, ref("JOB_GRADE", "employee"), "STAFF")).thenReturn(12L);
        when(usages.count(E, ref("JOB_GRADE", "hr_appointment"), "STAFF")).thenReturn(3L);

        assertThatThrownBy(() -> service.deleteCode(E, "JOB_GRADE", "STAFF"))
                .isInstanceOfSatisfying(CodeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(
                            "사용 중이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요 (사원 12건, 발령 이력 3건)");
                });
        verify(codes, never()).delete(any());
    }

    @Test
    void 기본_코드_삭제는_409() {
        CommonCode c = code("EMPLOYMENT_TYPE", "FIXED_TERM", 1, true).markSystem();
        codeExists("EMPLOYMENT_TYPE", c);

        assertThatThrownBy(() -> service.deleteCode(E, "EMPLOYMENT_TYPE", "FIXED_TERM"))
                .isInstanceOfSatisfying(CodeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("기본 코드").contains("사용 안 함");
                });
        verify(codes, never()).delete(any());
    }

    @Test
    void 없는_코드_삭제는_404() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeAndCode(any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteCode(E, "JOB_GRADE", "NOPE"))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 사용자_그룹을_추가한다_시스템_아님() {
        when(groups.existsByEnterCdAndGroupCode(E, "BENEFIT_TYPE")).thenReturn(false);
        when(groups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CodeGroupView v = service.createGroup(E, new GroupCreateRequest("BENEFIT_TYPE", " 복리후생 ", " ", 80, true));

        assertThat(v.groupCode()).isEqualTo("BENEFIT_TYPE");
        assertThat(v.name()).isEqualTo("복리후생");
        assertThat(v.description()).isNull();
        assertThat(v.system()).isFalse();
    }

    @Test
    void 같은_그룹코드_추가는_409() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        assertThatThrownBy(() -> service.createGroup(E, new GroupCreateRequest("JOB_GRADE", "직급", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(groups, never()).saveAndFlush(any());
    }

    @Test
    void 시스템_그룹은_이름_순서만_고치고_사용여부는_바꿀_수_없다() {
        CodeGroup g = new CodeGroup(E, "JOB_GRADE", "직급", null, 10, true, true, T0);
        when(groups.findByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(Optional.of(g));
        when(groups.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CodeGroupView v = service.updateGroup(E, "JOB_GRADE", new GroupUpdateRequest("직위", "설명", 5, true));
        assertThat(v.name()).isEqualTo("직위");
        assertThat(v.sortOrder()).isEqualTo(5);

        assertThatThrownBy(() -> service.updateGroup(E, "JOB_GRADE", new GroupUpdateRequest("직위", null, 5, false)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void 시스템_그룹_삭제는_409() {
        when(groups.findByEnterCdAndGroupCode(E, "JOB_GRADE"))
                .thenReturn(Optional.of(new CodeGroup(E, "JOB_GRADE", "직급", null, 10, true, true, T0)));
        assertThatThrownBy(() -> service.deleteGroup(E, "JOB_GRADE"))
                .isInstanceOfSatisfying(CodeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("시스템 그룹");
                });
        verify(groups, never()).delete(any());
    }

    @Test
    void 사용자_그룹은_코드와_함께_삭제한다() {
        CodeGroup g = group("BENEFIT_TYPE", true);
        when(groups.findByEnterCdAndGroupCode(E, "BENEFIT_TYPE")).thenReturn(Optional.of(g));
        List<CommonCode> list = List.of(code("BENEFIT_TYPE", "GIFT", 1, true));
        when(codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(E, "BENEFIT_TYPE")).thenReturn(list);

        service.deleteGroup(E, "BENEFIT_TYPE");

        verify(codes).deleteAll(list);
        verify(groups).delete(g);
    }

    @Test
    void 없는_그룹_삭제는_404() {
        when(groups.findByEnterCdAndGroupCode(E, "NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteGroup(E, "NOPE"))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 사용처는_건수가_있는_테이블만_보인다() {
        codeExists("JOB_GRADE", code("JOB_GRADE", "STAFF", 1, true));
        when(usages.count(E, ref("JOB_GRADE", "employee"), "STAFF")).thenReturn(12L);

        UsageSummary s = service.usages(E, "JOB_GRADE", "STAFF", false);

        assertThat(s.total()).isEqualTo(12);
        assertThat(s.tables()).extracting(UsageTable::label).containsExactly("사원");
        assertThat(s.rowsVisible()).isFalse();
    }

    @Test
    void 사용처_행_목록은_참조_목록에_있는_테이블만_받는다() {
        codeExists("JOB_GRADE", code("JOB_GRADE", "STAFF", 1, true));
        assertThatThrownBy(() -> service.usageRows(E, "JOB_GRADE", "STAFF", "app_user", 0))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(usages.count(E, ref("JOB_GRADE", "employee"), "STAFF")).thenReturn(21L);
        when(usages.rows(E, ref("JOB_GRADE", "employee"), "STAFF", 20, CommonCodeService.USAGE_PAGE_SIZE))
                .thenReturn(List.of(new CodeUsageRepository.UsageRow("S01-2026-021", "가짜 사원", null)));
        UsagePage p = service.usageRows(E, "JOB_GRADE", "STAFF", "employee", 1);
        assertThat(p.total()).isEqualTo(21);
        assertThat(p.rows()).hasSize(1);
        assertThat(p.dateLabel()).isEqualTo("입사일");
    }

    @Test
    void 없는_그룹_조회는_404() {
        when(groups.existsByEnterCdAndGroupCode(E, "NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.listCodes(E, "NOPE", true))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 여러_그룹_조회는_요청_순서이고_코드가_없는_그룹도_빈_목록으로_있다() {
        when(groups.findByEnterCdAndGroupCodeIn(any(), any()))
                .thenReturn(List.of(group("JOB_GRADE", true), group("EMPLOYMENT_TYPE", true)));
        when(codes.findByEnterCdAndGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(any(), any(), any()))
                .thenReturn(List.of(code("EMPLOYMENT_TYPE", "REGULAR", 10, true), code("EMPLOYMENT_TYPE", "FIXED_TERM", 20, true)));

        Map<String, List<CodeView>> r = service.activeCodes(E, List.of("EMPLOYMENT_TYPE", "JOB_GRADE", "EMPLOYMENT_TYPE"));

        assertThat(r.keySet()).containsExactly("EMPLOYMENT_TYPE", "JOB_GRADE");
        assertThat(r.get("EMPLOYMENT_TYPE")).extracting(CodeView::code).containsExactly("REGULAR", "FIXED_TERM");
        assertThat(r.get("JOB_GRADE")).isEmpty();
    }

    @Test
    void 사용중지된_그룹의_코드는_훅에_내보내지_않는다() {
        when(groups.findByEnterCdAndGroupCodeIn(any(), any())).thenReturn(List.of(group("WORK_LOCATION", false)));
        when(codes.findByEnterCdAndGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(any(), any(), any()))
                .thenReturn(List.of(code("WORK_LOCATION", "HQ", 10, true)));

        assertThat(service.activeCodes(E, List.of("WORK_LOCATION")).get("WORK_LOCATION")).isEmpty();
    }

    @Test
    void 여러_그룹_조회에_없는_그룹이_섞이면_404() {
        when(groups.findByEnterCdAndGroupCodeIn(any(), any())).thenReturn(List.of(group("JOB_GRADE", true)));
        assertThatThrownBy(() -> service.activeCodes(E, List.of("JOB_GRADE", "NOPE")))
                .isInstanceOfSatisfying(CodeException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).contains("NOPE");
                });
    }

    @Test
    void 여러_그룹_조회는_빈_요청과_너무_많은_그룹을_거부한다() {
        assertThatThrownBy(() -> service.activeCodes(E, Collections.emptyList()))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        List<String> many = IntStream.rangeClosed(0, CommonCodeService.MAX_GROUPS_PER_REQUEST).mapToObj(i -> "G" + i).toList();
        assertThatThrownBy(() -> service.activeCodes(E, many))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 추가는_이름을_다듬고_빈_설명은_null로_서울_시각으로_저장한다() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.existsByEnterCdAndGroupCodeAndCode(E, "JOB_GRADE", "INTERN")).thenReturn(false);

        CodeView v = service.create(E, "JOB_GRADE", new CodeCreateRequest("INTERN", "  인턴 ", "   ", 5, true));

        assertThat(v.name()).isEqualTo("인턴");
        assertThat(v.description()).isNull();
        assertThat(v.sortOrder()).isEqualTo(5);
        verify(codes).saveAndFlush(org.mockito.ArgumentMatchers.argThat(c -> c.getUpdatedAt().equals(T0)));
    }

    @Test
    void 중복_코드값_추가는_409이고_저장하지_않는다() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.existsByEnterCdAndGroupCodeAndCode(E, "JOB_GRADE", "STAFF")).thenReturn(true);

        assertThatThrownBy(() -> service.create(E, "JOB_GRADE", new CodeCreateRequest("STAFF", "사원", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        verify(codes, never()).saveAndFlush(any());
    }

    @Test
    void 없는_그룹에_추가는_404() {
        when(groups.existsByEnterCdAndGroupCode(E, "NOPE")).thenReturn(false);
        assertThatThrownBy(() -> service.create(E, "NOPE", new CodeCreateRequest("A", "에이", null, 1, true)))
                .isInstanceOfSatisfying(CodeException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void 수정은_코드값을_유지하고_속성만_바꾼다() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeAndCode(E, "JOB_GRADE", "STAFF"))
                .thenReturn(Optional.of(code("JOB_GRADE", "STAFF", 10, true)));

        CodeView v = service.update(E, "JOB_GRADE", "STAFF", new CodeUpdateRequest("사원(구)", "개편 전 직급", 99, false));

        assertThat(v.code()).isEqualTo("STAFF");
        assertThat(v.name()).isEqualTo("사원(구)");
        assertThat(v.description()).isEqualTo("개편 전 직급");
        assertThat(v.sortOrder()).isEqualTo(99);
        assertThat(v.active()).isFalse();
    }

    @Test
    void 없는_코드_수정은_404() {
        when(groups.existsByEnterCdAndGroupCode(E, "JOB_GRADE")).thenReturn(true);
        when(codes.findByEnterCdAndGroupCodeAndCode(any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(E, "JOB_GRADE", "NOPE", new CodeUpdateRequest("x", null, 1, true)))
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
        // Hibernate 부팅 시 CHECK 조건 생성에서 null 로 호출되므로 null 은 그대로 통과해야 한다(NOT NULL 은 DDL이 막는다).
        assertThat(c.convertToDatabaseColumn(null)).isNull();
    }
}
