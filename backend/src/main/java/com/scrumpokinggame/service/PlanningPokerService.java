package com.scrumpokinggame.service;

import static com.scrumpokinggame.api.GameDtos.ActiveRoundView;
import static com.scrumpokinggame.api.GameDtos.CompletedStorySummary;
import static com.scrumpokinggame.api.GameDtos.CreateRoomRequest;
import static com.scrumpokinggame.api.GameDtos.EstimateFinalizeCommand;
import static com.scrumpokinggame.api.GameDtos.JoinRoomRequest;
import static com.scrumpokinggame.api.GameDtos.JoinRoomResponse;
import static com.scrumpokinggame.api.GameDtos.ParticipantNameCommand;
import static com.scrumpokinggame.api.GameDtos.ParticipantView;
import static com.scrumpokinggame.api.GameDtos.RoomListItem;
import static com.scrumpokinggame.api.GameDtos.RoomMetadata;
import static com.scrumpokinggame.api.GameDtos.RoomSnapshot;
import static com.scrumpokinggame.api.GameDtos.RoomSummary;
import static com.scrumpokinggame.api.GameDtos.RoomView;
import static com.scrumpokinggame.api.GameDtos.StoryCreateCommand;
import static com.scrumpokinggame.api.GameDtos.StoryUpdateCommand;
import static com.scrumpokinggame.api.GameDtos.StoryView;
import static com.scrumpokinggame.api.GameDtos.VoteCastCommand;
import static com.scrumpokinggame.api.GameDtos.VoteSummary;
import static com.scrumpokinggame.api.GameDtos.VoteView;

