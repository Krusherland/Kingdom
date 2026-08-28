package KruDev.Kingdom.model;

import jakarta.persistence.*;
import lombok.*;
import KruDev.Kingdom.model.enums.GameStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 8)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameStatus status = GameStatus.LOBBY;

    private String innocentWord;
    private String outsiderWord;

    private int currentRound = 0;
    private int currentDrawerIndex = 0;
    private int currentNightActorIndex = 0;
    private int totalRounds = 3;
    private int drawingTimeSecs = 40;

    @Column(nullable = false)
    private String hostSessionToken;

    /** "INNOCENTS" or "OUTSIDERS" */
    private String winner;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<GamePlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Round> rounds = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
