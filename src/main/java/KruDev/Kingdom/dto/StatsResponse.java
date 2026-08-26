package KruDev.Kingdom.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsResponse {
    private String nickname;
    private int gamesPlayed;
    private int gamesWon;
    private double winRate;
    private int timesAsPlebeian;
    private int timesAsAlchemist;
    private int timesAsRoyalGuard;
    private int timesAsOutsider;
}
