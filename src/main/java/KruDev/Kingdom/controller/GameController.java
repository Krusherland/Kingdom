package KruDev.Kingdom.controller;

import KruDev.Kingdom.dto.*;
import KruDev.Kingdom.service.ActionService;
import KruDev.Kingdom.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private static final String SESSION_HEADER = "X-Session-Token";

    private final GameService gameService;
    private final ActionService actionService;

    /** Create a new game lobby. Returns session token + room code. */
    @PostMapping
    public ResponseEntity<AuthResponse> createGame(@Valid @RequestBody CreateGameRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(req));
    }

    /** Join an existing lobby. */
    @PostMapping("/{code}/join")
    public ResponseEntity<AuthResponse> joinGame(
            @PathVariable String code,
            @Valid @RequestBody JoinGameRequest req) {
        return ResponseEntity.ok(gameService.joinGame(code, req));
    }

    /** Start the game (host only). Optionally supply custom word pair. */
    @PostMapping("/{code}/start")
    public ResponseEntity<Void> startGame(
            @PathVariable String code,
            @RequestHeader(SESSION_HEADER) String sessionToken,
            @RequestBody(required = false) StartGameRequest req) {
        gameService.startGame(code, sessionToken, req);
        return ResponseEntity.ok().build();
    }

    /** Public game state (player list, current drawer, status). */
    @GetMapping("/{code}/state")
    public ResponseEntity<GameStateResponse> getState(@PathVariable String code) {
        return ResponseEntity.ok(gameService.getPublicState(code));
    }

    /** Private player state (role, word, reveal results). */
    @GetMapping("/{code}/me")
    public ResponseEntity<MyStateResponse> getMyState(
            @PathVariable String code,
            @RequestHeader(SESSION_HEADER) String sessionToken) {
        return ResponseEntity.ok(gameService.getMyState(code, sessionToken));
    }

    /** Advance to the next drawer (host or current drawer). */
    @PostMapping("/{code}/advance-drawer")
    public ResponseEntity<Void> advanceDrawer(
            @PathVariable String code,
            @RequestHeader(SESSION_HEADER) String sessionToken) {
        gameService.advanceDrawer(code, sessionToken);
        return ResponseEntity.ok().build();
    }

    /** Submit a night action (VOTE / SHIELD / REVEAL / KILL). */
    @PostMapping("/{code}/action")
    public ResponseEntity<Void> submitAction(
            @PathVariable String code,
            @RequestHeader(SESSION_HEADER) String sessionToken,
            @Valid @RequestBody ActionRequest req) {
        actionService.submitAction(code, sessionToken, req);
        return ResponseEntity.ok().build();
    }

    /** Submit word guess during the final phase. */
    @PostMapping("/{code}/guess")
    public ResponseEntity<Map<String, Boolean>> submitGuess(
            @PathVariable String code,
            @RequestHeader(SESSION_HEADER) String sessionToken,
            @Valid @RequestBody WordGuessRequest req) {
        boolean correct = actionService.processWordGuess(code, sessionToken, req);
        return ResponseEntity.ok(Map.of("correct", correct));
    }
}
