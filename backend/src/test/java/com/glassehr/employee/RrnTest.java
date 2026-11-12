package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 주민등록번호 값 타입. 명백한 가짜 값(000000-0000001 등)만 쓴다. */
class RrnTest {

    @Test
    void 하이픈_유무와_관계없이_정규화한다() {
        assertThat(Rrn.parse("0000000000001").plain()).isEqualTo("000000-0000001");
        assertThat(Rrn.parse(" 000000-0000001 ").plain()).isEqualTo("000000-0000001");
        assertThat(Rrn.parse("000000-0000001")).isEqualTo(Rrn.parse("0000000000001"));
    }

    @Test
    void 마스킹은_앞_6자리만_보이고_뒷자리_7자리를_가린다() {
        assertThat(Rrn.parse("000000-0000001").masked()).isEqualTo("000000-*******");
    }

    @Test
    void toString은_원문을_찍지_않는다() {
        Rrn r = Rrn.parse("000000-9999999");
        assertThat(r.toString()).isEqualTo("000000-*******").doesNotContain("9999999");
    }

    @Test
    void 형식이_틀리면_거부하고_메시지에_값을_넣지_않는다() {
        for (String bad : new String[] {"", "00000-0000001", "000000-000000", "000000-00000011", "abcdef-0000001",
                "000000--0000001"}) {
            assertThatThrownBy(() -> Rrn.parse(bad)).as(bad)
                    .isInstanceOf(IllegalArgumentException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("0000001"));
        }
        assertThatThrownBy(() -> Rrn.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
