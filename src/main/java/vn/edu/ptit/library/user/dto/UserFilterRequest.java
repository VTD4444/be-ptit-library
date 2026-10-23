package vn.edu.ptit.library.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.edu.ptit.library.common.dto.PaginationRequest;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterRequest extends PaginationRequest {

    private String keyword;

    private Long departmentId;

    private String status;

    private String userType;

    private String role;
}
