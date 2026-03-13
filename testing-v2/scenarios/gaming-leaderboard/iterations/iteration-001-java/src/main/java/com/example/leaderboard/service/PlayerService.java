package com.example.leaderboard.service;

import com.example.leaderboard.dto.CreatePlayerRequest;
import com.example.leaderboard.dto.PlayerResponse;
import com.example.leaderboard.exception.PlayerNotFoundException;
import com.example.leaderboard.model.Player;
import com.example.leaderboard.repository.PlayerRepository;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public PlayerResponse createPlayer(CreatePlayerRequest request) {
        Player player = new Player(
            request.getPlayerId(),
            request.getDisplayName(),
            request.getRegion()
        );
        Player saved = playerRepository.create(player);
        return PlayerResponse.from(saved);
    }

    public PlayerResponse getPlayer(String playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
            .orElseThrow(() -> new PlayerNotFoundException(playerId));
        return PlayerResponse.from(player);
    }
}
