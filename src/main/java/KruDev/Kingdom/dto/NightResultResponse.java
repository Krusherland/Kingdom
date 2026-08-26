package KruDev.Kingdom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Broadcast after all night actions are resolved. */
@Data
@Builder
public class NightResultResponse {
    private int roundNumber;
    /** Nicknames of players eliminated this night (by kill or vote). */
    private List<String> eliminated;
    /** Whether an Alchemist shield blocked at least one elimination. */
    private boolean shieldUsed;
    private String nextPhase;
}
