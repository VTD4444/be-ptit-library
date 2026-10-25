package vn.edu.ptit.library.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.library.common.dto.ApiResponse;
import vn.edu.ptit.library.user.dto.DepartmentDto;
import vn.edu.ptit.library.user.service.DepartmentService;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Danh muc khoa va phong ban")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "Lay danh muc Khoa/Phong ban")
    public ApiResponse<List<DepartmentDto>> getAllDepartments() {
        return ApiResponse.success(departmentService.getAllDepartments());
    }
}
