package com.glassehr.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 암호화 왕복·IV·키 버전·설정 검사. 키는 테스트 안에서 만든 고정 바이트(실제 키 아님). */
class RrnCryptoTest {

    static String key(int fill) {
        byte[] b = new byte[32];
        java.util.Arrays.fill(b, (byte) fill);
        return Base64.getEncoder().encodeToString(b);
    }

    static RrnCrypto crypto() {
        return new RrnCrypto("1:" + key(1), "1", key(9));
    }

    private static final Rrn R = Rrn.parse("000000-0000001");

    @Test
    void 암호화_복호화_왕복() {
        RrnCrypto c = crypto();
        RrnCrypto.Sealed s = c.encrypt(R, 10L);
        assertThat(s.keyVersion()).isEqualTo(1);
        assertThat(c.decrypt(s.cipherText(), 1, 10L)).isEqualTo(R);
    }

    @Test
    void 암호문에_평문이_없고_V009_컬럼_크기_안에_든다() {
        byte[] ct = crypto().encrypt(R, 10L).cipherText();
        assertThat(new String(ct, StandardCharsets.ISO_8859_1)).doesNotContain("0000001").doesNotContain("000000-");
        assertThat(ct.length).isLessThanOrEqualTo(64); // rrn_enc RAW(64)
    }

    @Test
    void 같은_번호도_레코드마다_IV와_암호문이_다르다() {
        RrnCrypto c = crypto();
        byte[] a = c.encrypt(R, 10L).cipherText();
        byte[] b = c.encrypt(R, 10L).cipherText();
        assertThat(RrnCrypto.ivOf(a)).isNotEqualTo(RrnCrypto.ivOf(b));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void 다른_사원_행으로_옮긴_암호문은_복호화되지_않는다() {
        RrnCrypto c = crypto();
        byte[] ct = c.encrypt(R, 10L).cipherText();
        assertThatThrownBy(() -> c.decrypt(ct, 1, 11L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키_버전이_다른_레코드도_복호화한다() {
        RrnCrypto old = new RrnCrypto("1:" + key(1), "1", key(9));
        byte[] v1 = old.encrypt(R, 10L).cipherText();

        RrnCrypto rotated = new RrnCrypto("1:" + key(1) + ", 2:" + key(2), "2", key(9));
        RrnCrypto.Sealed v2 = rotated.encrypt(R, 10L);
        assertThat(v2.keyVersion()).isEqualTo(2);
        assertThat(rotated.decrypt(v1, 1, 10L)).isEqualTo(R);
        assertThat(rotated.decrypt(v2.cipherText(), 2, 10L)).isEqualTo(R);
        assertThatThrownBy(() -> rotated.decrypt(v2.cipherText(), 1, 10L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> old.decrypt(v2.cipherText(), 2, 10L))
                .isInstanceOf(RrnCrypto.KeyNotConfiguredException.class);
    }

    @Test
    void HMAC은_같은_번호에_같은_값이고_다른_번호엔_다르다() {
        RrnCrypto c = crypto();
        assertThat(c.hash(R)).hasSize(32).isEqualTo(c.hash(Rrn.parse("0000000000001")));
        assertThat(c.hash(R)).isNotEqualTo(c.hash(Rrn.parse("000000-0000002")));
    }

    @Test
    void 키가_없으면_사용할_때_거부한다() {
        RrnCrypto c = new RrnCrypto("", "", "");
        assertThatThrownBy(() -> c.encrypt(R, 1L)).isInstanceOf(RrnCrypto.KeyNotConfiguredException.class);
        assertThatThrownBy(() -> c.hash(R)).isInstanceOf(RrnCrypto.KeyNotConfiguredException.class);
        assertThatThrownBy(() -> c.decrypt(new byte[40], 1, 1L)).isInstanceOf(RrnCrypto.KeyNotConfiguredException.class);
    }

    @Test
    void 잘못된_키_설정은_거부하고_메시지에_키를_넣지_않는다() {
        String k = key(1);
        String[][] bad = {
                {"1:" + Base64.getEncoder().encodeToString(new byte[16]), "1", key(9)}, // 16바이트
                {"1:" + k, "2", key(9)}, // 현재 버전 키 없음
                {"1:" + k, "1", k}, // HMAC 키 = 암호화 키
                {"1:" + k + ",1:" + key(2), "1", key(9)}, // 버전 중복
                {"x:" + k, "1", key(9)}, // 버전이 숫자 아님
                {k, "1", key(9)}, // 형식 틀림
        };
        for (String[] b : bad) {
            RrnCrypto c = new RrnCrypto(b[0], b[1], b[2]);
            assertThatThrownBy(() -> c.encrypt(R, 1L)).isInstanceOf(RrnCrypto.KeyNotConfiguredException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(k));
        }
    }
}
