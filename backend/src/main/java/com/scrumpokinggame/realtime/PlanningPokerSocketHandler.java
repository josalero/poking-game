package com.scrumpokinggame.realtime;

import com.scrumpokinggame.api.GameDtos.CommandEnvelope;
import com.scrumpokinggame.api.GameDtos.EstimateFinalizeCommand;
import com.scrumpokinggame.api.GameDtos.ParticipantNameCommand;
import com.scrumpokinggame.api.GameDtos.StoryCreateCommand;
import com.scrumpokinggame.api.GameDtos.StoryIdCommand;
import com.scrumpokinggame.api.GameDtos.StoryUpdateCommand;
import com.scrumpokinggame.api.GameDtos.VoteCastCommand;
import com.scrumpokinggame.api.GameDtos.VoteRoundCommand;
import com.scrumpokinggame.service.GameException;
import com.scrumpokinggame.service.PlanningPokerService;
import com.scrumpokinggame.service.SessionPrincipal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

@Component
public class PlanningPokerSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final PlanningPokerService service;
    private final RoomBroadcaster broadcaster;

    public PlanningPokerSocketHandler(ObjectMapper objectMapper, PlanningPokerService service, RoomBroadcaster broadcaster) {
        this.objectMapper = objectMapper;
        this.service = service;
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomCode = roomCode(session);
        String token = query(session).get("token");
        try {
            SessionPrincipal principal = service.authenticate(roomCode, token);
            session.getAttributes().put("roomCode", principal.roomCode());
            session.getAttributes().put("principal", principal);
            service.markOnline(principal, true);
            broadcaster.add(principal.roomCode(), session);
            broadcaster.broadcastSnapshot(principal.roomCode());
        } catch (GameException exception) {
            broadcaster.sendError(session, exception.code(), exception.getMessage());
            closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason(exception.getMessage()));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SessionPrincipal principal = (SessionPrincipal) session.getAttributes().get("principal");
        if (principal == null) {
            broadcaster.sendError(session, "unauthorized", "Socket is not authenticated.");
            return;
        }

        try {
            CommandEnvelope envelope = objectMapper.readValue(message.getPayload(), CommandEnvelope.class);
            handleCommand(principal, envelope);
            broadcaster.broadcastSnapshot(principal.roomCode());
        } catch (GameException exception) {
            broadcaster.sendError(session, exception.code(), exception.getMessage());
        } catch (Exception exception) {
            broadcaster.sendError(session, "command.invalid", "Command could not be processed.");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = (String) session.getAttributes().get("roomCode");
        SessionPrincipal principal = (SessionPrincipal) session.getAttributes().get("principal");
        if (roomCode != null) {
            broadcaster.remove(roomCode, session);
        }
        if (principal != null) {
            service.markOnline(principal, false);
            broadcaster.broadcastSnapshot(principal.roomCode());
        }
    }

    private void handleCommand(SessionPrincipal principal, CommandEnvelope envelope) throws Exception {
        if (envelope == null || envelope.command() == null) {
            throw new IllegalArgumentException("Missing command.");
        }
        switch (envelope.command()) {
            case "participant.updateName" ->
                    service.updateParticipantName(principal, objectMapper.treeToValue(envelope.payload(), ParticipantNameCommand.class));
            case "story.create" ->
                    service.createStory(principal, objectMapper.treeToValue(envelope.payload(), StoryCreateCommand.class));
            case "story.update" ->
                    service.updateStory(principal, objectMapper.treeToValue(envelope.payload(), StoryUpdateCommand.class));
            case "story.activate" ->
                    service.activateStory(principal, objectMapper.treeToValue(envelope.payload(), StoryIdCommand.class).storyId());
            case "story.skip" ->
                    service.skipStory(principal, objectMapper.treeToValue(envelope.payload(), StoryIdCommand.class).storyId());
            case "vote.cast" ->
                    service.castVote(principal, objectMapper.treeToValue(envelope.payload(), VoteCastCommand.class));
            case "vote.reveal" ->
                    service.revealVotes(principal, objectMapper.treeToValue(envelope.payload(), VoteRoundCommand.class).roundId());
            case "vote.reset" ->
                    service.resetVotes(principal, objectMapper.treeToValue(envelope.payload(), StoryIdCommand.class).storyId());
            case "estimate.finalize" ->
                    service.finalizeEstimate(principal, objectMapper.treeToValue(envelope.payload(), EstimateFinalizeCommand.class));
            case "room.end" -> service.endRoom(principal);
            default -> throw new IllegalArgumentException("Unknown command.");
        }
    }

    private String roomCode(WebSocketSession session) {
        String path = Optional.ofNullable(session.getUri()).map(uri -> uri.getPath()).orElse("");
        String[] segments = path.split("/");
        if (segments.length == 0) {
            return "";
        }
        return segments[segments.length - 1].toUpperCase();
    }

    private Map<String, String> query(WebSocketSession session) {
        String rawQuery = Optional.ofNullable(session.getUri()).map(uri -> uri.getRawQuery()).orElse("");
        if (rawQuery.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(rawQuery.split("&"))
                .map(part -> part.split("=", 2))
                .filter(parts -> parts.length == 2)
                .collect(Collectors.toMap(
                        parts -> decode(parts[0]),
                        parts -> decode(parts[1]),
                        (left, right) -> right
                ));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
            // Nothing useful to do after a failed close.
        }
    }
}
