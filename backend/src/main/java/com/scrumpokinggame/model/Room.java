package com.scrumpokinggame.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Room {

    private final UUID id;
    private final String code;
    private final String name;
    private final List<String> votingScale;
    private RoomStatus status;
    private UUID currentStoryId;
    private final Instant createdAt;
    private Instant expiresAt;
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final Map<UUID, Story> stories = new LinkedHashMap<>();
    private final Map<UUID, List<VoteRound>> voteRoundsByStory = new LinkedHashMap<>();

    public Room(UUID id, String code, String name, List<String> votingScale, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.votingScale = List.copyOf(votingScale);
        this.status = RoomStatus.OPEN;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public List<String> votingScale() {
        return votingScale;
    }

    public RoomStatus status() {
        return status;
    }

    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    public UUID currentStoryId() {
        return currentStoryId;
    }

    public void setCurrentStoryId(UUID currentStoryId) {
        this.currentStoryId = currentStoryId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public void touch(Instant now, long inactivityHours) {
        this.expiresAt = now.plus(inactivityHours, ChronoUnit.HOURS);
    }

    public Map<UUID, Participant> participants() {
        return participants;
    }

    public Map<UUID, Story> stories() {
        return stories;
    }

    public List<VoteRound> roundsForStory(UUID storyId) {
        return voteRoundsByStory.computeIfAbsent(storyId, ignored -> new ArrayList<>());
    }

    public Map<UUID, List<VoteRound>> voteRoundsByStory() {
        return voteRoundsByStory;
    }
}

