package com.scrumpokinggame.model;

import java.time.Instant;
import java.util.UUID;

public final class Participant {

    private final UUID id;
    private final UUID roomId;
    private String displayName;
    private final ParticipantRole role;
    private final String avatarKey;
    private boolean online;
    private final Instant joinedAt;
    private Instant lastSeenAt;

    public Participant(UUID id, UUID roomId, String displayName, ParticipantRole role, String avatarKey, Instant joinedAt) {
        this.id = id;
        this.roomId = roomId;
        this.displayName = displayName;
        this.role = role;
        this.avatarKey = avatarKey;
        this.joinedAt = joinedAt;
        this.lastSeenAt = joinedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID roomId() {
        return roomId;
    }

    public String displayName() {
        return displayName;
    }

    public void updateDisplayName(String displayName, Instant now) {
        this.displayName = displayName;
        this.lastSeenAt = now;
    }

    public ParticipantRole role() {
        return role;
    }

    public String avatarKey() {
        return avatarKey;
    }

    public boolean online() {
        return online;
    }

    public void setOnline(boolean online, Instant now) {
        this.online = online;
        this.lastSeenAt = now;
    }

    public Instant joinedAt() {
        return joinedAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }
}
