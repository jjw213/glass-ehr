package com.glassehr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모든 API 메서드에 권한 표시가 있는지(빠뜨리면 AuthInterceptor 가 403으로 막지만, 실수를 테스트에서 먼저 잡는다).
 * 개인정보를 돌려주는 사원 API에는 PublicApi 를 붙이지 않는다.
 */
class ControllerAuthCoverageTest {

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scan = new ClassPathScanningCandidateComponentProvider(false);
        scan.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (BeanDefinition d : scan.findCandidateComponents("com.glassehr")) {
            out.add(Class.forName(d.getBeanClassName()));
        }
        return out;
    }

    @Test
    void 모든_API_메서드에_RequiresPermission이나_PublicApi가_있다() throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> publics = new ArrayList<>();
        int handlers = 0;
        for (Class<?> c : controllers()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)) {
                    continue;
                }
                handlers++;
                boolean rp = m.isAnnotationPresent(RequiresPermission.class);
                boolean pub = m.isAnnotationPresent(PublicApi.class);
                if (rp == pub) {
                    missing.add(c.getSimpleName() + "." + m.getName());
                }
                if (pub) {
                    publics.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertThat(handlers).isGreaterThan(15);
        assertThat(missing).as("권한 표시가 없거나 둘 다 있는 메서드").isEmpty();
        assertThat(publics).containsExactly("HealthController.health");
    }
}
