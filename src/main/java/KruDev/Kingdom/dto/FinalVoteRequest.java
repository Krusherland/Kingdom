package KruDev.Kingdom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FinalVoteRequest {

    @NotBlank(message = "Target nickname is required")
    private String targetNickname;
}
