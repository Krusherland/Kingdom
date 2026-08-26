package KruDev.Kingdom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeaderboardEntry {
    private int rank;
    private String nickname;
    private int gamesWon;
    private int gamesPlayed;
    private double winRate;
}
