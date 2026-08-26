package KruDev.Kingdom.dto;

import lombok.Data;

/** Sent by the current drawer (or host) to signal end of their drawing turn. */
@Data
public class DoneDrawingMessage {
    private String sessionToken;
}
