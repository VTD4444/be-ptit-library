package vn.edu.ptit.library.user.service.impl;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.library.circulation.repository.CirculationFineRepository;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.user.dto.UpdateProfileRequest;
import vn.edu.ptit.library.user.dto.UpdateUserStatusRequest;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.dto.UserFilterRequest;
import vn.edu.ptit.library.user.dto.UserProfileResponse;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;
import vn.edu.ptit.library.user.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CirculationFineRepository circulationFineRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDto getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDto> getUsers(PaginationRequest request) {
        Page<User> page = userRepository.findAll(request.toPageable());
        return PageResponse.from(page, this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Long debt = circulationFineRepository.calculateTotalUnpaidFineByUserId(userId);

        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .userCode(user.getUserCode())
                .email(user.getEmail())
                .phone(user.getPhone())
                .address(user.getAddress())
                .role(user.getRole())
                .userType(user.getUserType())
                .departmentId(user.getDepartment() != null ? user.getDepartment().getId() : null)
                .departmentName(user.getDepartment() != null ? user.getDepartment().getName() : null)
                .enrollmentYear(user.getEnrollmentYear())
                .status(user.getStatus())
                .cardExpiry(user.getCardExpiry())
                .debt(debt != null ? debt : 0L)
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }

        userRepository.save(user);
        return getProfile(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserDto> searchUsers(UserFilterRequest request) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(request.getKeyword())) {
                String pattern = "%" + request.getKeyword().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("userCode")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern)
                ));
            }

            if (request.getDepartmentId() != null) {
                predicates.add(cb.equal(root.get("department").get("id"), request.getDepartmentId()));
            }

            if (StringUtils.hasText(request.getStatus())) {
                predicates.add(cb.equal(cb.upper(root.get("status")), request.getStatus().trim().toUpperCase()));
            }

            if (StringUtils.hasText(request.getUserType())) {
                predicates.add(cb.equal(cb.upper(root.get("userType")), request.getUserType().trim().toUpperCase()));
            }

            if (StringUtils.hasText(request.getRole())) {
                predicates.add(cb.equal(cb.upper(root.get("role")), request.getRole().trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, request.toPageable());
        return PageResponse.from(page, this::toDto);
    }

    @Override
    @Transactional
    public void updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String newStatus = request.getStatus().trim().toUpperCase();
        user.setStatus(newStatus);
        if ("LOCKED".equalsIgnoreCase(newStatus) || "BLOCKED".equalsIgnoreCase(newStatus)) {
            user.setLockReason(request.getReason());
        } else if ("ACTIVE".equalsIgnoreCase(newStatus)) {
            user.setLockReason(null);
        }

        userRepository.save(user);
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
