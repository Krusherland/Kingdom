package KruDev.Kingdom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerInfoResponse {
    private String nickname;
    private boolean alive;
    private boolean currentDrawer;
    private int score;
    private boolean hasActedThisNight;
}
