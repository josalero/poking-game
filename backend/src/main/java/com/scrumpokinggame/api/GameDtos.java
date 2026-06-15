package com.scrumpokinggame.api;

import com.scrumpokinggame.model.ParticipantRole;
import com.scrumpokinggame.model.RoomStatus;
import com.scrumpokinggame.model.StoryStatus;
import com.scrumpokinggame.model.VoteRoundStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class GameDtos {

    private GameDtos() {
    }

    public record CreateRoomRequest(
            @Size(max = 80) String roomName,
            @NotBlank @Size(max = 40) String facilitatorName,
            @Size(max = 24) String facilitatorAvatarKey,
            List<String> votingScale
    ) {
    }

    public record JoinRoomRequest(
            @NotBlank @Size(max = 40) String displayName,
            ParticipantRole role,
            @Size(max = 24) String avatarKey
    ) {
    }

    public record JoinRoomResponse(
            String roomCode,
            UUID participantId,
            ParticipantRole role,
            String sessionToken,
            RoomSnapshot snapshot
    ) {
    }

    public record RoomMetadata(
            String code,
            String name,
            RoomStatus status,
            List<String> votingScale,
            Instant expiresAt
    ) {
    }

    public record RoomListItem(
            String code,
            String name,
            RoomStatus status,
            int participantCount,
            Instant createdAt
    ) {
    }

    public record RoomSnapshot(
            RoomView room,
            List<ParticipantView> participants,
            List<StoryView> stories,
            ActiveRoundView activeRound
    ) {
    }

    public record RoomView(
            String code,
            String name,
            RoomStatus status,
            List<String> votingScale,
            UUID currentStoryId,
            Instant expiresAt
    ) {
    }

    public record ParticipantView(
            UUID id,
            String displayName,
            ParticipantRole role,
            String avatarKey,
            boolean online,
            boolean hasVoted
    ) {
    }

    public record StoryView(
            UUID id,
            String title,
            String description,
            StoryStatus status,
            String finalEstimate,
            int sortOrder
    ) {
    }

    public record ActiveRoundView(
            UUID id,
            UUID storyId,
            VoteRoundStatus status,
            int roundNumber,
            Instant createdAt,
            List<VoteView> votes,
            VoteSummary summary
    ) {
    }

    public record VoteView(
            UUID participantId,
            String value
    ) {
    }

    public record VoteSummary(
            int numericCount,
            Double average,
            Double median,
            Double min,
            Double max,
            Map<String, Long> distribution,
            Map<String, Long> nonNumeric
    ) {
    }

    public record RoomSummary(
            String roomCode,
            String roomName,
            List<CompletedStorySummary> stories
    ) {
    }

    public record CompletedStorySummary(
            UUID storyId,
            String title,
            String finalEstimate,
            VoteSummary voteSummary
    ) {
    }

    public record CommandEnvelope(
            String command,
            JsonNode payload
    ) {
    }

    public record OutboundEvent(
            String event,
            Object payload
    ) {
    }

    public record StoryCreateCommand(
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description
    ) {
    }

    public record StoryUpdateCommand(
            UUID storyId,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String description
    ) {
    }

    public record StoryIdCommand(
            UUID storyId
    ) {
    }

    public record VoteCastCommand(
            UUID roundId,
            String value
    ) {
    }

    public record VoteRoundCommand(
            UUID roundId
    ) {
    }

    public record EstimateFinalizeCommand(
            UUID storyId,
            String estimate
    ) {
    }

    public record ParticipantNameCommand(
            @NotBlank @Size(max = 40) String displayName
    ) {
    }

    public record ApiError(
            String code,
            String message
    ) {
    }
}
