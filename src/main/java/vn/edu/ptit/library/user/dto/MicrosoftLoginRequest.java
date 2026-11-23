package vn.edu.ptit.library.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MicrosoftLoginRequest {

    @NotBlank(message = "idToken tu Microsoft khong duoc de trong")
    private String idToken;
}
