package com.scrumpokinggame.model;

import java.time.Instant;
import java.util.UUID;

public final class Vote {

    private final UUID id;
    private final UUID roundId;
    private final UUID participantId;
    private String value;
    private final Instant createdAt;
    private Instant updatedAt;

    public Vote(UUID id, UUID roundId, UUID participantId, String value, Instant now) {
        this.id = id;
        this.roundId = roundId;
        this.participantId = participantId;
        this.value = value;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID roundId() {
        return roundId;
    }

    public UUID participantId() {
        return participantId;
    }

    public String value() {
        return value;
    }

    public void updateValue(String value, Instant now) {
        this.value = value;
        this.updatedAt = now;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

