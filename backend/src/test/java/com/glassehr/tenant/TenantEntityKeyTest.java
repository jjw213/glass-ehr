package com.glassehr.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * 법인별 JPA 엔티티는 모두 enterCd 를 키로 가진다(#129). 설계: docs/tenant-design.md 5.2.
 * <ul>
 *   <li>복합 ID(@EmbeddedId·@IdClass) 엔티티: ID 안에 enter_cd 가 있다.</li>
 *   <li>IDENTITY @Id 하나인 엔티티: enter_cd 를 NOT NULL·수정 불가 열로 매핑한다(DB PK 는 (enter_cd, id)).
 *       IDENTITY 값이 전체에서 유일하므로 영속성 컨텍스트 식별은 @Id 로 충분하다.</li>
 * </ul>
 */
class TenantEntityKeyTest {

    private static List<Class<?>> entities() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition d : scan.findCandidateComponents("com.glassehr")) {
            out.add(Class.forName(d.getBeanClassName()));
        }
        return out;
    }

    static String table(Class<?> c) {
        Table t = c.getAnnotation(Table.class);
        String name = t != null && !t.name().isEmpty() ? t.name() : c.getSimpleName();
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<Field> fields(Class<?> c) {
        List<Field> out = new ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            out.addAll(List.of(k.getDeclaredFields()));
        }
        return out;
    }

    /** enter_cd 열에 매핑된 필드(이름 규칙 enterCd → enter_cd 포함). */
    static Field enterCdField(Class<?> c) {
        for (Field f : fields(c)) {
            Column col = f.getAnnotation(Column.class);
            String name = col != null && !col.name().isEmpty() ? col.name() : f.getName();
            if (name.equalsIgnoreCase("enter_cd") || name.equals("enterCd")) {
                return f;
            }
        }
        return null;
    }

    @Test
    void 법인별_엔티티는_모두_enterCd를_키에_가진다() throws Exception {
        Set<String> tenant = TenantSchemaScriptTest.tenantTables();
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Class<?> c : entities()) {
            String t = table(c);
            if (!tenant.contains(t)) {
                continue;
            }
            checked++;
            IdClass idClass = c.getAnnotation(IdClass.class);
            Field embedded = fields(c).stream().filter(f -> f.isAnnotationPresent(EmbeddedId.class)).findFirst()
                    .orElse(null);
            if (embedded != null || idClass != null) {
                Class<?> idType = embedded != null ? embedded.getType() : idClass.value();
                if (enterCdField(idType) == null) {
                    wrong.add(c.getSimpleName() + "(" + t + "): 복합 ID " + idType.getSimpleName() + "에 enterCd 없음");
                }
                if (idClass != null) {
                    Field f = enterCdField(c);
                    if (f == null || !f.isAnnotationPresent(Id.class)) {
                        wrong.add(c.getSimpleName() + "(" + t + "): @IdClass 엔티티의 enterCd 에 @Id 없음");
                    }
                }
                continue;
            }
            Field f = enterCdField(c);
            Column col = f == null ? null : f.getAnnotation(Column.class);
            if (f == null) {
                wrong.add(c.getSimpleName() + "(" + t + "): enterCd 없음");
            } else if (TenantSchemaScriptTest.NULLABLE_NO_FK.contains(t)) {
                // login_access_log: 없는 법인으로 시도한 로그인도 남기므로 NULL 허용(3.2 ※※). 수정 불가만 본다.
                if (col == null || col.updatable()) {
                    wrong.add(c.getSimpleName() + "(" + t + "): enterCd 는 updatable = false 여야 함");
                }
            } else if (col == null || col.nullable() || col.updatable()) {
                wrong.add(c.getSimpleName() + "(" + t + "): enterCd 는 nullable = false, updatable = false 여야 함");
            }
        }
        // 사원·조직·코드·발령·계약·퇴직·근태·결재·연장근로·가족돌봄 엔티티(새 엔티티가 생기면 늘어난다).
        assertThat(checked).isGreaterThanOrEqualTo(20);
        assertThat(wrong).as("법인 키가 빠진 법인별 엔티티").isEmpty();
    }

    @Test
    void 엔티티가_매핑한_테이블은_마이그레이션에_있다() throws Exception {
        // @Table 이름 오타로 위 검사에서 빠지는 것을 막는다.
        Set<String> all = TenantSchemaScriptTest.allTables();
        List<String> unknown = new ArrayList<>();
        for (Class<?> c : entities()) {
            if (!all.contains(table(c))) {
                unknown.add(c.getSimpleName() + " → " + table(c));
            }
        }
        assertThat(unknown).isEmpty();
    }
}