import com.scrumpokinggame.model.Participant;
import com.scrumpokinggame.model.ParticipantRole;
import com.scrumpokinggame.model.Room;
import com.scrumpokinggame.model.RoomStatus;
import com.scrumpokinggame.model.Story;
import com.scrumpokinggame.model.StoryStatus;
import com.scrumpokinggame.model.Vote;
import com.scrumpokinggame.model.VoteRound;
import com.scrumpokinggame.model.VoteRoundStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlanningPokerService {

    private static final List<String> DEFAULT_SCALE = List.of("0", "1", "2", "3", "5", "8", "13", "21", "?", "coffee");
    private static final List<String> AVATAR_KEYS = List.of("avatar-1", "avatar-2", "avatar-3", "avatar-4", "avatar-5", "avatar-6");
    private static final char[] ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final long roomInactivityHours;
    private final Map<String, Room> roomsByCode = new ConcurrentHashMap<>();
    private final Map<String, SessionPrincipal> principalsByToken = new ConcurrentHashMap<>();

    @Autowired
    public PlanningPokerService(@Value("${scrum-poking.room-inactivity-hours:1}") long roomInactivityHours) {
        this(Clock.systemUTC(), roomInactivityHours);
    }

    PlanningPokerService(Clock clock, long roomInactivityHours) {
        this.clock = clock;
        this.roomInactivityHours = roomInactivityHours;
    }

    public synchronized JoinRoomResponse createRoom(CreateRoomRequest request) {
        Instant now = now();
        String roomName = cleanDefault(request.roomName(), "Planning poker", 80);
        String facilitatorName = cleanRequired(request.facilitatorName(), "facilitatorName", 40);
        String facilitatorAvatarKey = cleanAvatar(request.facilitatorAvatarKey(), 0);
        List<String> votingScale = normalizeScale(request.votingScale());
        ensureRoomNameAvailable(roomName);

        Room room = new Room(
                UUID.randomUUID(),
                uniqueRoomCode(),
                roomName,
                votingScale,
                now,
                now.plus(roomInactivityHours, ChronoUnit.HOURS)
        );
        Participant facilitator = new Participant(UUID.randomUUID(), room.id(), facilitatorName, ParticipantRole.FACILITATOR, facilitatorAvatarKey, now);
        facilitator.setOnline(true, now);
        room.participants().put(facilitator.id(), facilitator);
        roomsByCode.put(room.code(), room);

        String token = createSessionToken(room, facilitator);
        return new JoinRoomResponse(room.code(), facilitator.id(), facilitator.role(), token, snapshot(room.code()));
    }

    public synchronized List<RoomListItem> recentRooms() {
        purgeInactiveRooms();
        return roomsByCode.values().stream()
                .sorted(Comparator.comparing(Room::createdAt).reversed())
                .limit(12)
                .map(room -> new RoomListItem(
                        room.code(),
                        room.name(),
                        room.status(),
                        room.participants().size(),
                        room.createdAt()
                ))
                .toList();
    }

    public synchronized RoomMetadata metadata(String roomCode) {
        Room room = room(roomCode);
        return new RoomMetadata(room.code(), room.name(), room.status(), room.votingScale(), room.expiresAt());
    }

    public synchronized JoinRoomResponse joinRoom(String roomCode, JoinRoomRequest request) {
        Room room = requireOpenRoom(roomCode);
        Instant now = now();
        String displayName = cleanRequired(request.displayName(), "displayName", 40);
        ParticipantRole role = request.role() == ParticipantRole.OBSERVER ? ParticipantRole.OBSERVER : ParticipantRole.VOTER;
        String avatarKey = cleanAvatar(request.avatarKey(), room.participants().size());

        Participant participant = new Participant(UUID.randomUUID(), room.id(), displayName, role, avatarKey, now);
        participant.setOnline(true, now);
        room.participants().put(participant.id(), participant);
        String token = createSessionToken(room, participant);
        return new JoinRoomResponse(room.code(), participant.id(), participant.role(), token, snapshot(room.code()));
    }

    public synchronized SessionPrincipal authenticate(String roomCode, String token) {
        if (token == null || token.isBlank()) {
            throw notAuthorized("Missing participant token.");
        }
        SessionPrincipal principal = principalsByToken.get(token);
        if (principal == null || !principal.roomCode().equalsIgnoreCase(normalizeRoomCode(roomCode))) {
            throw notAuthorized("Invalid participant token.");
        }
        Room room = requireOpenRoom(roomCode);
        if (!room.participants().containsKey(principal.participantId())) {
            throw notAuthorized("Participant is no longer in the room.");
        }
        return principal;
    }

    public synchronized void markOnline(SessionPrincipal principal, boolean online) {
        Room room = room(principal.roomCode());
        Participant participant = participant(room, principal.participantId());
        participant.setOnline(online, now());
    }

    public synchronized RoomSnapshot snapshot(String roomCode) {
        Room room = room(roomCode);
        VoteRound activeRound = activeRound(room).orElse(null);
        Map<UUID, Vote> activeVotes = activeRound == null ? Map.of() : activeRound.votesByParticipant();
        boolean revealed = activeRound != null && activeRound.status() == VoteRoundStatus.REVEALED;

        List<ParticipantView> participants = room.participants().values().stream()
                .sorted(Comparator.comparing(Participant::joinedAt))
                .map(participant -> new ParticipantView(
                        participant.id(),
                        participant.displayName(),
                        participant.role(),
                        participant.avatarKey(),
                        participant.online(),
                        activeVotes.containsKey(participant.id())
                ))
                .toList();

        List<StoryView> stories = room.stories().values().stream()
                .sorted(Comparator.comparingInt(Story::sortOrder))
                .map(story -> new StoryView(
                        story.id(),
                        story.title(),
                        story.description(),
                        story.status(),
                        story.finalEstimate(),
                        story.sortOrder()
                ))
                .toList();

        ActiveRoundView activeRoundView = null;
        if (activeRound != null) {
            List<VoteView> votes = revealed
                    ? activeRound.votesByParticipant().values().stream()
                            .map(vote -> new VoteView(vote.participantId(), vote.value()))
                            .toList()
                    : List.of();
            VoteSummary summary = revealed ? summarize(activeRound.votesByParticipant().values().stream().map(Vote::value).toList()) : null;
            activeRoundView = new ActiveRoundView(
                    activeRound.id(),
                    activeRound.storyId(),
                    activeRound.status(),
                    activeRound.roundNumber(),
                    activeRound.createdAt(),
                    votes,
                    summary
            );
        }

        RoomView roomView = new RoomView(
                room.code(),
                room.name(),
                room.status(),
                room.votingScale(),
                room.currentStoryId(),
                room.expiresAt()
        );
        return new RoomSnapshot(roomView, participants, stories, activeRoundView);
    }

    public synchronized RoomSummary roomSummary(String roomCode, SessionPrincipal principal) {
        Room room = room(principal.roomCode());
        List<CompletedStorySummary> stories = room.stories().values().stream()
                .filter(story -> story.status() == StoryStatus.ESTIMATED)
                .sorted(Comparator.comparingInt(Story::sortOrder))
                .map(story -> new CompletedStorySummary(
                        story.id(),
                        story.title(),
                        story.finalEstimate(),
                        latestRevealedRound(room, story.id())
                                .map(round -> summarize(round.votesByParticipant().values().stream().map(Vote::value).toList()))
                                .orElse(null)
                ))
                .toList();
        return new RoomSummary(room.code(), room.name(), stories);
    }

    public synchronized void updateParticipantName(SessionPrincipal principal, ParticipantNameCommand command) {
        Room room = requireOpenRoom(principal.roomCode());
        participant(room, principal.participantId()).updateDisplayName(cleanRequired(command.displayName(), "displayName", 40), now());
    }

    public synchronized void createStory(SessionPrincipal principal, StoryCreateCommand command) {
        Room room = requireFacilitator(principal);
        Instant now = now();
        String title = cleanRequired(command.title(), "title", 160);
        String description = cleanOptional(command.description(), 2000);
        int sortOrder = room.stories().values().stream().mapToInt(Story::sortOrder).max().orElse(0) + 1;
        Story story = new Story(UUID.randomUUID(), room.id(), title, description, sortOrder, now);
        room.stories().put(story.id(), story);
    }

    public synchronized void updateStory(SessionPrincipal principal, StoryUpdateCommand command) {
        Room room = requireFacilitator(principal);
        Story story = story(room, command.storyId());
        if (story.status() == StoryStatus.ESTIMATED) {
            throw badRequest("story.finalized", "Finalized stories cannot be edited.");
        }
        story.update(cleanRequired(command.title(), "title", 160), cleanOptional(command.description(), 2000), now());
    }

    public synchronized void activateStory(SessionPrincipal principal, UUID storyId) {
        Room room = requireFacilitator(principal);
        Story story = story(room, storyId);
        if (story.status() == StoryStatus.ESTIMATED) {
            throw badRequest("story.finalized", "Finalized stories cannot be activated.");
        }
        if (story.status() == StoryStatus.SKIPPED) {
            throw badRequest("story.skipped", "Skipped stories cannot be activated.");
        }
        activateStoryInternal(room, story, now());
    }

    public synchronized void skipStory(SessionPrincipal principal, UUID storyId) {
        Room room = requireFacilitator(principal);
        Story story = story(room, storyId);
        Instant now = now();
        story.setStatus(StoryStatus.SKIPPED, now);
        activeRoundForStory(room, story.id()).ifPresent(round -> round.setStatus(VoteRoundStatus.CANCELLED));
        if (story.id().equals(room.currentStoryId())) {
            room.setCurrentStoryId(null);
        }
    }

    public synchronized void castVote(SessionPrincipal principal, VoteCastCommand command) {
        Room room = requireOpenRoom(principal.roomCode());
        if (principal.role() == ParticipantRole.OBSERVER) {
            throw forbidden("Observers cannot vote.");
        }
        if (command.roundId() == null) {
            throw badRequest("vote.roundRequired", "roundId is required.");
        }
        String value = cleanRequired(command.value(), "value", 40);
        if (!room.votingScale().contains(value)) {
            throw badRequest("vote.invalidValue", "Vote value is not in this room's voting scale.");
        }
        VoteRound round = activeRound(room)
                .filter(candidate -> candidate.id().equals(command.roundId()))
                .orElseThrow(() -> badRequest("vote.roundInactive", "Vote round is not active."));
        if (round.status() != VoteRoundStatus.VOTING) {
            throw badRequest("vote.roundClosed", "This vote round is no longer accepting votes.");
        }

        Instant now = now();
        Vote existingVote = round.votesByParticipant().get(principal.participantId());
        if (existingVote == null) {
            round.votesByParticipant().put(
                    principal.participantId(),
                    new Vote(UUID.randomUUID(), round.id(), principal.participantId(), value, now)
            );
        } else {
            existingVote.updateValue(value, now);
        }
    }

    public synchronized void revealVotes(SessionPrincipal principal, UUID roundId) {
        Room room = requireFacilitator(principal);
        VoteRound round = activeRound(room)
                .filter(candidate -> candidate.id().equals(roundId))
                .orElseThrow(() -> badRequest("vote.roundInactive", "Vote round is not active."));
        if (round.status() != VoteRoundStatus.VOTING) {
            throw badRequest("vote.roundClosed", "This vote round cannot be revealed.");
        }
        round.reveal(now());
    }

    public synchronized void resetVotes(SessionPrincipal principal, UUID storyId) {
        Room room = requireFacilitator(principal);
        Story story = story(room, storyId);
        if (!story.id().equals(room.currentStoryId())) {
            activateStoryInternal(room, story, now());
            return;
        }
        activeRoundForStory(room, story.id()).ifPresent(round -> round.setStatus(VoteRoundStatus.CANCELLED));
        room.roundsForStory(story.id()).add(newRound(room, story, now()));
        story.setStatus(StoryStatus.ACTIVE, now());
    }

    public synchronized void finalizeEstimate(SessionPrincipal principal, EstimateFinalizeCommand command) {
        Room room = requireFacilitator(principal);
        Story story = story(room, command.storyId());
        VoteRound round = activeRoundForStory(room, story.id())
                .orElseThrow(() -> badRequest("estimate.noRound", "Story does not have an active vote round."));
        if (round.status() != VoteRoundStatus.REVEALED) {
            throw badRequest("estimate.notRevealed", "Votes must be revealed before finalizing an estimate.");
        }
        String estimate = cleanRequired(command.estimate(), "estimate", 40);
        story.finalizeEstimate(estimate, now());
        room.setCurrentStoryId(null);
    }

    public synchronized void endRoom(SessionPrincipal principal) {
        Room room = requireFacilitator(principal);
        room.setStatus(RoomStatus.ENDED);
        room.participants().values().forEach(participant -> participant.setOnline(false, now()));
    }

    private void activateStoryInternal(Room room, Story story, Instant now) {
        if (room.currentStoryId() != null && !room.currentStoryId().equals(story.id())) {
            Story current = room.stories().get(room.currentStoryId());
            if (current != null && current.status() == StoryStatus.ACTIVE) {
                current.setStatus(StoryStatus.PENDING, now);
            }
        }
        room.setCurrentStoryId(story.id());
        story.setStatus(StoryStatus.ACTIVE, now);
        if (activeRoundForStory(room, story.id()).isEmpty()) {
            room.roundsForStory(story.id()).add(newRound(room, story, now));
        }
    }

    private VoteRound newRound(Room room, Story story, Instant now) {
        int roundNumber = room.roundsForStory(story.id()).size() + 1;
        return new VoteRound(UUID.randomUUID(), room.id(), story.id(), roundNumber, now);
    }

    private Optional<VoteRound> activeRound(Room room) {
        if (room.currentStoryId() == null) {
            return Optional.empty();
        }
        return activeRoundForStory(room, room.currentStoryId());
    }

    private Optional<VoteRound> activeRoundForStory(Room room, UUID storyId) {
        List<VoteRound> rounds = room.voteRoundsByStory().getOrDefault(storyId, List.of());
        for (int i = rounds.size() - 1; i >= 0; i--) {
            VoteRound round = rounds.get(i);
            if (round.status() != VoteRoundStatus.CANCELLED) {
                return Optional.of(round);
            }
        }
        return Optional.empty();
    }

    private Optional<VoteRound> latestRevealedRound(Room room, UUID storyId) {
        List<VoteRound> rounds = room.voteRoundsByStory().getOrDefault(storyId, List.of());
        for (int i = rounds.size() - 1; i >= 0; i--) {
            VoteRound round = rounds.get(i);
            if (round.status() == VoteRoundStatus.REVEALED) {
                return Optional.of(round);
            }
        }
        return Optional.empty();
    }

    private VoteSummary summarize(List<String> values) {
        Map<String, Long> distribution = values.stream()
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> nonNumeric = values.stream()
                .filter(value -> parseNumber(value).isEmpty())
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
        List<Double> numeric = values.stream()
                .map(this::parseNumber)
                .flatMap(Optional::stream)
                .sorted()
                .toList();
        if (numeric.isEmpty()) {
            return new VoteSummary(0, null, null, null, null, distribution, nonNumeric);
        }
        DoubleSummaryStatistics stats = numeric.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double median = median(numeric);
        return new VoteSummary(
                numeric.size(),
                roundOneDecimal(stats.getAverage()),
                roundOneDecimal(median),
                stats.getMin(),
                stats.getMax(),
                distribution,
                nonNumeric
        );
    }

    private double median(List<Double> values) {
        int size = values.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Optional<Double> parseNumber(String value) {
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private Room requireFacilitator(SessionPrincipal principal) {
        Room room = requireOpenRoom(principal.roomCode());
        if (principal.role() != ParticipantRole.FACILITATOR) {
            throw forbidden("Only the facilitator can perform this action.");
        }
        return room;
    }

    private Room requireOpenRoom(String roomCode) {
        Room room = room(roomCode);
        if (room.status() != RoomStatus.OPEN) {
            throw badRequest("room.closed", "Room is not open.");
        }
        return room;
    }

    private Room room(String roomCode) {
        String code = normalizeRoomCode(roomCode);
        Room room = roomsByCode.get(code);
        if (room == null) {
            throw new GameException(HttpStatus.NOT_FOUND, "room.notFound", "Room not found.");
        }
        if (isInactive(room)) {
            removeRoom(room);
            throw new GameException(HttpStatus.NOT_FOUND, "room.notFound", "Room not found.");
        }
        touchRoom(room);
        return room;
    }

    private Participant participant(Room room, UUID participantId) {
        Participant participant = room.participants().get(participantId);
        if (participant == null) {
            throw notAuthorized("Participant is not part of this room.");
        }
        return participant;
    }

    private Story story(Room room, UUID storyId) {
        if (storyId == null) {
            throw badRequest("story.required", "storyId is required.");
        }
        Story story = room.stories().get(storyId);
        if (story == null) {
            throw new GameException(HttpStatus.NOT_FOUND, "story.notFound", "Story not found.");
        }
        return story;
    }

    private void purgeInactiveRooms() {
        for (Room room : List.copyOf(roomsByCode.values())) {
            if (isInactive(room)) {
                removeRoom(room);
            }
        }
    }

    private void touchRoom(Room room) {
        room.touch(now(), roomInactivityHours);
    }

    private boolean isInactive(Room room) {
        return now().isAfter(room.expiresAt());
    }

    private void removeRoom(Room room) {
        roomsByCode.remove(room.code());
        principalsByToken.entrySet().removeIf(entry -> entry.getValue().roomCode().equalsIgnoreCase(room.code()));
    }

    private String createSessionToken(Room room, Participant participant) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        principalsByToken.put(token, new SessionPrincipal(room.code(), room.id(), participant.id(), participant.role()));
        return token;
    }

    private String uniqueRoomCode() {
        String code;
        do {
            code = randomRoomCode();
        } while (roomsByCode.containsKey(code));
        return code;
    }

    private String randomRoomCode() {
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            code.append(ROOM_CODE_ALPHABET[random.nextInt(ROOM_CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private List<String> normalizeScale(List<String> scale) {
        if (scale == null || scale.isEmpty()) {
            return DEFAULT_SCALE;
        }
        List<String> values = new ArrayList<>();
        for (String rawValue : scale) {
            String value = cleanOptional(rawValue, 40);
            if (value != null && !values.contains(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return DEFAULT_SCALE;
        }
        if (values.size() > 24) {
            throw badRequest("scale.tooLarge", "Voting scale can contain at most 24 cards.");
        }
        return List.copyOf(values);
    }

    private void ensureRoomNameAvailable(String roomName) {
        purgeInactiveRooms();
        String normalizedName = normalizeRoomName(roomName);
        boolean duplicate = roomsByCode.values().stream()
                .filter(room -> room.status() == RoomStatus.OPEN)
                .anyMatch(room -> normalizeRoomName(room.name()).equals(normalizedName));
        if (duplicate) {
            throw conflict("room.duplicateName", "A room with that name already exists.");
        }
    }

    private String cleanAvatar(String value, int fallbackIndex) {
        String cleaned = cleanOptional(value, 24);
        if (cleaned != null && AVATAR_KEYS.contains(cleaned)) {
            return cleaned;
        }
        return AVATAR_KEYS.get(Math.floorMod(fallbackIndex, AVATAR_KEYS.size()));
    }

    private String cleanDefault(String value, String fallback, int maxLength) {
        String cleaned = cleanOptional(value, maxLength);
        return cleaned == null ? fallback : cleaned;
    }

    private String cleanRequired(String value, String field, int maxLength) {
        String cleaned = cleanOptional(value, maxLength);
        if (cleaned == null) {
            throw badRequest("validation.required", field + " is required.");
        }
        return cleaned;
    }

    private String cleanOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() > maxLength) {
            throw badRequest("validation.tooLong", "Value exceeds " + maxLength + " characters.");
        }
        return cleaned;
    }

    private String normalizeRoomCode(String roomCode) {
        return roomCode == null ? "" : roomCode.trim().toUpperCase();
    }

    private String normalizeRoomName(String roomName) {
        return roomName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Instant now() {
        return clock.instant();
    }

    private GameException badRequest(String code, String message) {
        return new GameException(HttpStatus.BAD_REQUEST, code, message);
    }

    private GameException conflict(String code, String message) {
        return new GameException(HttpStatus.CONFLICT, code, message);
    }

    private GameException forbidden(String message) {
        return new GameException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    private GameException notAuthorized(String message) {
        return new GameException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }
}
