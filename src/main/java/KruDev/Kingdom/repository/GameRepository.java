package KruDev.Kingdom.repository;

import KruDev.Kingdom.model.Game;
import KruDev.Kingdom.model.enums.GameStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {

    Optional<Game> findByCode(String code);

    boolean existsByCode(String code);

    long countByStatus(GameStatus status);
}
