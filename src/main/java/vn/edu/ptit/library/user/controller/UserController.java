package vn.edu.ptit.library.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.common.dto.PageResponse;
import vn.edu.ptit.library.common.dto.PaginationRequest;
import vn.edu.ptit.library.user.dto.UserDto;
import vn.edu.ptit.library.user.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Quan ly nguoi dung va ban doc")
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    @Operation(summary = "Xem thong tin chi tiet nguoi dung theo ID")
    public ApiResponse<UserDto> getById(@PathVariable("id") UUID id) {
        return ApiResponse.success(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN')")
    @Operation(summary = "Danh sach nguoi dung co phan trang (Can bo / Admin)")
    public ApiResponse<PageResponse<UserDto>> getUsers(@Valid @ModelAttribute PaginationRequest request) {
        return ApiResponse.success(userService.getUsers(request));
    }
}
