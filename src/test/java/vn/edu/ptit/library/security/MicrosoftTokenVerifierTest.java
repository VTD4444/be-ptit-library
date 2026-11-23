package vn.edu.ptit.library.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MicrosoftTokenVerifierTest {

    private static final String CLIENT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String PTIT_TID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private MicrosoftAuthProperties defaultProps() {
        MicrosoftAuthProperties props = new MicrosoftAuthProperties();
        props.setEnabled(true);
        props.setClientId(CLIENT_ID);
        props.setAllowedDomains("stu.ptit.edu.vn,ptit.edu.vn");
        return props;
    }

    private Jwt buildJwt(String aud, String tid, String issuer, String preferredUsername, String name) {
        Instant now = Instant.now();
        return new Jwt(
                "mock.jwt.token",
                now.minusSeconds(10),
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "aud", List.of(aud),
                        "tid", tid,
                        "iss", issuer,
                        "preferred_username", preferredUsername,
                        "name", name,
                        "oid", "99999999-8888-7777-6666-555555555555",
                        "sub", "subject-1"
                )
        );
    }

    @Test
    void verifyIdToken_validPtitStudentToken_shouldReturnClaims() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = buildJwt(
                CLIENT_ID,
                PTIT_TID,
                "https://login.microsoftonline.com/" + PTIT_TID + "/v2.0",
                "An.NV.B21DCCN001@stu.ptit.edu.vn",
                "Nguyen Van An"
        );
        when(decoder.decode("valid-token")).thenReturn(jwt);

        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(defaultProps(), decoder);
        MicrosoftUserClaims claims = verifier.verifyIdToken("valid-token");

        assertEquals("an.nv.b21dccn001@stu.ptit.edu.vn", claims.email());
        assertEquals("Nguyen Van An", claims.fullName());
        assertEquals(PTIT_TID, claims.tenantId());
        assertEquals("99999999-8888-7777-6666-555555555555", claims.objectId());
    }

    @Test
    void verifyIdToken_wrongAudience_shouldThrowUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = buildJwt(
                "other-app-client-id",
                PTIT_TID,
                "https://login.microsoftonline.com/" + PTIT_TID + "/v2.0",
                "an.nv.b21dccn001@stu.ptit.edu.vn",
                "Nguyen Van An"
        );
        when(decoder.decode("bad-aud")).thenReturn(jwt);

        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(defaultProps(), decoder);
        AppException ex = assertThrows(AppException.class, () -> verifier.verifyIdToken("bad-aud"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void verifyIdToken_mismatchedIssuerAndTenant_shouldThrowUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = buildJwt(
                CLIENT_ID,
                PTIT_TID,
                "https://login.microsoftonline.com/12345678-1234-1234-1234-1234567890ab/v2.0",
                "an.nv.b21dccn001@stu.ptit.edu.vn",
                "Nguyen Van An"
        );
        when(decoder.decode("bad-iss")).thenReturn(jwt);

        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(defaultProps(), decoder);
        AppException ex = assertThrows(AppException.class, () -> verifier.verifyIdToken("bad-iss"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void verifyIdToken_disallowedDomain_shouldThrowForbidden() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        Jwt jwt = buildJwt(
                CLIENT_ID,
                PTIT_TID,
                "https://login.microsoftonline.com/" + PTIT_TID + "/v2.0",
                "outsider@gmail.com",
                "Outsider"
        );
        when(decoder.decode("bad-domain")).thenReturn(jwt);

        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(defaultProps(), decoder);
        AppException ex = assertThrows(AppException.class, () -> verifier.verifyIdToken("bad-domain"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void verifyIdToken_notConfigured_shouldThrowInvalidRequest() {
        MicrosoftAuthProperties props = defaultProps();
        props.setClientId("");
        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(props, mock(JwtDecoder.class));

        AppException ex = assertThrows(AppException.class, () -> verifier.verifyIdToken("any-token"));
        assertEquals(ErrorCode.INVALID_REQUEST, ex.getErrorCode());
    }

    @Test
    void verifyIdToken_decoderRejectsSignature_shouldThrowUnauthorized() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("tampered")).thenThrow(new JwtException("Invalid signature"));

        NimbusMicrosoftTokenVerifier verifier = new NimbusMicrosoftTokenVerifier(defaultProps(), decoder);
        AppException ex = assertThrows(AppException.class, () -> verifier.verifyIdToken("tampered"));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
