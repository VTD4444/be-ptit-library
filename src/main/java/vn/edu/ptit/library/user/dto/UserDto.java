package vn.edu.ptit.library.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {

    private UUID id;
    private String userCode;
    private String fullName;
    private String email;
    private String role;
    private String userType;
    private Long departmentId;
    private String departmentName;
    private Integer enrollmentYear;
    private String status;
    private OffsetDateTime cardExpiry;
}
