package vn.edu.ptit.library.user.service;

import vn.edu.ptit.library.user.dto.LoginRequest;
import vn.edu.ptit.library.user.dto.RegisterRequest;
import vn.edu.ptit.library.user.dto.TokenResponse;
import vn.edu.ptit.library.user.dto.UserDto;

public interface AuthService {

    TokenResponse login(LoginRequest request);

    TokenResponse register(RegisterRequest request);

    TokenResponse refreshToken(String refreshToken);

    UserDto getCurrentUser();
}
