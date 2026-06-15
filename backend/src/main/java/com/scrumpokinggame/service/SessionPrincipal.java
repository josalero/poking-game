package com.scrumpokinggame.service;

import com.scrumpokinggame.model.ParticipantRole;
import java.util.UUID;

public record SessionPrincipal(
        String roomCode,
        UUID roomId,
        UUID participantId,
        ParticipantRole role
) {
}

