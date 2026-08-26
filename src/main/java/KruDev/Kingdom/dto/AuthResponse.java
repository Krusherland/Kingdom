package KruDev.Kingdom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String sessionToken;
    private String nickname;
    private String gameCode;
}
