package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 법인 조건 누락 검사(#129). 설계: docs/tenant-design.md 5.2 "검사 테스트" 1·2.
 * <ol>
 *   <li>법인 조건 없이 ID 로만 찾는 상속 메서드(findById 등)를 본 코드에서 부르지 않는다.</li>
 *   <li>법인별 엔티티 리포지토리의 메서드는 모두 enterCd 조건을 가진다(파생 메서드 이름 또는 @Query).</li>
 *   <li>JdbcTemplate·네이티브 SQL 문자열과 MyBatis 매퍼에서 법인별 테이블을 쓰면 enter_cd 조건이 있다.</li>
 * </ol>
 */
class TenantQueryScanTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final Path MAPPERS = Path.of("src/main/resources/mapper");

    /** 법인 조건 없이 ID·전체로 찾는 상속 메서드. */
    private static final Pattern INHERITED = Pattern.compile(
            "(\\w+)\\.(findById|getReferenceById|getById|existsById|findAllById|deleteById|deleteAllById|findAll|count)\\(\\s*([^)]*)\\)");

    /** 전체 공통 테이블 리포지토리(법인 마스터)의 호출만 허용. 파일명:수신자.메서드. */
    private static final Set<String> ALLOWED_INHERITED = Set.of("PrivacyAccessLogger.java:companies.existsById");

    /** 법인 조건 없는 리포지토리 메서드 허용 목록(이유). */
    private static final Set<String> ALLOWED_UNSCOPED = Set.of(
            // 발령 매일 반영: 모든 법인 사원 키를 읽어 법인별로 나눠 처리한다(AppointmentDailySync, tenant-design.md 8장).
            "EmployeeRepository.findAllByOrderByEnterCdAscEmpIdAsc");

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> s = Files.walk(MAIN)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    @Test
    void 본_코드는_법인_조건_없는_상속_메서드를_부르지_않는다() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path p : javaSources()) {
            String code = stripCommentsAndStrings(Files.readString(p, StandardCharsets.UTF_8));
            Matcher m = INHERITED.matcher(code);
            while (m.find()) {
                // count()·findAll() 은 인자 없는 것만(스트림 count() 와 구분할 수 없어 수신자가 리포지토리 필드인 경우만 잡는다).
                String receiver = m.group(1);
                String method = m.group(2);
                if ((method.equals("count") || method.equals("findAll")) && !receiver.matches("[a-z]\\w*")) {
                    continue;
                }
                if ((method.equals("count")) && !m.group(3).isBlank()) {
                    continue;
                }
                if (method.equals("count") && !looksLikeRepository(code, receiver)) {
                    continue;
                }
                String key = p.getFileName() + ":" + receiver + "." + method;
                if (!ALLOWED_INHERITED.contains(key)) {
                    found.add(key);
                }
            }
        }
        assertThat(found).as("법인 조건 없이 ID·전체로 찾는 호출(리포지토리에 findByEnterCdAnd... 를 쓴다)").isEmpty();
    }

    /** 필드 선언 타입이 *Repository 인지. */
    private static boolean looksLikeRepository(String code, String receiver) {
        return Pattern.compile("\\b\\w+Repository\\s+" + Pattern.quote(receiver) + "\\b").matcher(code).find();
    }

    @Test
    void 법인별_엔티티_리포지토리_메서드는_모두_enterCd_조건을_가진다() throws Exception {
        Set<String> tenant = TenantSchemaScriptTest.tenantTables();
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition d) {
                return d.getMetadata().isInterface();
            }
        };
        scan.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<String> wrong = new ArrayList<>();
        int repos = 0;
        for (BeanDefinition d : scan.findCandidateComponents("com.glassehr")) {
            Class<?> repo = Class.forName(d.getBeanClassName());
            Class<?> domain = ResolvableType.forClass(repo).as(Repository.class).getGeneric(0).resolve();
            if (domain == null || !domain.isAnnotationPresent(Entity.class)
                    || !tenant.contains(TenantEntityKeyTest.table(domain))) {
                continue;
            }
            repos++;
            for (Method m : repo.getDeclaredMethods()) {
                if (m.isDefault() || m.isSynthetic() || java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                // 저장은 엔티티에 든 enterCd(서비스가 세션 법인으로 넣음)를 그대로 쓴다.
                if (m.getName().matches("save(All)?(AndFlush)?")) {
                    continue;
                }
                String key = repo.getSimpleName() + "." + m.getName();
                Query q = m.getAnnotation(Query.class);
                boolean scoped = q != null
                        ? q.value().toLowerCase(Locale.ROOT).matches("(?s).*\\b(enter_?cd)\\s*=.*")
                        : m.getName().matches("^[a-z]+(First\\d*|Top\\d*|Distinct)?By(EnterCd)\\b.*")
                                || m.getName().matches("^[a-z]+\\w*?ByEnterCd(And|In|Order|$).*");
                if (!scoped && !ALLOWED_UNSCOPED.contains(key)) {
                    wrong.add(key);
                }
            }
        }
        assertThat(repos).isGreaterThanOrEqualTo(20);
        assertThat(wrong).as("enterCd 조건이 없는 리포지토리 메서드").isEmpty();
    }

    @Test
    void SQL_문자열과_매퍼는_법인별_테이블마다_enter_cd_조건을_건다() throws IOException {
        Set<String> tenant = TenantSchemaScriptTest.tenantTables();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Path p : javaSources()) {
            for (String sql : javaSqlStatements(Files.readString(p, StandardCharsets.UTF_8))) {
                String miss = missingEnterCd(sql, tenant);
                if (miss != null) {
                    checked++;
                    if (!miss.isEmpty()) {
                        wrong.add(p.getFileName() + ": " + miss + " ← " + oneLine(sql));
                    }
                }
            }
        }
        if (Files.isDirectory(MAPPERS)) {
            try (Stream<Path> s = Files.walk(MAPPERS)) {
                for (Path p : s.filter(x -> x.toString().endsWith(".xml")).toList()) {
                    Matcher m = Pattern.compile("(?is)<(select|insert|update|delete)\\b[^>]*>(.*?)</\\1>")
                            .matcher(Files.readString(p, StandardCharsets.UTF_8));
                    while (m.find()) {
                        String miss = missingEnterCd(m.group(2), tenant);
                        if (miss != null) {
                            checked++;
                            if (!miss.isEmpty()) {
                                wrong.add(p.getFileName() + ": " + miss + " ← " + oneLine(m.group(2)));
                            }
                        }
                    }
                }
            }
        }
        // 메시지·집계 매퍼·공통코드 사용처·데모 계정 등.
        assertThat(checked).isGreaterThanOrEqualTo(10);
        assertThat(wrong).as("enter_cd 조건이 모자란 SQL").isEmpty();
    }

    private static final Pattern DML = Pattern.compile("(?i)\\b(SELECT|INSERT|UPDATE|DELETE|MERGE)\\b");
    /**
     * 테이블 이름은 소문자 그대로만 맞춘다. JPQL 은 엔티티 이름(Holiday·Employee)을 쓰므로 여기서 빠지고,
     * 위 리포지토리 메서드 검사가 맡는다.
     */
    private static final Pattern TABLE_REF = Pattern.compile("\\b(?i:FROM|JOIN|INTO|UPDATE|USING)\\s+([a-z_0-9]+)\\b");

    /**
     * 법인별 테이블을 쓰지 않으면 null, 쓰는데 enter_cd 가 테이블 수보다 적게 나오면 테이블 목록, 충분하면 "".
     * 테이블마다 WHERE·ON·INSERT 열에 enter_cd 가 하나 이상 있어야 한다는 근사 규칙이다.
     */
    static String missingEnterCd(String sql, Set<String> tenant) {
        if (!DML.matcher(sql).find()) {
            return null;
        }
        List<String> refs = new ArrayList<>();
        Matcher m = TABLE_REF.matcher(sql);
        while (m.find()) {
            String t = m.group(1).toLowerCase(Locale.ROOT);
            if (tenant.contains(t)) {
                refs.add(t);
            }
        }
        if (refs.isEmpty()) {
            return null;
        }
        Matcher e = Pattern.compile("(?i)\\benter_cd\\b").matcher(sql);
        int n = 0;
        while (e.find()) {
            n++;
        }
        return n >= refs.size() ? "" : refs.toString();
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    /** 자바 문장(; 단위)마다 그 안의 문자열 리터럴·텍스트 블록을 이어 붙인 것. 주석은 뺀다. */
    static List<String> javaSqlStatements(String src) {
        List<String> out = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (src.startsWith("//", i)) {
                i = indexOrEnd(src, "\n", i);
            } else if (src.startsWith("/*", i)) {
                i = indexOrEnd(src, "*/", i + 2) + 2;
            } else if (src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end;
                sql.append(src, i + 3, end).append(' ');
                i = end + 3;
            } else if (c == '"') {
                int j = i + 1;
                StringBuilder lit = new StringBuilder();
                while (j < n && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\' && j + 1 < n) {
                        lit.append(src.charAt(j + 1));
                        j += 2;
                    } else {
                        lit.append(src.charAt(j++));
                    }
                }
                sql.append(lit);
                i = j + 1;
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '\'') {
                    j += src.charAt(j) == '\\' ? 2 : 1;
                }
                i = j + 1;
            } else {
                if (c == ';' || c == '{' || c == '}') {
                    if (!sql.isEmpty()) {
                        out.add(sql.toString());
                        sql.setLength(0);
                    }
                }
                i++;
            }
        }
        return out;
    }

    private static int indexOrEnd(String s, String what, int from) {
        int k = s.indexOf(what, from);
        return k < 0 ? s.length() : k;
    }

    /** 주석과 문자열 내용을 지운 코드(호출 검사용). */
    private static String stripCommentsAndStrings(String src) {
        String noBlock = src.replaceAll("(?s)/\\*.*?\\*/", " ");
        String noText = noBlock.replaceAll("(?s)\"\"\".*?\"\"\"", "\"\"");
        String noLine = noText.replaceAll("//[^\n]*", " ");
        return noLine.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
    }
}
