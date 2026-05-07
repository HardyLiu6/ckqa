package org.ysu.ckqaback.pdf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.ysu.ckqaback.api.ApiResultCode;
import org.ysu.ckqaback.auth.JwtProperties;
import org.ysu.ckqaback.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 为原生 EventSource 签发短期、资料绑定的解析进度流令牌。
 */
@Service
public class PdfParseStreamTokenService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration ttl;

    @Autowired
    public PdfParseStreamTokenService(JwtProperties jwtProperties, ObjectProvider<ObjectMapper> objectMapperProvider) {
        this(
                jwtProperties,
                objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()),
                Clock.systemUTC(),
                DEFAULT_TTL
        );
    }

    PdfParseStreamTokenService(
            JwtProperties jwtProperties,
            ObjectMapper objectMapper,
            Clock clock,
            Duration ttl
    ) {
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
    }

    public ParseStreamTokenResponse issue(Long materialId) {
        Instant expiresAt = Instant.now(clock).plus(ttl);
        TokenPayload payload = new TokenPayload(materialId, expiresAt.getEpochSecond());
        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedPayload);
        return new ParseStreamTokenResponse(encodedPayload + "." + signature, expiresAt);
    }

    public ValidatedStreamToken validate(Long materialId, String token) {
        if (!StringUtils.hasText(token)) {
            throw invalidToken();
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw invalidToken();
        }

        String expectedSignature = sign(parts[0]);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw invalidToken();
        }

        TokenPayload payload = decodePayload(parts[0]);
        if (!materialId.equals(payload.materialId())) {
            throw invalidToken();
        }

        if (Instant.ofEpochSecond(payload.expiresAt()).isBefore(Instant.now(clock))) {
            throw new BusinessException(ApiResultCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED, "解析进度令牌已过期");
        }

        return new ValidatedStreamToken(payload.materialId());
    }

    private String encodeJson(TokenPayload payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("解析进度令牌生成失败", ex);
        }
    }

    private TokenPayload decodePayload(String encodedPayload) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(encodedPayload);
            return objectMapper.readValue(json, TokenPayload.class);
        } catch (Exception ex) {
            throw invalidToken();
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("解析进度令牌签名失败", ex);
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException(ApiResultCode.AUTH_INVALID, HttpStatus.UNAUTHORIZED, "解析进度令牌无效");
    }

    public record ValidatedStreamToken(Long materialId) {
    }

    private record TokenPayload(Long materialId, Long expiresAt) {
    }
}
