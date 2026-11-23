package vn.edu.ptit.library.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NimbusMicrosoftTokenVerifier implements MicrosoftTokenVerifier {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private final MicrosoftAuthProperties properties;
    private final JwtDecoder jwtDecoderOverride;
    private volatile JwtDecoder cachedDecoder;

    @Autowired
    public NimbusMicrosoftTokenVerifier(MicrosoftAuthProperties properties) {
        this(properties, null);
    }

    NimbusMicrosoftTokenVerifier(MicrosoftAuthProperties properties, JwtDecoder jwtDecoderOverride) {
        this.properties = properties;
        this.jwtDecoderOverride = jwtDecoderOverride;
    }

    @Override
    public MicrosoftUserClaims verifyIdToken(String idToken) {
        if (!properties.isConfigured()) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Tính năng đăng nhập bằng tài khoản Microsoft chưa được cấu hình trên máy chủ");
        }
        if (!StringUtils.hasText(idToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Thiếu idToken xác thực từ Microsoft");
        }

        Jwt jwt;
        try {
            jwt = getDecoder().decode(idToken.trim());
        } catch (JwtException ex) {
            log.warn("Khong xac thuc duoc Microsoft id_token: {}", ex.getMessage());
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Token đăng nhập Microsoft không hợp lệ hoặc đã hết hạn");
        }

        validateAudience(jwt);
        String tenantId = validateIssuerAndTenant(jwt);
        String email = extractAndValidateEmail(jwt);
        String fullName = extractFullName(jwt, email);
        String objectId = firstNonBlank(jwt.getClaimAsString("oid"), jwt.getSubject());

        return new MicrosoftUserClaims(objectId, tenantId, email, fullName);
    }

    private JwtDecoder getDecoder() {
        if (jwtDecoderOverride != null) {
            return jwtDecoderOverride;
        }
        JwtDecoder local = cachedDecoder;
        if (local == null) {
            synchronized (this) {
                local = cachedDecoder;
                if (local == null) {
                    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
                    decoder.setJwtValidator(new JwtTimestampValidator());
                    cachedDecoder = decoder;
                    local = decoder;
                }
            }
        }
        return local;
    }

    private void validateAudience(Jwt jwt) {
        String expectedClientId = properties.getClientId().trim();
        List<String> audiences = jwt.getAudience();
        if (audiences == null || !audiences.contains(expectedClientId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Token Microsoft không được phát hành cho ứng dụng thư viện PTIT");
        }
    }

    private String validateIssuerAndTenant(Jwt jwt) {
        String tid = jwt.getClaimAsString("tid");
        if (!StringUtils.hasText(tid) || !UUID_PATTERN.matcher(tid.trim()).matches()) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Token Microsoft không chứa định danh tổ chức (tid) hợp lệ");
        }
        String normalizedTid = tid.trim().toLowerCase(Locale.ROOT);
        String expectedIssuer = "https://login.microsoftonline.com/" + normalizedTid + "/v2.0";
        String actualIssuer = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
        if (!expectedIssuer.equalsIgnoreCase(actualIssuer)) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Nguồn phát hành (issuer) của token Microsoft không hợp lệ");
        }

        List<String> allowedTenants = properties.getAllowedTenantIdList();
        if (!allowedTenants.isEmpty() && !allowedTenants.contains(normalizedTid)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Tổ chức Microsoft của tài khoản này không được phép truy cập hệ thống");
        }
        return normalizedTid;
    }

    private String extractAndValidateEmail(Jwt jwt) {
        String rawEmail = firstEmailClaim(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("upn")
        );
        if (!StringUtils.hasText(rawEmail)) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Tài khoản Microsoft không cung cấp địa chỉ email hợp lệ");
        }

        String normalizedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex <= 0 || atIndex >= normalizedEmail.length() - 1) {
            throw new AppException(ErrorCode.UNAUTHORIZED,
                    "Địa chỉ email trong token Microsoft không đúng định dạng");
        }

        String domain = normalizedEmail.substring(atIndex + 1);
        List<String> allowedDomains = properties.getAllowedDomainList();
        if (!allowedDomains.isEmpty() && !allowedDomains.contains("*") && !allowedDomains.contains(domain)) {
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Chỉ chấp nhận tài khoản Microsoft thuộc miền " + String.join(", ", allowedDomains));
        }

        return normalizedEmail;
    }

    private String extractFullName(Jwt jwt, String email) {
        String name = jwt.getClaimAsString("name");
        if (StringUtils.hasText(name)) {
            return name.trim();
        }
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        String combined = ((family != null ? family.trim() : "") + " " + (given != null ? given.trim() : "")).trim();
        if (StringUtils.hasText(combined)) {
            return combined;
        }
        int atIndex = email.indexOf('@');
        return atIndex > 0 ? email.substring(0, atIndex) : email;
    }

    private static String firstEmailClaim(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && candidate.contains("@")) {
                return candidate;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }
}
