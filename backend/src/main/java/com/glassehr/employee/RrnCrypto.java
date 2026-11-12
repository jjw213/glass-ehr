package com.glassehr.employee;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 주민등록번호 컬럼 단위 암호화(애플리케이션 계층). 개인정보 보호법 제24조의2 제2항·제29조, docs/rrn-design.md 2장.
 *
 * <ul>
 *   <li>암호화: AES-256-GCM, 레코드마다 무작위 IV(12바이트), 태그 128비트. 저장 형식 = IV ‖ 암호문 ‖ 태그.
 *       AAD 로 emp_id 를 넣어 다른 사원 행으로 옮긴 암호문은 복호화되지 않게 한다.</li>
 *   <li>중복 검사: HMAC-SHA256(암호화 키와 다른 키). 원문 복원 불가.</li>
 *   <li>키는 환경변수(.env·비밀 저장소)로만 받는다. {@code RRN_ENC_KEYS="1:base64,2:base64"}(버전:키, 32바이트),
 *       {@code RRN_CURRENT_KEY_VERSION}(새로 암호화할 때 쓰는 버전), {@code RRN_HMAC_KEY}(base64, 32바이트 이상).
 *       옛 버전 키를 남겨 두면 그 버전으로 암호화된 레코드도 복호화된다(키 교체 대비).</li>
 *   <li>키가 없어도 애플리케이션은 뜬다. 주민등록번호를 저장·조회할 때 {@link KeyNotConfiguredException}.</li>
 * </ul>
 */
@Component
public class RrnCrypto {

    private static final String AES = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    /** 키 설정이 없을 때. 메시지에 키 값을 넣지 않는다. */
    public static class KeyNotConfiguredException extends RuntimeException {
        KeyNotConfiguredException(String message) {
            super(message);
        }
    }

    /** 암호문과 그때 쓴 키 버전. */
    public record Sealed(byte[] cipherText, int keyVersion) {
    }

    private final Map<Integer, byte[]> encKeys;
    private final Integer currentVersion;
    private final byte[] hmacKey;
    private final String configError;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public RrnCrypto(@Value("${glassehr.rrn.enc-keys:}") String encKeys,
            @Value("${glassehr.rrn.current-key-version:}") String currentVersion,
            @Value("${glassehr.rrn.hmac-key:}") String hmacKey) {
        Map<Integer, byte[]> keys = new HashMap<>();
        Integer cur = null;
        byte[] hk = null;
        String err = null;
        try {
            if (encKeys == null || encKeys.isBlank() || hmacKey == null || hmacKey.isBlank()
                    || currentVersion == null || currentVersion.isBlank()) {
                err = "주민등록번호 암호화 키가 설정되지 않았어요(RRN_ENC_KEYS, RRN_CURRENT_KEY_VERSION, RRN_HMAC_KEY)";
            } else {
                for (String part : encKeys.split(",")) {
                    String[] kv = part.strip().split(":", 2);
                    if (kv.length != 2) {
                        throw new IllegalArgumentException("RRN_ENC_KEYS 형식은 '버전:base64키,...' 이어야 해요");
                    }
                    int ver = Integer.parseInt(kv[0].strip());
                    byte[] key = Base64.getDecoder().decode(kv[1].strip());
                    if (ver < 1 || key.length != KEY_BYTES) {
                        throw new IllegalArgumentException("RRN_ENC_KEYS 의 키는 버전 1 이상, 32바이트(AES-256)여야 해요");
                    }
                    if (keys.put(ver, key) != null) {
                        throw new IllegalArgumentException("RRN_ENC_KEYS 에 같은 버전이 두 번 있어요");
                    }
                }
                cur = Integer.parseInt(currentVersion.strip());
                if (!keys.containsKey(cur)) {
                    throw new IllegalArgumentException("RRN_CURRENT_KEY_VERSION 에 해당하는 키가 RRN_ENC_KEYS 에 없어요");
                }
                hk = Base64.getDecoder().decode(hmacKey.strip());
                if (hk.length < KEY_BYTES) {
                    throw new IllegalArgumentException("RRN_HMAC_KEY 는 32바이트 이상이어야 해요");
                }
                for (byte[] k : keys.values()) {
                    if (MessageDigest.isEqual(k, hk)) {
                        throw new IllegalArgumentException("RRN_HMAC_KEY 는 암호화 키와 달라야 해요");
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            // 키 값은 메시지에 넣지 않는다. 잘못 설정한 키로 조용히 동작하지 않도록 사용할 때마다 거부한다.
            err = e instanceof NumberFormatException ? "주민등록번호 키 버전은 숫자여야 해요" : e.getMessage();
            keys.clear();
            cur = null;
            hk = null;
        }
        this.encKeys = Collections.unmodifiableMap(keys);
        this.currentVersion = cur;
        this.hmacKey = hk;
        this.configError = err;
    }

    private void requireConfigured() {
        if (configError != null) {
            throw new KeyNotConfiguredException(configError);
        }
    }

    /** 현재 키 버전으로 암호화한다. */
    public Sealed encrypt(Rrn rrn, long empId) {
        requireConfigured();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encKeys.get(currentVersion), AES),
                    new GCMParameterSpec(TAG_BITS, iv));
            c.updateAAD(aad(empId));
            byte[] ct = c.doFinal(rrn.plain().getBytes(StandardCharsets.US_ASCII));
            return new Sealed(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array(), currentVersion);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("주민등록번호 암호화에 실패했어요", e);
        }
    }

    /** 저장된 키 버전의 키로 복호화한다. 키가 없거나 암호문·emp_id 가 맞지 않으면 예외. */
    public Rrn decrypt(byte[] sealed, int keyVersion, long empId) {
        requireConfigured();
        byte[] key = encKeys.get(keyVersion);
        if (key == null) {
            throw new KeyNotConfiguredException("주민등록번호 키 버전 " + keyVersion + "의 키가 설정되지 않았어요");
        }
        if (sealed == null || sealed.length <= IV_BYTES) {
            throw new IllegalStateException("주민등록번호 암호문이 올바르지 않아요");
        }
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, AES),
                    new GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES));
            c.updateAAD(aad(empId));
            byte[] pt = c.doFinal(sealed, IV_BYTES, sealed.length - IV_BYTES);
            return Rrn.parse(new String(pt, StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("주민등록번호 복호화에 실패했어요(키 또는 암호문 불일치)", e);
        }
    }

    /** 중복 등록 검사용 HMAC-SHA256(32바이트). */
    public byte[] hash(Rrn rrn) {
        requireConfigured();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(hmacKey, HMAC));
            return mac.doFinal(rrn.plain().getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("주민등록번호 해시 계산에 실패했어요", e);
        }
    }

    public int currentKeyVersion() {
        requireConfigured();
        return currentVersion;
    }

    private static byte[] aad(long empId) {
        return ("emp_rrn:" + empId).getBytes(StandardCharsets.US_ASCII);
    }

    /** 테스트용: 암호문에서 IV 부분. */
    static byte[] ivOf(byte[] sealed) {
        return Arrays.copyOf(sealed, IV_BYTES);
    }
}
