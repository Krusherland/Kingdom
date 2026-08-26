package KruDev.Kingdom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WordGuessRequest {

    @NotBlank(message = "Word guess cannot be blank")
    private String word;
}
