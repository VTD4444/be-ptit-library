package vn.edu.ptit.library.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.common.enums.UserRole;
import vn.edu.ptit.library.common.enums.UserStatus;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.common.util.SecurityUtils;
import vn.edu.ptit.library.security.JwtTokenProvider;
import vn.edu.ptit.library.user.dto.LoginRequest;
import vn.edu.ptit.library.user.dto.RegisterRequest;
import vn.edu.ptit.library.user.dto.TokenResponse;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.entity.Department;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.entity.UserProfile;
import vn.edu.ptit.library.user.repository.DepartmentRepository;
import vn.edu.ptit.library.user.repository.UserProfileRepository;
import vn.edu.ptit.library.user.repository.UserRepository;
import vn.edu.ptit.library.user.service.AuthService;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailOrUserCode(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getUserCode(), user.getRole()
        );
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(toDto(user))
                .build();
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Email đã được sử dụng trong hệ thống");
        }
        if (userRepository.existsByUserCode(request.getUserCode().trim().toUpperCase())) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Mã người dùng/thẻ đã tồn tại trong hệ thống");
        }

        Department department = null;
        if (request.getDepartmentId() != null) {
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElse(null);
        }

        String userType = StringUtils.hasText(request.getUserType())
                ? request.getUserType().trim().toUpperCase()
                : "STUDENT";

        User user = User.builder()
                .userCode(request.getUserCode().trim().toUpperCase())
                .fullName(request.getFullName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.READER.name())
                .userType(userType)
                .department(department)
                .enrollmentYear(request.getEnrollmentYear())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .status(UserStatus.ACTIVE.name())
                .cardExpiry(OffsetDateTime.now().plusYears(4))
                .build();

        User savedUser = userRepository.save(user);

        // Khoi tao UserProfile 1-1
        UserProfile profile = UserProfile.builder()
                .id(savedUser.getId())
                .user(savedUser)
                .personalizationEnabled(true)
                .build();
        userProfileRepository.save(profile);

        String accessToken = tokenProvider.generateAccessToken(
                savedUser.getId(), savedUser.getEmail(), savedUser.getUserCode(), savedUser.getRole()
        );
        String refreshToken = tokenProvider.generateRefreshToken(savedUser.getId());

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(toDto(savedUser))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn");
        }

        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getUserCode(), user.getRole()
        );
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(toDto(user))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toDto(user);
    }

    private UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .userType(user.getUserType())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .enrollmentYear(user.getEnrollmentYear())
                .status(user.getStatus())
                .cardExpiry(user.getCardExpiry())
                .phone(user.getPhone())
                .address(user.getAddress())
                .lockReason(user.getLockReason())
                .build();
    }
}
