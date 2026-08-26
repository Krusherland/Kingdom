package KruDev.Kingdom.model;

import jakarta.persistence.*;
import lombok.*;
import KruDev.Kingdom.model.enums.ActionType;
import java.time.LocalDateTime;

@Entity
@Table(name = "actions")
@Getter
@Setter
@NoArgsConstructor
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private Round round;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "actor_id", nullable = false)
    private GamePlayer actor;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_id", nullable = false)
    private GamePlayer target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType type;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
