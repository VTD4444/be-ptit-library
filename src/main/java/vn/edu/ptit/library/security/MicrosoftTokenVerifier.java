package vn.edu.ptit.library.security;

public interface MicrosoftTokenVerifier {

    MicrosoftUserClaims verifyIdToken(String idToken);
}
