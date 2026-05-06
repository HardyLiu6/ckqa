package org.ysu.ckqaback.auth;

import lombok.Getter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 基于 Spring Security Nimbus 组件签发和解析 HMAC JWT。
 */
@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    @Getter
    private final JwtDecoder decoder;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        SecretKeySpec secretKey = new SecretKeySpec(resolveSecret(properties).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.userCode())
                .claim(AuthConstants.USER_ID_CLAIM, user.id())
                .claim(AuthConstants.USER_CODE_CLAIM, user.userCode())
                .claim(AuthConstants.USERNAME_CLAIM, user.username())
                .claim(AuthConstants.DISPLAY_NAME_CLAIM, user.displayName())
                .claim(AuthConstants.ROLES_CLAIM, safeList(user.roles()))
                .claim(AuthConstants.PERMISSIONS_CLAIM, safeList(user.permissions()))
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        ZoneId zone = ZoneId.systemDefault();
        return new IssuedToken(
                token,
                LocalDateTime.ofInstant(issuedAt, zone),
                LocalDateTime.ofInstant(expiresAt, zone)
        );
    }

    public AuthenticatedUser parse(String accessToken) {
        Jwt jwt = decoder.decode(accessToken);
        return AuthenticatedUser.fromJwt(jwt);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String resolveSecret(JwtProperties properties) {
        String secret = properties.getSecret();
        if (!StringUtils.hasText(secret) || secret.trim().length() < 32) {
            throw new IllegalArgumentException("ckqa.jwt.secret 长度不能少于32个字符");
        }
        return secret.trim();
    }

    public record IssuedToken(String accessToken, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    }
}
