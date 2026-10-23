package vn.edu.ptit.library.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.user.dto.LoginRequest;
import vn.edu.ptit.library.user.dto.RegisterRequest;
import vn.edu.ptit.library.user.dto.TokenResponse;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.service.AuthService;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Xac thuc va phan quyen nguoi dung")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Dang ky tai khoan ban doc moi")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.created("Dang ky tai khoan thanh cong", authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Dang nhap bang email hoac ma ban doc")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("Dang nhap thanh cong", authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Lam moi Access Token bang Refresh Token")
    public ApiResponse<TokenResponse> refreshToken(@RequestParam("token") String token) {
        return ApiResponse.success("Lam moi token thanh cong", authService.refreshToken(token));
    }

    @GetMapping("/me")
    @Operation(summary = "Lay thong tin nguoi dung hien tai")
    public ApiResponse<UserDto> getCurrentUser() {
        return ApiResponse.success(authService.getCurrentUser());
    }
}
