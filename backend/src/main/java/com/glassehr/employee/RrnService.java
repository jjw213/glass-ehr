package com.glassehr.employee;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주민등록번호 저장·마스킹 조회·원문 조회. docs/rrn-design.md.
 *
 * <ul>
 *   <li>개인정보 보호법 제24조의2: 주민등록번호는 법령 근거가 있을 때만 처리한다. 저장 목적은 4대보험 신고·원천징수
 *       (처리 근거 개별 조문은 docs/rrn-design.md 6장 미결 — 사람 확인 대기).</li>
 *   <li>개인정보 보호법 제24조의2 제2항·제29조: 암호화해 저장하고 접근을 통제한다. 원문 조회는 인사·급여 담당자만,
 *       조회할 때마다(거부·대상 없음 포함) 로그를 남긴다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class RrnService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EmpRrnRepository rrns;
    private final RrnCrypto crypto;
    private final RrnAccessLogger accessLogger;
    private final Clock clock;

    @Autowired
    public RrnService(EmpRrnRepository rrns, RrnCrypto crypto, RrnAccessLogger accessLogger) {
        this(rrns, crypto, accessLogger, Clock.system(SEOUL));
    }

    RrnService(EmpRrnRepository rrns, RrnCrypto crypto, RrnAccessLogger accessLogger, Clock clock) {
        this.rrns = rrns;
        this.crypto = crypto;
        this.accessLogger = accessLogger;
        this.clock = clock.withZone(SEOUL);
    }

    /**
     * 이 법인의 어떤 사원에게 이미 등록된 번호인가(일괄 등록 미리보기용). 누구인지는 돌려주지 않는다.
     * 중복 검사는 법인 안에서만 한다: 전적은 퇴직 + 다른 법인 신규 입사라 같은 사람이 두 법인에 있을 수 있다
     * (docs/tenant-design.md 3.2).
     */
    public boolean isRegistered(String enterCd, Rrn rrn) {
        return rrns.existsByEnterCdAndRrnHash(enterCd, crypto.hash(rrn));
    }

    /**
     * 등록하거나 정정한다. enterCd 는 그 사원의 법인(호출자가 이 법인 사원인지 먼저 확인한다).
     * 같은 법인의 다른 사원에게 같은 번호가 있으면 409(누구인지는 알리지 않음).
     */
    @Transactional
    public String save(String enterCd, long empId, Rrn rrn, RequestActor actor) {
        byte[] hash = crypto.hash(rrn);
        if (rrns.existsByEnterCdAndRrnHashAndEmpIdNot(enterCd, hash, empId)) {
            throw EmployeeException.conflict("이미 다른 사원에게 등록된 주민등록번호예요");
        }
        RrnCrypto.Sealed sealed = crypto.encrypt(rrn, empId);
        LocalDateTime now = LocalDateTime.now(clock);
        EmpRrn row = rrns.findByEnterCdAndEmpId(enterCd, empId).orElse(null);
        if (row == null) {
            row = new EmpRrn(enterCd, empId, sealed, rrn.masked(), hash, actor.actorId(), now);
        } else {
            row.replace(sealed, rrn.masked(), hash, actor.actorId(), now);
        }
        rrns.saveAndFlush(row);
        return rrn.masked();
    }

    /** 화면 표시용 마스킹 값. 복호화하지 않는다. */
    public Optional<String> masked(String enterCd, long empId) {
        return rrns.findByEnterCdAndEmpId(enterCd, empId).map(EmpRrn::getRrnMasked);
    }

    /**
     * 원문 조회. 인사담당·급여담당만 허용하고, 결과와 관계없이 한 건마다 로그 한 행을 남긴다.
     * 로그는 원문을 돌려주기 전에 커밋한다(로그를 못 남기면 원문도 주지 않음).
     * enterCd 는 세션의 현재 법인(로그의 대상 법인). 다른 법인 사원은 등록된 번호가 없는 것과 같은 404다(#123).
     */
    public Rrn reveal(String enterCd, long empId, RequestActor actor, RrnAccessLog.Purpose purpose) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!actor.canRevealRrn()) {
            accessLogger.log(enterCd, actor, empId, purpose, RrnAccessLog.Result.DENIED, now);
            throw EmployeeException.forbidden("주민등록번호 원문은 인사·급여 담당자만 볼 수 있어요");
        }
        EmpRrn row = rrns.findByEnterCdAndEmpId(enterCd, empId).orElse(null);
        if (row == null) {
            accessLogger.log(enterCd, actor, empId, purpose, RrnAccessLog.Result.NOT_FOUND, now);
            throw new EmployeeException(org.springframework.http.HttpStatus.NOT_FOUND, "등록된 주민등록번호가 없어요");
        }
        Rrn rrn = crypto.decrypt(row.getRrnEnc(), row.getKeyVersion(), empId);
        accessLogger.log(enterCd, actor, empId, purpose, RrnAccessLog.Result.SUCCESS, now);
        return rrn;
    }
}
