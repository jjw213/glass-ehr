package com.glassehr.code;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeListItem;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import com.glassehr.code.CodeDtos.GroupCreateRequest;
import com.glassehr.code.CodeDtos.GroupUpdateRequest;
import com.glassehr.code.CodeDtos.UsagePage;
import com.glassehr.code.CodeDtos.UsageSummary;
import com.glassehr.code.CodeDtos.UsageTable;
import com.glassehr.code.CodeReferences.Ref;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공통코드 조회·추가·수정·삭제(#140).
 * 삭제 규칙(docs/common-code-design.md): 시스템 그룹·기본 코드는 삭제 불가, 코드는 참조 목록({@link CodeReferences})의
 * 어떤 테이블에서도 참조하지 않을 때만 물리 삭제한다. 참조 중이면 409 로 막고 사용 안 함으로 바꾸게 안내한다.
 */
@Service
@Transactional(readOnly = true)
public class CommonCodeService {

    /** 한 번에 조회할 수 있는 그룹 수. 화면 하나가 쓰는 그룹 수보다 넉넉하게 둔다. */
    static final int MAX_GROUPS_PER_REQUEST = 20;

    /** 사용처 행 목록 한 페이지 크기. */
    static final int USAGE_PAGE_SIZE = 20;

    static final String IN_USE_MESSAGE = "사용 중이라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요";

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CodeGroupRepository groups;
    private final CommonCodeRepository codes;
    private final CodeUsageRepository usages;
    private final Clock clock;

    @Autowired
    public CommonCodeService(CodeGroupRepository groups, CommonCodeRepository codes, CodeUsageRepository usages) {
        this(groups, codes, usages, Clock.system(SEOUL));
    }

    CommonCodeService(CodeGroupRepository groups, CommonCodeRepository codes, CodeUsageRepository usages, Clock clock) {
        this.groups = groups;
        this.codes = codes;
        this.usages = usages;
        this.clock = clock.withZone(SEOUL);
    }

    /*
     * 모든 메서드의 enterCd 는 로그인 세션의 현재 법인(AuthUser.enterCd)이다. 요청 본문·쿼리로 받지 않는다.
     * 다른 법인의 그룹·코드는 없는 것과 같게 404 로 답한다(docs/tenant-design.md 5.2).
     */

    public List<CodeGroupView> listGroups(String enterCd) {
        return groups.findByEnterCdOrderBySortOrderAscGroupCodeAsc(enterCd).stream().map(CodeGroupView::of).toList();
    }

    /** 관리 화면용. includeInactive=false 면 사용 중인 코드만. 코드마다 참조 건수를 붙인다. */
    public List<CodeListItem> listCodes(String enterCd, String groupCode, boolean includeInactive) {
        requireGroup(enterCd, groupCode);
        Map<String, Long> counts = usageCounts(enterCd, groupCode);
        return codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(enterCd, groupCode).stream()
                .filter(c -> includeInactive || c.isActive())
                .map(c -> CodeListItem.of(c, counts.getOrDefault(c.getCode(), 0L)))
                .toList();
    }

    /** 코드별 참조 건수 합(참조 목록의 모든 테이블). 근태코드 목록도 쓴다(#143). */
    Map<String, Long> usageCounts(String enterCd, String groupCode) {
        Map<String, Long> counts = new HashMap<>();
        for (Ref ref : CodeReferences.of(groupCode)) {
            usages.countsByCode(enterCd, ref).forEach((code, n) -> counts.merge(code, n, Long::sum));
        }
        return counts;
    }

    /**
     * 화면 공통 훅(useCodes)용. 여러 그룹의 사용 중인 코드를 그룹별로 돌려준다.
     * 요청한 그룹이 모두 있어야 하며, 결과 맵은 요청 순서를 따른다. 사용 중지된 그룹은 빈 목록이다.
     */
    public Map<String, List<CodeView>> activeCodes(String enterCd, Collection<String> groupCodes) {
        Set<String> wanted = new LinkedHashSet<>(groupCodes);
        if (wanted.isEmpty()) {
            throw CodeException.badRequest("조회할 코드그룹을 하나 이상 지정해 주세요");
        }
        if (wanted.size() > MAX_GROUPS_PER_REQUEST) {
            throw CodeException.badRequest("코드그룹은 한 번에 " + MAX_GROUPS_PER_REQUEST + "개까지 조회할 수 있어요");
        }
        Map<String, CodeGroup> found = new LinkedHashMap<>();
        for (CodeGroup g : groups.findByEnterCdAndGroupCodeIn(enterCd, wanted)) {
            found.put(g.getGroupCode(), g);
        }
        Map<String, List<CodeView>> result = new LinkedHashMap<>();
        for (String g : wanted) {
            if (!found.containsKey(g)) {
                throw CodeException.groupNotFound(g);
            }
            result.put(g, new ArrayList<>());
        }
        for (CommonCode c : codes.findByEnterCdAndGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(
                enterCd, wanted, Boolean.TRUE)) {
            if (found.get(c.getGroupCode()).isActive()) {
                result.get(c.getGroupCode()).add(CodeView.of(c));
            }
        }
        result.replaceAll((k, v) -> List.copyOf(v));
        return result;
    }

    /** 사용자 그룹 추가. 같은 법인에 같은 그룹코드가 있으면 409. */
    @Transactional
    public CodeGroupView createGroup(String enterCd, GroupCreateRequest req) {
        if (groups.existsByEnterCdAndGroupCode(enterCd, req.groupCode())) {
            throw CodeException.conflict("이미 있는 그룹코드예요: " + req.groupCode());
        }
        CodeGroup g = new CodeGroup(enterCd, req.groupCode(), req.name().strip(), blankToNull(req.description()),
                req.sortOrder(), req.active(), false, now());
        return CodeGroupView.of(groups.saveAndFlush(g));
    }

    /** 그룹 이름·설명·순서·사용 여부 수정. 시스템 그룹은 사용 여부를 바꿀 수 없다(로직이 그룹을 쓴다). */
    @Transactional
    public CodeGroupView updateGroup(String enterCd, String groupCode, GroupUpdateRequest req) {
        CodeGroup g = findGroup(enterCd, groupCode);
        requireEditableHere(g);
        if (g.isSystem() && g.isActive() != req.active()) {
            throw CodeException.conflict("시스템 그룹은 사용 여부를 바꿀 수 없어요. 이름·설명·순서만 고칠 수 있어요");
        }
        g.change(req.name().strip(), blankToNull(req.description()), req.sortOrder(), req.active(), now());
        return CodeGroupView.of(groups.saveAndFlush(g));
    }

    /** 사용자 그룹 삭제. 그룹 안 코드가 하나라도 참조 중이면 409, 아니면 코드와 함께 지운다. */
    @Transactional
    public void deleteGroup(String enterCd, String groupCode) {
        CodeGroup g = findGroup(enterCd, groupCode);
        if (g.isSystem()) {
            throw CodeException.conflict("시스템 그룹이라 삭제할 수 없어요. 사용하지 않는 코드는 사용 안 함으로 바꿔 주세요");
        }
        List<CommonCode> list = codes.findByEnterCdAndGroupCodeOrderBySortOrderAscCodeAsc(enterCd, groupCode);
        for (CommonCode c : list) {
            if (c.isSystem()) {
                throw CodeException.conflict("기본 코드(" + c.getCode() + ")가 있어 그룹을 삭제할 수 없어요");
            }
            List<UsageTable> used = usageTables(enterCd, groupCode, c.getCode());
            if (!used.isEmpty()) {
                throw CodeException.conflict("그룹 안의 코드(" + c.getCode() + ")가 " + IN_USE_MESSAGE + " ("
                        + describe(used) + ")");
            }
        }
        codes.deleteAll(list);
        codes.flush();
        groups.delete(g);
        groups.flush();
    }

    @Transactional
    public CodeView create(String enterCd, String groupCode, CodeCreateRequest req) {
        requireGroup(enterCd, groupCode);
        requireCodesEditableHere(enterCd, groupCode);
        if (codes.existsByEnterCdAndGroupCodeAndCode(enterCd, groupCode, req.code())) {
            throw CodeException.duplicate(groupCode, req.code());
        }
        String sysType = requireSysType(groupCode, req.sysType());
        CommonCode c = new CommonCode(enterCd, groupCode, req.code(), req.name().strip(), blankToNull(req.description()),
                req.sortOrder(), req.active(), sysType, now());
        return CodeView.of(codes.saveAndFlush(c));
    }

    @Transactional
    public CodeView update(String enterCd, String groupCode, String code, CodeUpdateRequest req) {
        requireCodesEditableHere(enterCd, groupCode);
        CommonCode c = findCode(enterCd, groupCode, code);
        String sysType = requireSysType(groupCode, req.sysType());
        if (c.isSystem() && !Objects.equals(sysType, c.getProcessType())) {
            throw CodeException.conflict("기본 코드는 처리방식을 바꿀 수 없어요");
        }
        c.change(req.name().strip(), blankToNull(req.description()), req.sortOrder(), req.active(), now());
        c.changeProcessType(sysType);
        return CodeView.of(codes.saveAndFlush(c));
    }

    /** 그룹의 처리방식 목록(#141). 처리방식이 없는 그룹은 빈 목록. 다른 법인·없는 그룹은 404. */
    public List<CodeSysTypes.SysType> sysTypes(String enterCd, String groupCode) {
        requireGroup(enterCd, groupCode);
        return CodeSysTypes.of(groupCode);
    }

    /**
     * 처리방식이 있는 그룹(고용형태·퇴직사유)은 목록 안의 값이 꼭 있어야 하고, 다른 그룹은 처리방식을 두지 않는다
     * (DB CHECK ck_common_code_process, V047). 근태코드 그룹은 공통코드 API 로 고치지 않으므로 여기 오지 않는다.
     */
    private static String requireSysType(String groupCode, String sysType) {
        String t = blankToNull(sysType);
        if (!CodeSysTypes.required(groupCode)) {
            if (t != null) {
                throw CodeException.badRequest("이 코드그룹은 처리방식을 쓰지 않아요");
            }
            return null;
        }
        if (t == null) {
            throw CodeException.badRequest("처리방식을 선택해 주세요");
        }
        if (!CodeSysTypes.allowed(groupCode, t)) {
            throw CodeException.badRequest("이 그룹에 없는 처리방식이에요: " + t);
        }
        return t;
    }

    /**
     * 코드 삭제. (1) 기본 코드는 409 (2) 참조 중이면 409 + 사용처 건수 (3) 아니면 물리 삭제.
     * 참조 검사와 삭제 사이에 다른 요청이 이 코드를 쓰는 행을 넣는 경우는 막지 않는다(코드 컬럼에 FK 없음, 문서 남은 일).
     */
    @Transactional
    public void deleteCode(String enterCd, String groupCode, String code) {
        requireCodesEditableHere(enterCd, groupCode);
        CommonCode c = findCode(enterCd, groupCode, code);
        requireDeletable(enterCd, c);
        codes.delete(c);
        codes.flush();
    }

    /** 코드 삭제 규칙: 기본 코드·참조 중이면 409. 근태코드 삭제도 같은 규칙을 쓴다(#143). */
    void requireDeletable(String enterCd, CommonCode c) {
        if (c.isSystem()) {
            throw CodeException.conflict("기본 코드라 삭제할 수 없어요. 사용 안 함으로 바꿔 주세요");
        }
        List<UsageTable> used = usageTables(enterCd, c.getGroupCode(), c.getCode());
        if (!used.isEmpty()) {
            throw CodeException.conflict(IN_USE_MESSAGE + " (" + describe(used) + ")");
        }
    }

    /**
     * 다른 화면에서 편집하는 그룹(edit_page, 예: 근태코드)은 공통코드 API 로 고치지 않는다(#143).
     * 그 화면이 처리방식·상세 설정을 함께 저장하기 때문이다. 조회·사용처 보기는 된다.
     */
    private void requireCodesEditableHere(String enterCd, String groupCode) {
        if (groups.existsByEnterCdAndGroupCodeAndEditPageIsNotNull(enterCd, groupCode)) {
            throw editPageConflict();
        }
    }

    private static void requireEditableHere(CodeGroup g) {
        if (g.getEditPage() != null) {
            throw editPageConflict();
        }
    }

    private static CodeException editPageConflict() {
        return CodeException.conflict("이 코드그룹은 공통코드 화면에서 고칠 수 없어요. 전용 화면에서 편집해 주세요");
    }

    /** 사용처 요약(테이블별 건수). 건수가 0인 테이블은 뺀다. */
    public UsageSummary usages(String enterCd, String groupCode, String code, boolean rowsVisible) {
        findCode(enterCd, groupCode, code);
        List<UsageTable> tables = usageTables(enterCd, groupCode, code);
        long total = tables.stream().mapToLong(UsageTable::count).sum();
        return new UsageSummary(groupCode, code, total, tables, rowsVisible);
    }

    /** 사용처 행 목록. 호출하는 쪽이 EMP_READ_ALL 을 확인한다(사번·이름). */
    public UsagePage usageRows(String enterCd, String groupCode, String code, String table, int page) {
        findCode(enterCd, groupCode, code);
        if (page < 0) {
            throw CodeException.badRequest("페이지는 0 이상이어야 해요");
        }
        Ref ref = CodeReferences.find(groupCode, table)
                .orElseThrow(() -> CodeException.badRequest("이 코드그룹을 참조하지 않는 테이블이에요: " + table));
        long total = usages.count(enterCd, ref, code);
        long offset = (long) page * USAGE_PAGE_SIZE;
        var rows = offset >= total ? List.<CodeUsageRepository.UsageRow>of()
                : usages.rows(enterCd, ref, code, (int) offset, USAGE_PAGE_SIZE);
        return new UsagePage(ref.table(), ref.label(), ref.dateLabel(), total, page, USAGE_PAGE_SIZE, rows);
    }

    private List<UsageTable> usageTables(String enterCd, String groupCode, String code) {
        List<UsageTable> result = new ArrayList<>();
        for (Ref ref : CodeReferences.of(groupCode)) {
            long n = usages.count(enterCd, ref, code);
            if (n > 0) {
                result.add(new UsageTable(ref.table(), ref.label(), n));
            }
        }
        return result;
    }

    private static String describe(List<UsageTable> used) {
        return used.stream().map(u -> u.label() + " " + u.count() + "건").collect(Collectors.joining(", "));
    }

    CodeGroup findGroup(String enterCd, String groupCode) {
        return groups.findByEnterCdAndGroupCode(enterCd, groupCode)
                .orElseThrow(() -> CodeException.groupNotFound(groupCode));
    }

    CommonCode findCode(String enterCd, String groupCode, String code) {
        requireGroup(enterCd, groupCode);
        return codes.findByEnterCdAndGroupCodeAndCode(enterCd, groupCode, code)
                .orElseThrow(() -> CodeException.codeNotFound(groupCode, code));
    }

    private void requireGroup(String enterCd, String groupCode) {
        if (!groups.existsByEnterCdAndGroupCode(enterCd, groupCode)) {
            throw CodeException.groupNotFound(groupCode);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
