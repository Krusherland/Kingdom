package KruDev.Kingdom.repository;

import KruDev.Kingdom.model.Action;
import KruDev.Kingdom.model.GamePlayer;
import KruDev.Kingdom.model.Round;
import KruDev.Kingdom.model.enums.ActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActionRepository extends JpaRepository<Action, Long> {

    boolean existsByRoundAndActor(Round round, GamePlayer actor);

    Optional<Action> findByRoundAndActor(Round round, GamePlayer actor);

    List<Action> findByRound(Round round);

    /** Fetch actions with actor/target players pre-loaded to avoid N+1 in night resolution. */
    @Query("SELECT a FROM Action a " +
           "JOIN FETCH a.actor ac JOIN FETCH ac.player " +
           "JOIN FETCH a.target tg JOIN FETCH tg.player " +
           "WHERE a.round = :round")
    List<Action> findByRoundWithPlayers(@Param("round") Round round);

    /** All REVEAL actions by a specific Royal Guard across their game history. */
    @Query("SELECT a FROM Action a WHERE a.actor = :actor AND a.type = :type ORDER BY a.round.roundNumber ASC")
    List<Action> findByActorAndType(@Param("actor") GamePlayer actor, @Param("type") ActionType type);
}
