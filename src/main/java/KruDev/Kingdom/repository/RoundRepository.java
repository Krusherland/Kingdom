package KruDev.Kingdom.repository;

import KruDev.Kingdom.model.Game;
import KruDev.Kingdom.model.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, Long> {

    List<Round> findByGameOrderByRoundNumberAsc(Game game);

    Optional<Round> findByGameAndRoundNumber(Game game, int roundNumber);
}
