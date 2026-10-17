package vn.edu.ptit.library.user.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.entity.User;
import vn.edu.ptit.library.user.repository.UserRepository;
import vn.edu.ptit.library.user.service.UserService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

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
                .build();
    }
}
