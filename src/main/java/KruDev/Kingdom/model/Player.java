package KruDev.Kingdom.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "session_token", unique = true, nullable = false)
    private String sessionToken;

    private int gamesPlayed = 0;
    private int gamesWon = 0;
    private int timesAsPlebeian = 0;
    private int timesAsAlchemist = 0;
    private int timesAsRoyalGuard = 0;
    private int timesAsOutsider = 0;

    private LocalDateTime createdAt;
    private LocalDateTime lastSeenAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        lastSeenAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        lastSeenAt = LocalDateTime.now();
    }
}
