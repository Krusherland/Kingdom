package KruDev.Kingdom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGameRequest {

    @NotBlank(message = "Nickname is required")
    @Size(min = 2, max = 20, message = "Nickname must be 2–20 characters")
    private String nickname;
}
