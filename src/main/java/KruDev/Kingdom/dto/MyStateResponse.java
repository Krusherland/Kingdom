package KruDev.Kingdom.dto;

import KruDev.Kingdom.model.enums.Role;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyStateResponse {
    private String nickname;
    private Role role;
    /** The word assigned to this player (innocent word or outsider word). */
    private String word;
    private boolean alive;
    private boolean shieldedThisNight;
    private boolean hasActedThisNight;
    private boolean hasUsedSpecialAbility;
    private int score;
    private boolean guessedCorrectly;
    private boolean hasActedFinalPhase;
    private String finalVoteTarget;
    /** Populated for ROYAL_GUARD only — results of their past REVEAL actions. */
    private List<RevealResult> revealResults;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RevealResult {
        private String targetNickname;
        private Role revealedRole;
        private int roundNumber;
    }
}
