package KruDev.Kingdom.repository;

import KruDev.Kingdom.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Optional<Player> findBySessionToken(String sessionToken);

    boolean existsBySessionToken(String sessionToken);

    /** Top players by win count for the leaderboard. */
    @Query("SELECT p FROM Player p ORDER BY p.gamesWon DESC, p.gamesPlayed ASC")
    List<Player> findTopPlayers(org.springframework.data.domain.Pageable pageable);
}
