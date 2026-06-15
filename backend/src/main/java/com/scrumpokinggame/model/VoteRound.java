package com.scrumpokinggame.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class VoteRound {

    private final UUID id;
    private final UUID roomId;
    private final UUID storyId;
    private VoteRoundStatus status;
    private final int roundNumber;
    private Instant revealedAt;
    private final Instant createdAt;
    private final Map<UUID, Vote> votesByParticipant = new LinkedHashMap<>();

    public VoteRound(UUID id, UUID roomId, UUID storyId, int roundNumber, Instant createdAt) {
        this.id = id;
        this.roomId = roomId;
        this.storyId = storyId;
        this.status = VoteRoundStatus.VOTING;
        this.roundNumber = roundNumber;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID roomId() {
        return roomId;
    }

    public UUID storyId() {
        return storyId;
    }

    public VoteRoundStatus status() {
        return status;
    }

    public void setStatus(VoteRoundStatus status) {
        this.status = status;
    }

    public int roundNumber() {
        return roundNumber;
    }

    public Instant revealedAt() {
        return revealedAt;
    }

    public void reveal(Instant now) {
        this.status = VoteRoundStatus.REVEALED;
        this.revealedAt = now;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Map<UUID, Vote> votesByParticipant() {
        return votesByParticipant;
    }
}

