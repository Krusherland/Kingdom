package KruDev.Kingdom;

import KruDev.Kingdom.dto.*;
import KruDev.Kingdom.model.enums.*;
import KruDev.Kingdom.service.ActionService;
import KruDev.Kingdom.service.GameService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GameSimulationTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private ActionService actionService;

    @Test
    @DisplayName("Drill test: Play a complete 4-player match from Lobby to Game Finished")
    void testFullMatchSimulation() {
        // 1. Host creates game
        CreateGameRequest createReq = new CreateGameRequest();
        createReq.setNickname("HostPlayer");
        AuthResponse hostAuth = gameService.createGame(createReq);

        assertNotNull(hostAuth.getGameCode());
        assertNotNull(hostAuth.getSessionToken());
        String code = hostAuth.getGameCode();

        // Map to keep track of nickname -> sessionToken
        Map<String, String> tokens = new HashMap<>();
        tokens.put("HostPlayer", hostAuth.getSessionToken());

        // 2. Three other players join
        for (int i = 2; i <= 4; i++) {
            String name = "Player" + i;
            JoinGameRequest joinReq = new JoinGameRequest();
            joinReq.setNickname(name);
            AuthResponse joinAuth = gameService.joinGame(code, joinReq);
            tokens.put(name, joinAuth.getSessionToken());
        }

        // 3. Verify lobby state
        GameStateResponse state = gameService.getPublicState(code);
        assertEquals(GameStatus.LOBBY, state.getStatus());
        assertEquals(4, state.getPlayers().size());

        // 4. Host starts match
        StartGameRequest startReq = new StartGameRequest();
        startReq.setDrawingTimeSecs(30);
        gameService.startGame(code, hostAuth.getSessionToken(), startReq);

        state = gameService.getPublicState(code);
        assertEquals(GameStatus.DRAWING, state.getStatus());
        assertEquals(1, state.getCurrentRound());

        // Play 3 Rounds
        for (int r = 1; r <= 3; r++) {
            System.out.println("=== SIMULATING ROUND " + r + " ===");

            // --- DRAWING PHASE ---
            playDrawingPhase(code, tokens);

            state = gameService.getPublicState(code);
            assertEquals(GameStatus.NIGHT, state.getStatus(), "Should transition to NIGHT after all players draw");

            // --- NIGHT PHASE ---
            playNightPhase(code, tokens);

            state = gameService.getPublicState(code);
            if (r < 3) {
                assertEquals(GameStatus.DRAWING, state.getStatus(), "Should return to DRAWING for round " + (r + 1));
                assertEquals(r + 1, state.getCurrentRound());
            } else {
                assertEquals(GameStatus.WORD_GUESS, state.getStatus(), "Should transition to WORD_GUESS after Round 3 Night");
            }
        }

        // --- WORD GUESS PHASE ---
        state = gameService.getPublicState(code);
        assertEquals(GameStatus.WORD_GUESS, state.getStatus());

        // Find Outsider token
        String outsiderName = null;
        String outsiderToken = null;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            MyStateResponse myState = gameService.getMyState(code, entry.getValue());
            if (myState.getRole() == Role.OUTSIDER) {
                outsiderName = entry.getKey();
                outsiderToken = entry.getValue();
                break;
            }
        }
        assertNotNull(outsiderToken, "An Outsider must exist");

        // Outsider submits guess
        WordGuessRequest guessReq = new WordGuessRequest();
        guessReq.setWord("FakeWord"); // intentionally wrong or right
        actionService.processWordGuess(code, outsiderToken, guessReq);

        // --- FINAL VOTE PHASE (if needed) ---
        state = gameService.getPublicState(code);
        if (state.getStatus() == GameStatus.WORD_GUESS) {
            // Non-outsiders vote
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                if (!entry.getKey().equalsIgnoreCase(outsiderName)) {
                    MyStateResponse myState = gameService.getMyState(code, entry.getValue());
                    if (myState.isAlive() && myState.getFinalVoteTarget() == null) {
                        FinalVoteRequest voteReq = new FinalVoteRequest();
                        voteReq.setTargetNickname(outsiderName);
                        actionService.processFinalVote(code, entry.getValue(), voteReq);
                    }
                }
            }
        }

        // Check final status
        state = gameService.getPublicState(code);
        assertEquals(GameStatus.FINISHED, state.getStatus(), "Game should be FINISHED at the end of the match");
        System.out.println("Match successfully simulated! Winner team: " + state.getWinner());
    }

    private void playDrawingPhase(String code, Map<String, String> tokens) {
        GameStateResponse state = gameService.getPublicState(code);
        int totalPlayers = state.getPlayers().size();

        for (int i = 0; i < totalPlayers; i++) {
            state = gameService.getPublicState(code);
            if (state.getStatus() != GameStatus.DRAWING) break;

            String currentDrawer = state.getCurrentDrawerNickname();
            String drawerToken = tokens.get(currentDrawer);

            gameService.advanceDrawer(code, drawerToken);
        }
    }

    private void playNightPhase(String code, Map<String, String> tokens) {
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            GameStateResponse state = gameService.getPublicState(code);
            if (state.getStatus() != GameStatus.NIGHT) break;

            String nickname = entry.getKey();
            String token = entry.getValue();

            MyStateResponse myState = gameService.getMyState(code, token);
            if (!myState.isAlive() || myState.isHasActedThisNight()) continue;

            String primaryTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && (myState.getRole() == Role.ALCHEMIST || !p.getNickname().equals(nickname)))
                .map(p -> p.getNickname())
                .findFirst()
                .orElse(nickname);

            String voteTarget = state.getPlayers().stream()
                .filter(p -> p.isAlive() && !p.getNickname().equals(nickname))
                .map(p -> p.getNickname())
                .findFirst()
                .orElse(nickname);

            ActionRequest actionReq = new ActionRequest();

            if (myState.getRole() == Role.ALCHEMIST || myState.getRole() == Role.ROYAL_GUARD) {
                actionReq.setActionType(myState.getRole() == Role.ALCHEMIST ? ActionType.SHIELD : ActionType.REVEAL);
                actionReq.setTargetNickname(primaryTarget);
                actionReq.setVoteTargetNickname(voteTarget);
                actionService.submitAction(code, token, actionReq);
            } else if (myState.getRole() == Role.OUTSIDER) {
                actionReq.setActionType(ActionType.KILL);
                actionReq.setTargetNickname(voteTarget);
                actionService.submitAction(code, token, actionReq);
            } else {
                actionReq.setActionType(ActionType.VOTE);
                actionReq.setTargetNickname(voteTarget);
                actionService.submitAction(code, token, actionReq);
            }
        }
    }
}
