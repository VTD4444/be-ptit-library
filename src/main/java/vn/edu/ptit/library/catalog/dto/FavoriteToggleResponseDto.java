package vn.edu.ptit.library.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class FavoriteToggleResponseDto {

    private UUID documentId;

    @JsonProperty("isFavorite")
    private boolean isFavorite;
}
