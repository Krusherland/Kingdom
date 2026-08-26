package KruDev.Kingdom.dto;

import KruDev.Kingdom.model.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Generic envelope for all WebSocket broadcasts. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WsGameEvent {
    private MessageType type;
    private Object payload;
    private String gameCode;
    private long timestamp;

    public static WsGameEvent of(MessageType type, Object payload, String gameCode) {
        return new WsGameEvent(type, payload, gameCode, System.currentTimeMillis());
    }
}
