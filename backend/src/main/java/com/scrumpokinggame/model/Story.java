package com.scrumpokinggame.model;

import java.time.Instant;
import java.util.UUID;

public final class Story {

    private final UUID id;
    private final UUID roomId;
    private String title;
    private String description;
    private StoryStatus status;
    private String finalEstimate;
    private final int sortOrder;
    private final Instant createdAt;
    private Instant updatedAt;

    public Story(UUID id, UUID roomId, String title, String description, int sortOrder, Instant now) {
        this.id = id;
        this.roomId = roomId;
        this.title = title;
        this.description = description;
        this.status = StoryStatus.PENDING;
        this.sortOrder = sortOrder;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID roomId() {
        return roomId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public void update(String title, String description, Instant now) {
        this.title = title;
        this.description = description;
        this.updatedAt = now;
    }

    public StoryStatus status() {
        return status;
    }

    public void setStatus(StoryStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public String finalEstimate() {
        return finalEstimate;
    }

    public void finalizeEstimate(String finalEstimate, Instant now) {
        this.finalEstimate = finalEstimate;
        this.status = StoryStatus.ESTIMATED;
        this.updatedAt = now;
    }

    public int sortOrder() {
        return sortOrder;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}

