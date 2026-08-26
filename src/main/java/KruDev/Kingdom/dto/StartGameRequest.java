package KruDev.Kingdom.dto;

import lombok.Data;

@Data
public class StartGameRequest {
    /** Optional custom word for innocent players. Null = random from WordBank. */
    private String innocentWord;
    /** Optional custom word for Outsiders. Null = random from WordBank. */
    private String outsiderWord;
    /** Drawing time per player in seconds. Allowed: 20, 40, 60. Defaults to 40. */
    private Integer drawingTimeSecs;
}
