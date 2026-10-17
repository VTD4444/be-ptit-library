package vn.edu.ptit.library.user.service;

import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.user.dto.UserDto;

import java.util.UUID;

public interface UserService {

    UserDto getById(UUID id);

    PageResponse<UserDto> getUsers(PaginationRequest request);
}
