package KruDev.Kingdom.service;

import KruDev.Kingdom.dto.*;
import KruDev.Kingdom.exception.*;
import KruDev.Kingdom.model.*;
import KruDev.Kingdom.model.enums.*;
import KruDev.Kingdom.repository.*;
import KruDev.Kingdom.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final GamePlayerRepository gamePlayerRepository;
    private final RoundRepository roundRepository;
    private final ActionRepository actionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final DrawingTimerService drawingTimerService;

    // ─── Lobby ────────────────────────────────────────────────────────────────

    public AuthResponse createGame(CreateGameRequest request) {
        String sessionToken = UUID.randomUUID().toString();

        Player player = new Player();
        player.setNickname(sanitize(request.getNickname()));
        player.setSessionToken(sessionToken);
        playerRepository.save(player);

        String code = CodeGenerator.generateUniqueCode(gameRepository::existsByCode);

        Game game = new Game();
        game.setCode(code);
        game.setStatus(GameStatus.LOBBY);
        game.setHostSessionToken(sessionToken);
        gameRepository.save(game);

        GamePlayer gp = new GamePlayer();
        gp.setGame(game);
        gp.setPlayer(player);
        gp.setDrawOrder(0);
        gamePlayerRepository.save(gp);

        return new AuthResponse(sessionToken, player.getNickname(), code);
    }

    public AuthResponse joinGame(String code, JoinGameRequest request) {
        Game game = findGame(code);

        if (game.getStatus() != GameStatus.LOBBY) {
            throw new GameNotJoinableException("Game has already started");
        }

        List<GamePlayer> current = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        if (current.size() >= 8) {
            throw new GameFullException("Game is full (max 8 players)");
        }

        String sanitized = sanitize(request.getNickname());
        boolean taken = current.stream()
            .anyMatch(gp -> gp.getPlayer().getNickname().equalsIgnoreCase(sanitized));
        if (taken) {
            throw new InvalidGameStateException("Nickname already taken in this game");
        }

        String sessionToken = UUID.randomUUID().toString();

        Player player = new Player();
        player.setNickname(sanitized);
        player.setSessionToken(sessionToken);
        playerRepository.save(player);

        GamePlayer gp = new GamePlayer();
        gp.setGame(game);
        gp.setPlayer(player);
        gp.setDrawOrder(current.size());
        gamePlayerRepository.save(gp);

        broadcast(code, WsGameEvent.of(
            MessageType.PLAYER_JOINED,
            new PlayerInfoResponse(sanitized, true, false, 0),
            code
        ));

        return new AuthResponse(sessionToken, sanitized, code);
    }

    public void leaveLobby(String code, String sessionToken) {
        Game game = findGame(code);

        if (game.getStatus() != GameStatus.LOBBY) {
            throw new InvalidGameStateException("Cannot leave a game that has already started");
        }

        GamePlayer leavingGp = findGamePlayer(game, sessionToken);
        String leavingNickname = leavingGp.getPlayer().getNickname();
        boolean wasHost = game.getHostSessionToken().equals(sessionToken);

        gamePlayerRepository.delete(leavingGp);

        List<GamePlayer> remaining = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);

        if (remaining.isEmpty()) {
            gameRepository.delete(game);
            return;
        }

        // Re-index draw order and transfer host if needed
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDrawOrder(i);
        }
        gamePlayerRepository.saveAll(remaining);

        if (wasHost) {
            game.setHostSessionToken(remaining.get(0).getPlayer().getSessionToken());
            gameRepository.save(game);
        }

        broadcast(code, WsGameEvent.of(
            MessageType.PLAYER_LEFT,
            buildPublicState(game, remaining),
            code
        ));
    }

    public void startGame(String code, String sessionToken, StartGameRequest request) {
        Game game = findGame(code);
        requireHost(game, sessionToken);

        if (game.getStatus() != GameStatus.LOBBY) {
            throw new InvalidGameStateException("Game has already started");
        }

        List<GamePlayer> players = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        if (players.size() < 6) {
            throw new InvalidGameStateException(
                "Need at least 6 players to start (have " + players.size() + ")");
        }

        // Words
        String[] pair = resolveWordPair(request);
        game.setInnocentWord(pair[0]);
        game.setOutsiderWord(pair[1]);

        // Assign roles and randomise draw order
        List<Role> roles = buildRoleList(players.size());
        Collections.shuffle(roles);
        Collections.shuffle(players);
        for (int i = 0; i < players.size(); i++) {
            players.get(i).setRole(roles.get(i));
            players.get(i).setDrawOrder(i);
        }
        gamePlayerRepository.saveAll(players);

        // Pre-create all 3 round records
        for (int r = 1; r <= game.getTotalRounds(); r++) {
            Round round = new Round();
            round.setGame(game);
            round.setRoundNumber(r);
            roundRepository.save(round);
        }

        incrementRoleStats(players);

        game.setCurrentRound(1);
        game.setCurrentDrawerIndex(0);
        game.setStatus(GameStatus.DRAWING);
        int drawTime = (request != null && request.getDrawingTimeSecs() != null)
            ? Math.max(10, Math.min(120, request.getDrawingTimeSecs())) : 40;
        game.setDrawingTimeSecs(drawTime);
        gameRepository.save(game);

        broadcast(code, WsGameEvent.of(MessageType.GAME_STARTED, buildPublicState(game, players), code));
        drawingTimerService.schedule(code, drawTime);
    }

    // ─── State queries ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GameStateResponse getPublicState(String code) {
        Game game = findGame(code);
        List<GamePlayer> players = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        return buildPublicState(game, players);
    }

    @Transactional(readOnly = true)
    public MyStateResponse getMyState(String code, String sessionToken) {
        Game game = findGame(code);
        GamePlayer me = findGamePlayer(game, sessionToken);

        String word = game.getStatus() == GameStatus.LOBBY ? null
            : (me.getRole() == Role.OUTSIDER ? game.getOutsiderWord() : game.getInnocentWord());

        List<MyStateResponse.RevealResult> reveals = Collections.emptyList();
        if (me.getRole() == Role.ROYAL_GUARD) {
            reveals = actionRepository.findByActorAndType(me, ActionType.REVEAL).stream()
                .map(a -> new MyStateResponse.RevealResult(
                    a.getTarget().getPlayer().getNickname(),
                    a.getTarget().getRole(),
                    a.getRound().getRoundNumber()))
                .collect(Collectors.toList());
        }

        return MyStateResponse.builder()
            .nickname(me.getPlayer().getNickname())
            .role(me.getRole())
            .word(word)
            .alive(me.isAlive())
            .shieldedThisNight(me.isShieldedThisNight())
            .hasActedThisNight(me.isHasActedThisNight())
            .score(me.getScore())
            .guessedCorrectly(me.isGuessedCorrectly())
            .revealResults(reveals)
            .build();
    }

    // ─── Drawing phase ────────────────────────────────────────────────────────

    public void validateCurrentDrawer(String code, String sessionToken) {
        Game game = findGame(code);
        if (game.getStatus() != GameStatus.DRAWING) {
            throw new InvalidGameStateException("Game is not in drawing phase");
        }
        GamePlayer drawer = getCurrentDrawer(game);
        if (drawer == null || !drawer.getPlayer().getSessionToken().equals(sessionToken)) {
            throw new UnauthorizedActionException("You are not the current drawer");
        }
    }

    public void advanceDrawer(String code, String sessionToken) {
        Game game = findGame(code);
        if (game.getStatus() != GameStatus.DRAWING) {
            throw new InvalidGameStateException("Game is not in drawing phase");
        }

        boolean isHost = game.getHostSessionToken().equals(sessionToken);
        GamePlayer drawer = getCurrentDrawer(game);
        boolean isDrawer = drawer != null && drawer.getPlayer().getSessionToken().equals(sessionToken);
        if (!isHost && !isDrawer) {
            throw new UnauthorizedActionException("Only the host or current drawer can advance");
        }

        drawingTimerService.cancel(code);
        doAdvanceDrawer(game);
    }

    /** Called by the drawing timer when time expires for the current drawer. */
    public void advanceDrawerByTimer(String code) {
        Game game = gameRepository.findByCode(code).orElse(null);
        if (game == null || game.getStatus() != GameStatus.DRAWING) return;
        doAdvanceDrawer(game);
    }

    private void doAdvanceDrawer(Game game) {
        List<GamePlayer> alivePlayers = getAlivePlayersOrdered(game);
        int nextIdx = game.getCurrentDrawerIndex() + 1;

        if (nextIdx >= alivePlayers.size()) {
            drawingTimerService.cancel(game.getCode());
            startNightPhase(game);
        } else {
            game.setCurrentDrawerIndex(nextIdx);
            gameRepository.save(game);
            broadcast(game.getCode(), WsGameEvent.of(
                MessageType.DRAWER_CHANGED,
                alivePlayers.get(nextIdx).getPlayer().getNickname(),
                game.getCode()
            ));
            drawingTimerService.schedule(game.getCode(), game.getDrawingTimeSecs());
        }
    }

    // ─── Called by ActionService after night resolution ────────────────────────

    public void onNightResolved(Game game) {
        List<GamePlayer> players = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        String winner = checkWinCondition(players);
        if (winner != null) {
            finishGame(game, players, winner);
            return;
        }

        if (game.getCurrentRound() >= game.getTotalRounds()) {
            game.setStatus(GameStatus.WORD_GUESS);
            gameRepository.save(game);
            broadcast(game.getCode(), WsGameEvent.of(
                MessageType.WORD_GUESS_PHASE, buildPublicState(game, players), game.getCode()));
        } else {
            game.setCurrentRound(game.getCurrentRound() + 1);
            game.setCurrentDrawerIndex(0);
            game.setStatus(GameStatus.DRAWING);
            gameRepository.save(game);
            List<GamePlayer> alive = getAlivePlayersOrdered(game);
            String nextDrawer = alive.isEmpty() ? null : alive.get(0).getPlayer().getNickname();
            broadcast(game.getCode(), WsGameEvent.of(MessageType.DRAWER_CHANGED, nextDrawer, game.getCode()));
        }
    }

    public void finishGame(Game game, List<GamePlayer> players, String winner) {
        // Survivor bonus
        players.stream()
            .filter(gp -> gp.isAlive())
            .filter(gp -> "INNOCENTS".equals(winner) ? gp.getRole() != Role.OUTSIDER
                                                      : gp.getRole() == Role.OUTSIDER)
            .forEach(gp -> gp.setScore(gp.getScore() + 5));
        gamePlayerRepository.saveAll(players);

        players.forEach(gp -> {
            Player p = gp.getPlayer();
            boolean won = ("INNOCENTS".equals(winner) && gp.getRole() != Role.OUTSIDER)
                       || ("OUTSIDERS".equals(winner) && gp.getRole() == Role.OUTSIDER);
            if (won) p.setGamesWon(p.getGamesWon() + 1);
            playerRepository.save(p);
        });

        game.setStatus(GameStatus.FINISHED);
        game.setWinner(winner);
        game.setFinishedAt(LocalDateTime.now());
        gameRepository.save(game);

        broadcast(game.getCode(), WsGameEvent.of(
            MessageType.GAME_FINISHED, buildPublicState(game, players), game.getCode()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    public String checkWinCondition(List<GamePlayer> players) {
        long aliveInnocents = players.stream()
            .filter(gp -> gp.isAlive() && gp.getRole() != Role.OUTSIDER).count();
        long aliveOutsiders = players.stream()
            .filter(gp -> gp.isAlive() && gp.getRole() == Role.OUTSIDER).count();

        if (aliveInnocents == 0) return "OUTSIDERS";
        if (aliveOutsiders == 0) return "INNOCENTS";
        return null;
    }

    public Game findGame(String code) {
        return gameRepository.findByCode(code)
            .orElseThrow(() -> new GameNotFoundException(code));
    }

    public GamePlayer findGamePlayer(Game game, String sessionToken) {
        Player player = playerRepository.findBySessionToken(sessionToken)
            .orElseThrow(() -> new UnauthorizedActionException("Invalid session token"));
        return gamePlayerRepository.findByGameAndPlayer(game, player)
            .orElseThrow(() -> new UnauthorizedActionException("Player not in this game"));
    }

    public GamePlayer getCurrentDrawer(Game game) {
        List<GamePlayer> alive = getAlivePlayersOrdered(game);
        int idx = game.getCurrentDrawerIndex();
        return idx < alive.size() ? alive.get(idx) : null;
    }

    public List<GamePlayer> getAlivePlayersOrdered(Game game) {
        return gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game)
            .stream()
            .filter(GamePlayer::isAlive)
            .collect(Collectors.toList());
    }

    public void broadcast(String code, WsGameEvent event) {
        messagingTemplate.convertAndSend("/topic/game/" + code, event);
    }

    private void startNightPhase(Game game) {
        List<GamePlayer> all = gamePlayerRepository.findByGame(game);
        all.forEach(gp -> {
            gp.setHasActedThisNight(false);
            gp.setShieldedThisNight(false);
        });
        gamePlayerRepository.saveAll(all);

        game.setStatus(GameStatus.NIGHT);
        gameRepository.save(game);

        List<GamePlayer> withPlayers = gamePlayerRepository.findByGameWithPlayerOrderByDrawOrder(game);
        broadcast(game.getCode(), WsGameEvent.of(
            MessageType.NIGHT_STARTED, buildPublicState(game, withPlayers), game.getCode()));
    }

    private GameStateResponse buildPublicState(Game game, List<GamePlayer> players) {
        GamePlayer drawer = game.getStatus() == GameStatus.DRAWING ? getCurrentDrawer(game) : null;
        String drawerNick = drawer != null ? drawer.getPlayer().getNickname() : null;

        List<PlayerInfoResponse> infos = players.stream()
            .map(gp -> new PlayerInfoResponse(
                gp.getPlayer().getNickname(),
                gp.isAlive(),
                drawer != null && drawer.getId().equals(gp.getId()),
                gp.getScore()))
            .collect(Collectors.toList());

        return GameStateResponse.builder()
            .gameCode(game.getCode())
            .status(game.getStatus())
            .currentRound(game.getCurrentRound())
            .totalRounds(game.getTotalRounds())
            .currentDrawerNickname(drawerNick)
            .players(infos)
            .winner(game.getWinner())
            .drawingTimeSecs(game.getDrawingTimeSecs())
            .build();
    }

    private String[] resolveWordPair(StartGameRequest req) {
        if (req != null
            && req.getInnocentWord() != null && !req.getInnocentWord().isBlank()
            && req.getOutsiderWord() != null && !req.getOutsiderWord().isBlank()) {
            return new String[]{req.getInnocentWord().trim(), req.getOutsiderWord().trim()};
        }
        return WordBank.randomPair();
    }

    private List<Role> buildRoleList(int count) {
        int outsiders = count >= 7 ? 2 : 1;
        List<Role> roles = new ArrayList<>();
        for (int i = 0; i < outsiders; i++) roles.add(Role.OUTSIDER);
        roles.add(Role.ALCHEMIST);
        roles.add(Role.ROYAL_GUARD);
        for (int i = 0; i < count - outsiders - 2; i++) roles.add(Role.PLEBEIAN);
        return roles;
    }

    private void incrementRoleStats(List<GamePlayer> players) {
        players.forEach(gp -> {
            Player p = gp.getPlayer();
            p.setGamesPlayed(p.getGamesPlayed() + 1);
            switch (gp.getRole()) {
                case PLEBEIAN   -> p.setTimesAsPlebeian(p.getTimesAsPlebeian() + 1);
                case ALCHEMIST  -> p.setTimesAsAlchemist(p.getTimesAsAlchemist() + 1);
                case ROYAL_GUARD -> p.setTimesAsRoyalGuard(p.getTimesAsRoyalGuard() + 1);
                case OUTSIDER   -> p.setTimesAsOutsider(p.getTimesAsOutsider() + 1);
            }
            playerRepository.save(p);
        });
    }

    private void requireHost(Game game, String sessionToken) {
        if (!game.getHostSessionToken().equals(sessionToken)) {
            throw new UnauthorizedActionException("Only the host can perform this action");
        }
    }

    /** Strip HTML-injectable characters from user-supplied strings. */
    private String sanitize(String input) {
        return input == null ? "" : input.trim().replaceAll("[<>\"'&;]", "");
    }
}
