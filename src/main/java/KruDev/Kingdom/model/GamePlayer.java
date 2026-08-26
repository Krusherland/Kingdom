package KruDev.Kingdom.model;

import jakarta.persistence.*;
import lombok.*;
import KruDev.Kingdom.model.enums.Role;

@Entity
@Table(name = "game_players",
       uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "player_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GamePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean alive = true;
    private boolean shieldedThisNight = false;
    private boolean hasActedThisNight = false;
    private int drawOrder = 0;
    private int score = 0;
    private boolean guessedCorrectly = false;
}
