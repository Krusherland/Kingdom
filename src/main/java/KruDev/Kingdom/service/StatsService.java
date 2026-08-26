package KruDev.Kingdom.service;

import KruDev.Kingdom.dto.LeaderboardEntry;
import KruDev.Kingdom.dto.StatsResponse;
import KruDev.Kingdom.exception.UnauthorizedActionException;
import KruDev.Kingdom.model.Player;
import KruDev.Kingdom.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StatsService {

    private final PlayerRepository playerRepository;

    public StatsResponse getStats(String sessionToken) {
        Player p = playerRepository.findBySessionToken(sessionToken)
            .orElseThrow(() -> new UnauthorizedActionException("Invalid session token"));
        return toStats(p);
    }

    public List<LeaderboardEntry> getLeaderboard(int limit) {
        List<Player> top = playerRepository.findTopPlayers(PageRequest.of(0, limit));
        return IntStream.range(0, top.size())
            .mapToObj(i -> {
                Player p = top.get(i);
                return new LeaderboardEntry(
                    i + 1,
                    p.getNickname(),
                    p.getGamesWon(),
                    p.getGamesPlayed(),
                    winRate(p)
                );
            })
            .toList();
    }

    private StatsResponse toStats(Player p) {
        return StatsResponse.builder()
            .nickname(p.getNickname())
            .gamesPlayed(p.getGamesPlayed())
            .gamesWon(p.getGamesWon())
            .winRate(winRate(p))
            .timesAsPlebeian(p.getTimesAsPlebeian())
            .timesAsAlchemist(p.getTimesAsAlchemist())
            .timesAsRoyalGuard(p.getTimesAsRoyalGuard())
            .timesAsOutsider(p.getTimesAsOutsider())
            .build();
    }

    private double winRate(Player p) {
        if (p.getGamesPlayed() == 0) return 0.0;
        return Math.round((p.getGamesWon() * 100.0 / p.getGamesPlayed()) * 10.0) / 10.0;
    }
}
