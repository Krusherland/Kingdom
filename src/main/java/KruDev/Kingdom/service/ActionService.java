package KruDev.Kingdom.service;

import KruDev.Kingdom.dto.*;
import KruDev.Kingdom.exception.*;
import KruDev.Kingdom.model.*;
import KruDev.Kingdom.model.enums.*;
import KruDev.Kingdom.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ActionService {

    private final GameService gameService;
    private final GameRepository gameRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoundRepository roundRepository;
    private final ActionRepository actionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Night action ─────────────────────────────────────────────────────────

    public void submitAction(String code, String sessionToken, ActionRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.NIGHT) {
            throw new InvalidGameStateException("Actions can only be submitted during the night phase");
        }

        GamePlayer actor = gameService.findGamePlayer(game, sessionToken);

        if (!actor.isAlive()) {
            throw new InvalidGameStateException("Dead players cannot perform actions");
        }
        if (actor.isHasActedThisNight()) {
            throw new InvalidGameStateException("You have already acted this night");
        }

        // Validate the action type matches the player's role
        ActionType expectedType = expectedActionType(actor.getRole());
        if (request.getActionType() != expectedType) {
            throw new InvalidGameStateException(
                "Your role (" + actor.getRole() + ") must perform " + expectedType);
        }

        // Find target
        List<GamePlayer> allPlayers = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        GamePlayer target = allPlayers.stream()
            .filter(gp -> gp.getPlayer().getNickname().equalsIgnoreCase(request.getTargetNickname()))
            .findFirst()
            .orElseThrow(() -> new InvalidGameStateException(
                "Player not found: " + request.getTargetNickname()));

        if (!target.isAlive()) {
            throw new InvalidGameStateException("Cannot target a dead player");
        }
        if (target.getId().equals(actor.getId())) {
            // Alchemist may shield themselves; others may not self-target
            if (actor.getRole() != Role.ALCHEMIST) {
                throw new InvalidGameStateException("You cannot target yourself");
            }
        }
        // Outsiders cannot target other Outsiders
        if (actor.getRole() == Role.OUTSIDER && target.getRole() == Role.OUTSIDER) {
            throw new InvalidGameStateException("Outsiders cannot target other Outsiders");
        }

        Round currentRound = roundRepository.findByGameAndRoundNumber(game, game.getCurrentRound())
            .orElseThrow(() -> new InvalidGameStateException("Round not found"));

        Action action = new Action();
        action.setRound(currentRound);
        action.setActor(actor);
        action.setTarget(target);
        action.setType(request.getActionType());
        actionRepository.save(action);

        actor.setHasActedThisNight(true);
        gamePlayerRepository.save(actor);

        // Resolve night if all alive players have acted
        long aliveCount = allPlayers.stream().filter(GamePlayer::isAlive).count();
        long actedCount = allPlayers.stream().filter(gp -> gp.isAlive() && gp.isHasActedThisNight()).count();

        if (actedCount >= aliveCount) {
            resolveNight(game, currentRound, allPlayers);
        }
    }

    // ─── Word-guess phase ─────────────────────────────────────────────────────

    public boolean processWordGuess(String code, String sessionToken, WordGuessRequest request) {
        Game game = gameService.findGame(code);

        if (game.getStatus() != GameStatus.WORD_GUESS) {
            throw new InvalidGameStateException("Word guessing is not available right now");
        }

        GamePlayer me = gameService.findGamePlayer(game, sessionToken);
        if (me.isGuessedCorrectly()) {
            throw new InvalidGameStateException("You have already submitted your guess");
        }

        boolean correct = game.getInnocentWord().equalsIgnoreCase(request.getWord().trim());
        if (correct) {
            me.setScore(me.getScore() + 10);
            me.setGuessedCorrectly(true);
        }
        gamePlayerRepository.save(me);

        // Finish game once all alive players have guessed
        List<GamePlayer> alive = gameService.getAlivePlayersOrdered(game);
        boolean allGuessed = alive.stream().allMatch(GamePlayer::isGuessedCorrectly);
        if (allGuessed) {
            List<GamePlayer> all = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
            gameService.finishGame(game, all, "INNOCENTS");
        }

        return correct;
    }

    // ─── Internal resolution ──────────────────────────────────────────────────

    private void resolveNight(Game game, Round round, List<GamePlayer> allPlayers) {
        List<Action> actions = actionRepository.findByRoundWithPlayers(round);
        List<String> eliminated = new ArrayList<>();
        boolean shieldBlocked = false;

        // 1. Apply shields first
        actions.stream()
            .filter(a -> a.getType() == ActionType.SHIELD)
            .forEach(a -> a.getTarget().setShieldedThisNight(true));

        // 2. Process kills (Outsider)
        for (Action a : actions) {
            if (a.getType() == ActionType.KILL) {
                GamePlayer target = a.getTarget();
                if (target.isShieldedThisNight()) {
                    shieldBlocked = true;
                    // Award Alchemist bonus for a successful shield
                    actions.stream()
                        .filter(s -> s.getType() == ActionType.SHIELD
                                  && s.getTarget().getId().equals(target.getId()))
                        .map(Action::getActor)
                        .forEach(alchemist -> alchemist.setScore(alchemist.getScore() + 5));
                } else {
                    target.setAlive(false);
                    eliminated.add(target.getPlayer().getNickname());
                    // Award Outsider kill bonus
                    a.getActor().setScore(a.getActor().getScore() + 3);
                }
            }
        }

        // 3. Process votes (Plebeians) — most-voted unshielded player is eliminated
        Map<GamePlayer, Long> voteCounts = actions.stream()
            .filter(a -> a.getType() == ActionType.VOTE)
            .collect(Collectors.groupingBy(Action::getTarget, Collectors.counting()));

        if (!voteCounts.isEmpty()) {
            long maxVotes = Collections.max(voteCounts.values());
            List<GamePlayer> topVoted = voteCounts.entrySet().stream()
                .filter(e -> e.getValue() == maxVotes)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            // Tie → no elimination
            if (topVoted.size() == 1) {
                GamePlayer topTarget = topVoted.get(0);
                if (topTarget.isShieldedThisNight()) {
                    shieldBlocked = true;
                } else if (topTarget.isAlive()) {
                    topTarget.setAlive(false);
                    eliminated.add(topTarget.getPlayer().getNickname());

                    // Bonus if innocents voted out an Outsider
                    if (topTarget.getRole() == Role.OUTSIDER) {
                        actions.stream()
                            .filter(a -> a.getType() == ActionType.VOTE)
                            .map(Action::getActor)
                            .forEach(voter -> voter.setScore(voter.getScore() + 10));
                    }
                }
            }
        }

        // 4. Royal Guard reveal — result is read via getMyState, no extra effect here

        // Persist all GamePlayer changes (score, alive, shielded)
        gamePlayerRepository.saveAll(allPlayers);

        round.setNightComplete(true);
        roundRepository.save(round);

        // Broadcast public night result
        String nextPhase = determineNextPhase(game);
        NightResultResponse result = NightResultResponse.builder()
            .roundNumber(game.getCurrentRound())
            .eliminated(eliminated)
            .shieldUsed(shieldBlocked)
            .nextPhase(nextPhase)
            .build();
        messagingTemplate.convertAndSend(
            "/topic/game/" + game.getCode(),
            WsGameEvent.of(MessageType.NIGHT_RESULT, result, game.getCode())
        );

        // Transition to next phase
        gameService.onNightResolved(game);
    }

    private String determineNextPhase(Game game) {
        if (game.getCurrentRound() >= game.getTotalRounds()) return "WORD_GUESS";
        return "DRAWING";
    }

    private ActionType expectedActionType(Role role) {
        return switch (role) {
            case PLEBEIAN   -> ActionType.VOTE;
            case ALCHEMIST  -> ActionType.SHIELD;
            case ROYAL_GUARD -> ActionType.REVEAL;
            case OUTSIDER   -> ActionType.KILL;
        };
    }
}
