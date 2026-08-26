package KruDev.Kingdom.dto;

import KruDev.Kingdom.model.enums.GameStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GameStateResponse {
    private String gameCode;
    private GameStatus status;
    private int currentRound;
    private int totalRounds;
    private String currentDrawerNickname;
    private List<PlayerInfoResponse> players;
    /** "INNOCENTS", "OUTSIDERS", or null if still ongoing. */
    private String winner;
}
