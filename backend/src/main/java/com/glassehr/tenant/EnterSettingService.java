package com.glassehr.tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 법인별 회사 설정(#131, docs/tenant-design.md 7장). 계산·판정은 이 서비스로 그 법인·그 날짜의 값을 읽는다.
 * <ul>
 *   <li>법인 값: enter_setting 에서 적용시작일 <= 날짜 인 행 중 가장 늦은 것.</li>
 *   <li>법인 값이 없으면 application.yml({@link EnterSettingKey#property}) 값. 전역 값은 새 법인의 기본값으로만 쓴다.</li>
 * </ul>
 * enterCd 는 늘 세션의 현재 법인(또는 계산 대상 행의 법인)이다. 요청 본문·쿼리로 받지 않는다.
 */
@Service
public class EnterSettingService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public record SettingEntry(LocalDate effectiveFrom, int value, String updatedBy, LocalDateTime updatedAt) {
    }

    /**
     * 화면용 한 키. value·effectiveFrom 은 오늘 적용되는 값(effectiveFrom null = 기본값 사용).
     * entries 는 그 법인에 등록된 적용시작일별 값 전체(앞으로 적용될 값 포함).
     */
    public record SettingView(String key, String label, String unit, int min, int max, int defaultValue, int value,
            LocalDate effectiveFrom, List<SettingEntry> entries) {
    }

    public record SettingChange(long histId, String key, String label, LocalDate effectiveFrom, Integer oldValue,
            int newValue, String changedBy, LocalDateTime changedAt) {
    }

    /** value 는 정수만(1.5 같은 소수는 버리지 않고 거부한다). */
    public record UpdateRequest(LocalDate effectiveFrom, BigDecimal value) {
    }

    /** 설정 API 오류. */
    public static class SettingException extends RuntimeException {
        private final HttpStatus status;

        SettingException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }

    private final EnterSettingRepository repo;
    private final Map<EnterSettingKey, Integer> defaults;
    private final Clock clock;

    @Autowired
    public EnterSettingService(EnterSettingRepository repo, Environment env) {
        this(repo, defaultsFrom(env), Clock.system(SEOUL));
    }

    /** 테스트용: 기본값을 직접 준다. 빠진 키를 읽으면 IllegalStateException. */
    public EnterSettingService(EnterSettingRepository repo, Map<EnterSettingKey, Integer> defaults, Clock clock) {
        for (Map.Entry<EnterSettingKey, Integer> d : defaults.entrySet()) {
            if (d.getValue() == null || !d.getKey().inRange(d.getValue())) {
                throw new IllegalArgumentException(d.getKey().property + " 기본값은 " + d.getKey().min + "~"
                        + d.getKey().max + " 이어야 해요: " + d.getValue());
            }
        }
        this.repo = repo;
        this.defaults = Map.copyOf(defaults);
        this.clock = clock.withZone(SEOUL);
    }

    private static Map<EnterSettingKey, Integer> defaultsFrom(Environment env) {
        Map<EnterSettingKey, Integer> m = new EnumMap<>(EnterSettingKey.class);
        for (EnterSettingKey k : EnterSettingKey.values()) {
            m.put(k, env.getRequiredProperty(k.property, Integer.class));
        }
        return m;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 그 법인에 그 날짜에 적용되는 값. 법인 값이 없으면 기본값. */
    @Transactional(readOnly = true)
    public int value(String enterCd, EnterSettingKey key, LocalDate date) {
        Objects.requireNonNull(enterCd, "enterCd");
        Objects.requireNonNull(date, "date");
        return repo.findEffective(enterCd, key.name(), date).orElseGet(() -> defaultValue(key));
    }

    /** 오늘 적용되는 값. */
    public int valueToday(String enterCd, EnterSettingKey key) {
        return value(enterCd, key, today());
    }

    private int defaultValue(EnterSettingKey key) {
        Integer d = defaults.get(key);
        if (d == null) {
            throw new IllegalStateException("기본값이 없는 설정이에요: " + key.property);
        }
        return d;
    }

    @Transactional(readOnly = true)
    public List<SettingView> list(String enterCd) {
        LocalDate today = today();
        Map<String, List<SettingEntry>> byKey = new HashMap<>();
        for (EnterSettingRepository.Entry e : repo.findByEnterCd(enterCd)) {
            byKey.computeIfAbsent(e.key(), k -> new ArrayList<>())
                    .add(new SettingEntry(e.effectiveFrom(), e.value(), e.updatedBy(), e.updatedAt()));
        }
        List<SettingView> out = new ArrayList<>();
        for (EnterSettingKey k : EnterSettingKey.values()) {
            List<SettingEntry> entries = byKey.getOrDefault(k.name(), List.of());
            Optional<SettingEntry> current = entries.stream().filter(e -> !e.effectiveFrom().isAfter(today))
                    .reduce((a, b) -> b);
            int def = defaultValue(k);
            out.add(new SettingView(k.name(), k.label, k.unit, k.min, k.max, def,
                    current.map(SettingEntry::value).orElse(def), current.map(SettingEntry::effectiveFrom).orElse(null),
                    entries));
        }
        return out;
    }

    /**
     * 그 적용시작일의 값을 등록하거나 고친다. 바뀔 때마다 변경 이력을 남긴다(같은 값이면 아무것도 하지 않는다).
     * 과거 적용시작일도 받는다(입력 실수 정정). 그 날짜 뒤 계산은 다시 하면 새 값으로 판정된다.
     */
    @Transactional
    public SettingView update(String enterCd, String keyName, UpdateRequest req, String actor) {
        EnterSettingKey key = EnterSettingKey.parse(keyName)
                .orElseThrow(() -> new SettingException(HttpStatus.NOT_FOUND, "없는 설정이에요: " + keyName));
        if (req == null || req.effectiveFrom() == null) {
            throw new SettingException(HttpStatus.BAD_REQUEST, "적용 시작일을 넣어 주세요");
        }
        if (req.value() == null || req.value().stripTrailingZeros().scale() > 0
                || req.value().compareTo(BigDecimal.valueOf(key.min)) < 0
                || req.value().compareTo(BigDecimal.valueOf(key.max)) > 0) {
            throw new SettingException(HttpStatus.BAD_REQUEST,
                    key.label + "은(는) " + key.min + "~" + key.max + key.unit + " 사이 정수로 넣어 주세요");
        }
        int value = req.value().intValueExact();
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<Integer> old = repo.lockValue(enterCd, key.name(), req.effectiveFrom());
        if (old.isEmpty()) {
            repo.insert(enterCd, key.name(), req.effectiveFrom(), value, actor, now);
        } else if (old.get() != value) {
            repo.update(enterCd, key.name(), req.effectiveFrom(), value, actor, now);
        }
        if (old.isEmpty() || old.get() != value) {
            repo.insertChange(enterCd, key.name(), req.effectiveFrom(), old.orElse(null), value, actor, now);
        }
        return list(enterCd).stream().filter(v -> v.key().equals(key.name())).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<SettingChange> history(String enterCd) {
        return repo.findChanges(enterCd).stream().map(c -> new SettingChange(c.histId(), c.key(),
                EnterSettingKey.parse(c.key()).map(k -> k.label).orElse(c.key()), c.effectiveFrom(), c.oldValue(),
                c.newValue(), c.changedBy(), c.changedAt())).toList();
    }
}
