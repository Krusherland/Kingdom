package KruDev.Kingdom.repository;

import KruDev.Kingdom.model.Game;
import KruDev.Kingdom.model.GamePlayer;
import KruDev.Kingdom.model.Player;
import KruDev.Kingdom.model.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {

    List<GamePlayer> findByGame(Game game);

    List<GamePlayer> findByGameOrderByDrawOrderAsc(Game game);

    Optional<GamePlayer> findByGameAndPlayer(Game game, Player player);

    List<GamePlayer> findByGameAndAliveTrue(Game game);

    long countByGame(Game game);

    long countByGameAndAliveTrue(Game game);

    long countByGameAndRoleAndAliveTrue(Game game, Role role);

    @Query("SELECT gp FROM GamePlayer gp JOIN FETCH gp.player WHERE gp.game = :game ORDER BY gp.drawOrder ASC")
    List<GamePlayer> findByGameWithPlayerOrderByDrawOrder(@Param("game") Game game);
}
