package com.glassehr.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 공휴일 CSV 형식 검사. 날짜·이름은 형식 확인용 <b>가짜 값(2099년, '시험 공휴일')이며 실제 공휴일이 아니다</b>.
 * 실제 공휴일 자료는 사람이 넣는다.
 */
class PublicHolidayCsvTest {

    private static final String HEADER = "날짜,이름,종류,출처,검증일\n";

    @Test
    void 법정공휴일과_대체공휴일을_읽고_따옴표_안_쉼표를_허용한다() {
        List<PublicHolidayCsv.Row> rows = PublicHolidayCsv.parse("﻿" + HEADER
                + "2099-01-01,시험 공휴일,법정공휴일,\"시험 규정 제1조, https://example.invalid/a\",2099-01-01\n"
                + "\n"
                + "2099-01-02,시험 공휴일 대체,대체공휴일,시험 규정 제2조,2099-01-01\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2099, 1, 1));
        assertThat(rows.get(0).kind()).isEqualTo(HolidayKind.PUBLIC);
        assertThat(rows.get(0).source()).isEqualTo("시험 규정 제1조, https://example.invalid/a");
        assertThat(rows.get(1).kind()).isEqualTo(HolidayKind.SUBSTITUTE);
        assertThat(rows.get(1).line()).isEqualTo(4);
    }

    @Test
    void 열_순서가_달라도_되고_다른_열은_무시한다() {
        List<PublicHolidayCsv.Row> rows = PublicHolidayCsv.parse(
                "검증일,근거,종류,이름,날짜,출처\n2099-02-01,무시,법정공휴일,시험,2099-03-01,시험 출처\n");
        assertThat(rows.get(0).date()).isEqualTo(LocalDate.of(2099, 3, 1));
        assertThat(rows.get(0).verified()).isEqualTo(LocalDate.of(2099, 2, 1));
    }

    @Test
    void 윤일_날짜를_읽고_없는_날짜는_거부한다() {
        assertThat(PublicHolidayCsv.parse(HEADER + "2096-02-29,시험,법정공휴일,시험 출처,2099-01-01\n")
                .get(0).date()).isEqualTo(LocalDate.of(2096, 2, 29));
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER + "2099-02-29,시험,법정공휴일,시험 출처,2099-01-01\n"))
                .hasMessageContaining("2번째 줄").hasMessageContaining("날짜");
    }

    @Test
    void 필수_열이_없으면_거부한다() {
        assertThatThrownBy(() -> PublicHolidayCsv.parse("날짜,이름,종류\n2099-01-01,시험,법정공휴일\n"))
                .isInstanceOf(PublicHolidayCsv.FormatException.class)
                .hasMessageContaining("출처").hasMessageContaining("검증일");
    }

    @Test
    void 출처나_검증일이_비면_거부한다() {
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER + "2099-01-01,시험,법정공휴일,,2099-01-01\n"))
                .hasMessageContaining("출처");
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER + "2099-01-01,시험,법정공휴일,시험 출처,\n"))
                .hasMessageContaining("검증일");
    }

    @Test
    void 회사휴일이나_대체된_휴일은_공휴일_자료에_넣을_수_없다() {
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER + "2099-01-01,시험,회사휴일,시험 출처,2099-01-01\n"))
                .hasMessageContaining("종류");
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER + "2099-01-01,시험,대체된 휴일,시험 출처,2099-01-01\n"))
                .hasMessageContaining("종류");
    }

    @Test
    void 같은_날짜_이름이_두번이면_거부한다() {
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER
                + "2099-01-01,시험,법정공휴일,시험 출처,2099-01-01\n"
                + "2099-01-01,시험,대체공휴일,시험 출처,2099-01-01\n"))
                .hasMessageContaining("3번째 줄");
    }

    @Test
    void 비었거나_행이_없으면_거부한다() {
        assertThatThrownBy(() -> PublicHolidayCsv.parse(" ")).hasMessageContaining("비어");
        assertThatThrownBy(() -> PublicHolidayCsv.parse(HEADER)).hasMessageContaining("행이 없");
    }
}
