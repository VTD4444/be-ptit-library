package vn.edu.ptit.library.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.ptit.library.common.enums.UserRole;
import vn.edu.ptit.library.common.enums.UserStatus;
import vn.edu.ptit.library.common.enums.UserType;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.security.JwtTokenProvider;
import vn.edu.ptit.library.security.MicrosoftAuthProperties;
import vn.edu.ptit.library.security.MicrosoftTokenVerifier;
import vn.edu.ptit.library.security.MicrosoftUserClaims;
import vn.edu.ptit.library.user.dto.LoginRequest;
import vn.edu.ptit.library.user.dto.MicrosoftLoginRequest;
import vn.edu.ptit.library.user.dto.TokenResponse;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.entity.UserProfile;
import vn.edu.ptit.library.user.repository.DepartmentRepository;
import vn.edu.ptit.library.user.repository.UserProfileRepository;
import vn.edu.ptit.library.user.repository.UserRepository;
import vn.edu.ptit.library.user.service.impl.AuthServiceImpl;
import vn.edu.ptit.library.user.util.PtitEmailParser;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MicrosoftAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private MicrosoftTokenVerifier microsoftTokenVerifier;
    @Mock
    private MicrosoftAuthProperties microsoftAuthProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void ptitEmailParser_shouldExtractStudentCodeAndEnrollmentYear() {
        var student = PtitEmailParser.parse("an.nv.b21dccn001@stu.ptit.edu.vn");
        assertEquals("B21DCCN001", student.userCode());
        assertEquals(2021, student.enrollmentYear());
        assertEquals(UserType.STUDENT.name(), student.userType());

        var directCode = PtitEmailParser.parse("n20dcat015@stu.ptit.edu.vn");
        assertEquals("N20DCAT015", directCode.userCode());
        assertEquals(2020, directCode.enrollmentYear());
        assertEquals(UserType.STUDENT.name(), directCode.userType());

        var lecturer = PtitEmailParser.parse("nguyenvana@ptit.edu.vn");
        assertEquals("NGUYENVANA", lecturer.userCode());
        assertNull(lecturer.enrollmentYear());
        assertEquals(UserType.LECTURER.name(), lecturer.userType());
    }

    @Test
    void loginWithMicrosoft_existingAdmin_shouldPreserveRoleAndIssueTokens() {
        UUID adminId = UUID.randomUUID();
        User existing = User.builder()
                .id(adminId)
                .userCode("ADMIN01")
                .fullName("Quan tri vien")
                .email("admin@ptit.edu.vn")
                .role(UserRole.ADMIN.name())
                .userType(UserType.STAFF.name())
                .status(UserStatus.ACTIVE.name())
                .build();

        when(microsoftTokenVerifier.verifyIdToken("ms-id-token"))
                .thenReturn(new MicrosoftUserClaims("oid-1", "tid-1", "admin@ptit.edu.vn", "Quan tri vien"));
        when(userRepository.findByEmailIgnoreCase("admin@ptit.edu.vn")).thenReturn(Optional.of(existing));
        when(tokenProvider.generateAccessToken(adminId, "admin@ptit.edu.vn", "ADMIN01", "ADMIN"))
                .thenReturn("access-jwt");
        when(tokenProvider.generateRefreshToken(adminId)).thenReturn("refresh-jwt");

        TokenResponse response = authService.loginWithMicrosoft(new MicrosoftLoginRequest("ms-id-token"));

        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("refresh-jwt", response.getRefreshToken());
        assertEquals("ADMIN", response.getUser().getRole());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    void loginWithMicrosoft_firstTimeStudent_shouldAutoProvisionUserAndProfile() {
        UUID generatedId = UUID.randomUUID();
        when(microsoftTokenVerifier.verifyIdToken("ms-student-token"))
                .thenReturn(new MicrosoftUserClaims("oid-2", "tid-ptit", "an.nv.b21dccn001@stu.ptit.edu.vn", "Nguyen Van An"));
        when(userRepository.findByEmailIgnoreCase("an.nv.b21dccn001@stu.ptit.edu.vn")).thenReturn(Optional.empty());
        when(microsoftAuthProperties.isAutoProvision()).thenReturn(true);
        when(userRepository.existsByUserCodeIgnoreCase("B21DCCN001")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(generatedId);
            return u;
        });
        when(tokenProvider.generateAccessToken(generatedId, "an.nv.b21dccn001@stu.ptit.edu.vn", "B21DCCN001", "READER"))
                .thenReturn("student-access");
        when(tokenProvider.generateRefreshToken(generatedId)).thenReturn("student-refresh");

        TokenResponse response = authService.loginWithMicrosoft(new MicrosoftLoginRequest("ms-student-token"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User created = userCaptor.getValue();
        assertEquals("B21DCCN001", created.getUserCode());
        assertEquals(2021, created.getEnrollmentYear());
        assertEquals("STUDENT", created.getUserType());
        assertEquals("READER", created.getRole());
        assertNull(created.getPasswordHash());

        verify(userProfileRepository).save(any(UserProfile.class));
        assertEquals("student-access", response.getAccessToken());
        assertEquals("B21DCCN001", response.getUser().getUserCode());
    }

    @Test
    void loginWithMicrosoft_lockedUser_shouldThrowForbidden() {
        User locked = User.builder()
                .id(UUID.randomUUID())
                .userCode("B21DCCN002")
                .email("locked@stu.ptit.edu.vn")
                .role("READER")
                .status(UserStatus.LOCKED.name())
                .lockReason("Qua han chua nop phat")
                .build();

        when(microsoftTokenVerifier.verifyIdToken("locked-token"))
                .thenReturn(new MicrosoftUserClaims("oid-3", "tid-ptit", "locked@stu.ptit.edu.vn", "Locked Student"));
        when(userRepository.findByEmailIgnoreCase("locked@stu.ptit.edu.vn")).thenReturn(Optional.of(locked));

        AppException ex = assertThrows(AppException.class,
                () -> authService.loginWithMicrosoft(new MicrosoftLoginRequest("locked-token")));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void loginWithPassword_ssoUserWithoutPasswordHash_shouldThrowInvalidCredentials() {
        User ssoOnly = User.builder()
                .id(UUID.randomUUID())
                .userCode("B21DCCN003")
                .email("sso@stu.ptit.edu.vn")
                .passwordHash(null)
                .status(UserStatus.ACTIVE.name())
                .build();

        when(userRepository.findByEmailOrUserCode("sso@stu.ptit.edu.vn", "sso@stu.ptit.edu.vn"))
                .thenReturn(Optional.of(ssoOnly));

        AppException ex = assertThrows(AppException.class,
                () -> authService.login(new LoginRequest("sso@stu.ptit.edu.vn", "any-pass", "PASSWORD")));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }
}
