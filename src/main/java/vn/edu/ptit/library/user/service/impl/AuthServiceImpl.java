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
import vn.edu.ptit.library.security.MicrosoftAuthProperties;
import vn.edu.ptit.library.security.MicrosoftTokenVerifier;
import vn.edu.ptit.library.security.MicrosoftUserClaims;
import vn.edu.ptit.library.user.dto.LoginRequest;
import vn.edu.ptit.library.user.dto.MicrosoftLoginRequest;
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
import vn.edu.ptit.library.user.util.PtitEmailParser;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final MicrosoftTokenVerifier microsoftTokenVerifier;
    private final MicrosoftAuthProperties microsoftAuthProperties;

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailOrUserCode(request.getUsername(), request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (!StringUtils.hasText(user.getPasswordHash())
                || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        assertAccountActive(user);

        return issueTokenResponse(user);
    }

    @Override
    @Transactional
    public TokenResponse loginWithMicrosoft(MicrosoftLoginRequest request) {
        MicrosoftUserClaims claims = microsoftTokenVerifier.verifyIdToken(request.getIdToken());
        String email = claims.email().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    assertAccountActive(existing);
                    String cleanedName = PtitEmailParser.cleanFullName(claims.fullName());
                    if (!StringUtils.hasText(existing.getFullName()) && StringUtils.hasText(cleanedName)) {
                        existing.setFullName(cleanedName);
                        return userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> provisionMicrosoftUser(claims, email));

        return issueTokenResponse(user);
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

        assertAccountActive(user);

        return issueTokenResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        UUID currentUserId = SecurityUtils.getCurrentUserIdOrThrow();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toDto(user);
    }

    private User provisionMicrosoftUser(MicrosoftUserClaims claims, String email) {
        if (!microsoftAuthProperties.isAutoProvision()) {
            throw new AppException(ErrorCode.USER_NOT_FOUND,
                    "Tài khoản Microsoft chưa được đăng ký trong hệ thống thư viện. Vui lòng liên hệ nhân viên thư viện.");
        }

        PtitEmailParser.ParsedPtitEmail parsed = PtitEmailParser.parse(email);
        String userCode = generateUniqueUserCode(parsed.userCode());
        String cleanedName = PtitEmailParser.cleanFullName(claims.fullName());
        String fullName = StringUtils.hasText(cleanedName) ? cleanedName : userCode;

        User newUser = User.builder()
                .userCode(userCode)
                .fullName(fullName)
                .email(email)
                .passwordHash(null)
                .role(UserRole.READER.name())
                .userType(parsed.userType())
                .enrollmentYear(parsed.enrollmentYear())
                .status(UserStatus.ACTIVE.name())
                .cardExpiry(OffsetDateTime.now().plusYears(4))
                .build();

        User savedUser = userRepository.save(newUser);

        UserProfile profile = UserProfile.builder()
                .id(savedUser.getId())
                .user(savedUser)
                .personalizationEnabled(true)
                .build();
        userProfileRepository.save(profile);

        return savedUser;
    }

    private String generateUniqueUserCode(String baseCode) {
        String candidate = baseCode;
        int suffix = 1;
        while (userRepository.existsByUserCodeIgnoreCase(candidate)) {
            candidate = baseCode + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private void assertAccountActive(User user) {
        if (UserStatus.LOCKED.name().equalsIgnoreCase(user.getStatus())
                || UserStatus.INACTIVE.name().equalsIgnoreCase(user.getStatus())) {
            String reason = StringUtils.hasText(user.getLockReason())
                    ? " Lý do: " + user.getLockReason().trim()
                    : "";
            throw new AppException(ErrorCode.FORBIDDEN,
                    "Tài khoản hiện đang bị khóa hoặc ngừng hoạt động." + reason);
        }
    }

    private TokenResponse issueTokenResponse(User user) {
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
