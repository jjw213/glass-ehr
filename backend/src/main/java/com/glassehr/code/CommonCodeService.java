package com.glassehr.code;

import com.glassehr.code.CodeDtos.CodeCreateRequest;
import com.glassehr.code.CodeDtos.CodeGroupView;
import com.glassehr.code.CodeDtos.CodeUpdateRequest;
import com.glassehr.code.CodeDtos.CodeView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공통코드 조회·추가·수정. 삭제는 하지 않는다(사용 여부로 끈다). */
@Service
@Transactional(readOnly = true)
public class CommonCodeService {

    /** 한 번에 조회할 수 있는 그룹 수. 화면 하나가 쓰는 그룹 수보다 넉넉하게 둔다. */
    static final int MAX_GROUPS_PER_REQUEST = 20;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final CodeGroupRepository groups;
    private final CommonCodeRepository codes;
    private final Clock clock;

    @Autowired
    public CommonCodeService(CodeGroupRepository groups, CommonCodeRepository codes) {
        this(groups, codes, Clock.system(SEOUL));
    }

    CommonCodeService(CodeGroupRepository groups, CommonCodeRepository codes, Clock clock) {
        this.groups = groups;
        this.codes = codes;
        this.clock = clock.withZone(SEOUL);
    }

    public List<CodeGroupView> listGroups() {
        return groups.findAllByOrderBySortOrderAscGroupCodeAsc().stream().map(CodeGroupView::of).toList();
    }

    /** 관리 화면용. includeInactive=false 면 사용 중인 코드만. */
    public List<CodeView> listCodes(String groupCode, boolean includeInactive) {
        requireGroup(groupCode);
        return codes.findByGroupCodeOrderBySortOrderAscCodeAsc(groupCode).stream()
                .filter(c -> includeInactive || c.isActive())
                .map(CodeView::of)
                .toList();
    }

    /**
     * 화면 공통 훅(useCodes)용. 여러 그룹의 사용 중인 코드를 그룹별로 돌려준다.
     * 요청한 그룹이 모두 있어야 하며, 결과 맵은 요청 순서를 따른다. 사용 중지된 그룹은 빈 목록이다.
     */
    public Map<String, List<CodeView>> activeCodes(Collection<String> groupCodes) {
        Set<String> wanted = new LinkedHashSet<>(groupCodes);
        if (wanted.isEmpty()) {
            throw CodeException.badRequest("조회할 코드그룹을 하나 이상 지정해 주세요");
        }
        if (wanted.size() > MAX_GROUPS_PER_REQUEST) {
            throw CodeException.badRequest("코드그룹은 한 번에 " + MAX_GROUPS_PER_REQUEST + "개까지 조회할 수 있어요");
        }
        Map<String, CodeGroup> found = new LinkedHashMap<>();
        for (CodeGroup g : groups.findAllById(wanted)) {
            found.put(g.getGroupCode(), g);
        }
        Map<String, List<CodeView>> result = new LinkedHashMap<>();
        for (String g : wanted) {
            if (!found.containsKey(g)) {
                throw CodeException.groupNotFound(g);
            }
            result.put(g, new ArrayList<>());
        }
        for (CommonCode c : codes.findByGroupCodeInAndActiveOrderByGroupCodeAscSortOrderAscCodeAsc(wanted, Boolean.TRUE)) {
            if (found.get(c.getGroupCode()).isActive()) {
                result.get(c.getGroupCode()).add(CodeView.of(c));
            }
        }
        result.replaceAll((k, v) -> List.copyOf(v));
        return result;
    }

    @Transactional
    public CodeView create(String groupCode, CodeCreateRequest req) {
        requireGroup(groupCode);
        if (codes.existsById(new CommonCodeId(groupCode, req.code()))) {
            throw CodeException.duplicate(groupCode, req.code());
        }
        CommonCode c = new CommonCode(groupCode, req.code(), req.name().strip(), blankToNull(req.description()),
                req.sortOrder(), req.active(), now());
        return CodeView.of(codes.saveAndFlush(c));
    }

    @Transactional
    public CodeView update(String groupCode, String code, CodeUpdateRequest req) {
        requireGroup(groupCode);
        CommonCode c = codes.findById(new CommonCodeId(groupCode, code))
                .orElseThrow(() -> CodeException.codeNotFound(groupCode, code));
        c.change(req.name().strip(), blankToNull(req.description()), req.sortOrder(), req.active(), now());
        return CodeView.of(codes.saveAndFlush(c));
    }

    private void requireGroup(String groupCode) {
        if (!groups.existsById(groupCode)) {
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
