package KruDev.Kingdom.controller;

import KruDev.Kingdom.dto.DoneDrawingMessage;
import KruDev.Kingdom.dto.DrawingStrokeMessage;
import KruDev.Kingdom.dto.WsGameEvent;
import KruDev.Kingdom.model.enums.MessageType;
import KruDev.Kingdom.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class DrawingWebSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Client destination: /app/game/{code}/draw
     * Relays the stroke to all subscribers of /topic/game/{code}/drawing.
     * Rejected silently if the sender is not the current drawer.
     */
    @MessageMapping("/game/{code}/draw")
    public void handleDrawStroke(
            @DestinationVariable String code,
            @Payload DrawingStrokeMessage message) {
        try {
            gameService.validateCurrentDrawer(code, message.getSessionToken());
            // Strip the token before forwarding to prevent leaking it to other clients
            message.setSessionToken(null);
            messagingTemplate.convertAndSend("/topic/game/" + code + "/drawing", message);
        } catch (Exception ignored) {
            // Silently ignore strokes from non-drawers
        }
    }

    /**
     * Client destination: /app/game/{code}/done-drawing
     * Signals the current drawer is finished; advances to the next drawer or night phase.
     */
    @MessageMapping("/game/{code}/done-drawing")
    public void handleDoneDrawing(
            @DestinationVariable String code,
            @Payload DoneDrawingMessage message) {
        try {
            gameService.advanceDrawer(code, message.getSessionToken());
        } catch (Exception e) {
            messagingTemplate.convertAndSend(
                "/topic/game/" + code,
                WsGameEvent.of(MessageType.ERROR, e.getMessage(), code)
            );
        }
    }
}
