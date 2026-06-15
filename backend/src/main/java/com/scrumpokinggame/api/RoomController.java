package com.scrumpokinggame.api;

import com.scrumpokinggame.api.GameDtos.CreateRoomRequest;
import com.scrumpokinggame.api.GameDtos.JoinRoomRequest;
import com.scrumpokinggame.api.GameDtos.JoinRoomResponse;
import com.scrumpokinggame.api.GameDtos.RoomListItem;
import com.scrumpokinggame.api.GameDtos.RoomMetadata;
import com.scrumpokinggame.api.GameDtos.RoomSnapshot;
import com.scrumpokinggame.api.GameDtos.RoomSummary;
import java.util.List;
import com.scrumpokinggame.service.PlanningPokerService;
import com.scrumpokinggame.service.SessionPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final PlanningPokerService service;

    public RoomController(PlanningPokerService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    public List<RoomListItem> recentRooms() {
        return service.recentRooms();
    }

    @PostMapping
    public JoinRoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return service.createRoom(request);
    }

    @GetMapping("/{code}")
    public RoomMetadata metadata(@PathVariable String code) {
        return service.metadata(code);
    }

    @PostMapping("/{code}/join")
    public JoinRoomResponse joinRoom(@PathVariable String code, @Valid @RequestBody JoinRoomRequest request) {
        return service.joinRoom(code, request);
    }

    @GetMapping("/{code}/snapshot")
    public RoomSnapshot snapshot(
            @PathVariable String code,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Participant-Token", required = false) String tokenHeader
    ) {
        service.authenticate(code, firstPresent(tokenHeader, token));
        return service.snapshot(code);
    }

    @GetMapping("/{code}/summary")
    public RoomSummary summary(
            @PathVariable String code,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "X-Participant-Token", required = false) String tokenHeader
    ) {
        SessionPrincipal principal = service.authenticate(code, firstPresent(tokenHeader, token));
        return service.roomSummary(code, principal);
    }

    private String firstPresent(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
