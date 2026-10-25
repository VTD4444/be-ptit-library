package vn.edu.ptit.library.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.user.dto.UpdateUserStatusRequest;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.dto.UserFilterRequest;
import vn.edu.ptit.library.user.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
@Tag(name = "Admin Users", description = "Quan ly ban doc danh cho Thu thu va Admin")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lay danh sach ban doc voi bo loc va phan trang")
    public ApiResponse<PageResponse<UserDto>> getUsers(@Valid @ModelAttribute UserFilterRequest request) {
        return ApiResponse.success(userService.searchUsers(request));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Khoa hoac mo khoa the ban doc")
    public ApiResponse<Void> updateStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        userService.updateUserStatus(id, request);
        return ApiResponse.success("Cập nhật trạng thái thẻ bạn đọc thành công", null);
    }
}
