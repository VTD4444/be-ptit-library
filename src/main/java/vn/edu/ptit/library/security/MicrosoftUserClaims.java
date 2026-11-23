package vn.edu.ptit.library.security;

public record MicrosoftUserClaims(
        String objectId,
        String tenantId,
        String email,
        String fullName
) {
}
