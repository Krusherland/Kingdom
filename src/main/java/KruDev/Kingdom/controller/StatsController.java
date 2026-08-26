package KruDev.Kingdom.controller;

import KruDev.Kingdom.dto.LeaderboardEntry;
import KruDev.Kingdom.dto.StatsResponse;
import KruDev.Kingdom.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private static final String SESSION_HEADER = "X-Session-Token";

    private final StatsService statsService;

    /** Your personal stats (games played, win rate, times per role). */
    @GetMapping("/me")
    public ResponseEntity<StatsResponse> myStats(
            @RequestHeader(SESSION_HEADER) String sessionToken) {
        return ResponseEntity.ok(statsService.getStats(sessionToken));
    }

    /** Global leaderboard — top players by wins. */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> leaderboard(
            @RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(limit, 100);
        return ResponseEntity.ok(statsService.getLeaderboard(safeLimit));
    }
}
