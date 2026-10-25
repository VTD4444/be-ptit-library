package vn.edu.ptit.library.circulation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequestDto {

    @NotNull(message = "ID dau sach can dat truoc (documentId) khong duoc de trong")
    private UUID documentId;
}
