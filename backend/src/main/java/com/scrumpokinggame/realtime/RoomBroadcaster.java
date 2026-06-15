package com.scrumpokinggame.realtime;

import com.scrumpokinggame.api.GameDtos.ApiError;
import com.scrumpokinggame.api.GameDtos.OutboundEvent;
import com.scrumpokinggame.service.PlanningPokerService;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

@Component
public class RoomBroadcaster {

    private final ObjectMapper objectMapper;
    private final PlanningPokerService service;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public RoomBroadcaster(ObjectMapper objectMapper, PlanningPokerService service) {
        this.objectMapper = objectMapper;
        this.service = service;
    }

    public void add(String roomCode, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(roomCode, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(String roomCode, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomCode);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByRoom.remove(roomCode);
            }
        }
    }

    public void sendSnapshot(WebSocketSession session, String roomCode) {
        send(session, new OutboundEvent("room.snapshot", service.snapshot(roomCode)));
    }

    public void broadcastSnapshot(String roomCode) {
        OutboundEvent event = new OutboundEvent("room.snapshot", service.snapshot(roomCode));
        Set<WebSocketSession> sessions = sessionsByRoom.getOrDefault(roomCode, Set.of());
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                send(session, event);
            }
        }
    }

    public void sendError(WebSocketSession session, String code, String message) {
        send(session, new OutboundEvent("error", new ApiError(code, message)));
    }

    private void send(WebSocketSession session, Object payload) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException ignored) {
            // The connection cleanup path will remove closed or broken sessions.
        }
    }
}
