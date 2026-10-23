package vn.edu.ptit.library.user.service;

import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.user.dto.UpdateProfileRequest;
import vn.edu.ptit.library.user.dto.UpdateUserStatusRequest;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.dto.UserFilterRequest;
import vn.edu.ptit.library.user.dto.UserProfileResponse;

import java.util.UUID;

public interface UserService {

    UserDto getById(UUID id);

    PageResponse<UserDto> getUsers(PaginationRequest request);

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    PageResponse<UserDto> searchUsers(UserFilterRequest request);

    void updateUserStatus(UUID userId, UpdateUserStatusRequest request);
}
