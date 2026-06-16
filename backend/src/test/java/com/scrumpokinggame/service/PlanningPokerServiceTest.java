package com.scrumpokinggame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scrumpokinggame.api.GameDtos.CreateRoomRequest;
import com.scrumpokinggame.api.GameDtos.JoinRoomRequest;
import com.scrumpokinggame.api.GameDtos.StoryCreateCommand;
import com.scrumpokinggame.api.GameDtos.VoteCastCommand;
import com.scrumpokinggame.model.ParticipantRole;
import com.scrumpokinggame.model.StoryStatus;
import com.scrumpokinggame.model.VoteRoundStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

class PlanningPokerServiceTest {

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private final MutableClock clock = new MutableClock(Instant.parse("2026-06-15T12:00:00Z"));
    private final PlanningPokerService service = new PlanningPokerService(clock, 1);

    private void activateFirstStory(SessionPrincipal facilitator, String roomCode) {
        var storyId = service.snapshot(roomCode).stories().getFirst().id();
        service.activateStory(facilitator, storyId);
    }

    @Test
    void inactiveRoomIsRemovedAfterInactivityWindow() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));

        clock.advance(Duration.ofHours(1).plusMinutes(1));
        assertThatThrownBy(() -> service.metadata(room.roomCode()))
                .isInstanceOf(GameException.class)
                .satisfies(error -> assertThat(((GameException) error).status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.recentRooms()).isEmpty();
    }

    @Test
    void roomActivityExtendsInactivityWindow() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));

        clock.advance(Duration.ofMinutes(50));
        assertThat(service.metadata(room.roomCode()).code()).isEqualTo(room.roomCode());

        clock.advance(Duration.ofMinutes(50));
        assertThat(service.metadata(room.roomCode()).code()).isEqualTo(room.roomCode());

        clock.advance(Duration.ofMinutes(61));
        assertThatThrownBy(() -> service.metadata(room.roomCode()))
                .isInstanceOf(GameException.class)
                .satisfies(error -> assertThat(((GameException) error).status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createStoryDoesNotStartVotingUntilActivated() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));
        var facilitator = service.authenticate(room.roomCode(), room.sessionToken());

        service.createStory(facilitator, new StoryCreateCommand("Backlog item", null));

        var snapshot = service.snapshot(room.roomCode());
        assertThat(snapshot.activeRound()).isNull();
        assertThat(snapshot.stories()).hasSize(1);
        assertThat(snapshot.stories().getFirst().status()).isEqualTo(StoryStatus.PENDING);
        assertThat(snapshot.room().currentStoryId()).isNull();
    }

    @Test
    void keepsVoteValuesHiddenUntilFacilitatorReveals() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", "avatar-1", List.of("1", "2", "3", "5")));
        var facilitator = service.authenticate(room.roomCode(), room.sessionToken());
        service.createStory(facilitator, new StoryCreateCommand("Checkout retry", null));
        activateFirstStory(facilitator, room.roomCode());
        var voter = service.joinRoom(room.roomCode(), new JoinRoomRequest("Ben", ParticipantRole.VOTER, "avatar-2"));
        var voterPrincipal = service.authenticate(room.roomCode(), voter.sessionToken());

        var activeRound = service.snapshot(room.roomCode()).activeRound();
        service.castVote(facilitator, new VoteCastCommand(activeRound.id(), "3"));
        service.castVote(voterPrincipal, new VoteCastCommand(activeRound.id(), "5"));

        var hiddenSnapshot = service.snapshot(room.roomCode());
        assertThat(hiddenSnapshot.activeRound().votes()).isEmpty();
        assertThat(hiddenSnapshot.participants())
                .filteredOn(participant -> participant.role() != ParticipantRole.OBSERVER)
                .allSatisfy(participant -> assertThat(participant.hasVoted()).isTrue());

        service.revealVotes(facilitator, activeRound.id());

        var revealedSnapshot = service.snapshot(room.roomCode());
        assertThat(revealedSnapshot.activeRound().status()).isEqualTo(VoteRoundStatus.REVEALED);
        assertThat(revealedSnapshot.activeRound().votes()).hasSize(2);
        assertThat(revealedSnapshot.activeRound().summary().average()).isEqualTo(4.0);
        assertThat(revealedSnapshot.activeRound().summary().median()).isEqualTo(4.0);
    }

    @Test
    void resetVotesCreatesFreshVotingRound() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));
        var facilitator = service.authenticate(room.roomCode(), room.sessionToken());
        service.createStory(facilitator, new StoryCreateCommand("Saved filters", null));
        activateFirstStory(facilitator, room.roomCode());
        var firstRound = service.snapshot(room.roomCode()).activeRound();
        service.castVote(facilitator, new VoteCastCommand(firstRound.id(), "8"));
        service.revealVotes(facilitator, firstRound.id());

        service.resetVotes(facilitator, firstRound.storyId());

        var snapshot = service.snapshot(room.roomCode());
        assertThat(snapshot.activeRound().id()).isNotEqualTo(firstRound.id());
        assertThat(snapshot.activeRound().status()).isEqualTo(VoteRoundStatus.VOTING);
        assertThat(snapshot.activeRound().votes()).isEmpty();
        assertThat(snapshot.participants())
                .filteredOn(participant -> participant.role() != ParticipantRole.OBSERVER)
                .allSatisfy(participant -> assertThat(participant.hasVoted()).isFalse());
    }

    @Test
    void observerCannotVote() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));
        var facilitator = service.authenticate(room.roomCode(), room.sessionToken());
        service.createStory(facilitator, new StoryCreateCommand("Audit export", null));
        activateFirstStory(facilitator, room.roomCode());
        var observer = service.joinRoom(room.roomCode(), new JoinRoomRequest("Casey", ParticipantRole.OBSERVER, "avatar-3"));
        var observerPrincipal = service.authenticate(room.roomCode(), observer.sessionToken());
        var round = service.snapshot(room.roomCode()).activeRound();

        assertThatThrownBy(() -> service.castVote(observerPrincipal, new VoteCastCommand(round.id(), "5")))
                .isInstanceOf(GameException.class)
                .satisfies(error -> assertThat(((GameException) error).status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void finalizedStoryAppearsInSummary() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));
        var facilitator = service.authenticate(room.roomCode(), room.sessionToken());
        service.createStory(facilitator, new StoryCreateCommand("Invoice PDF", null));
        activateFirstStory(facilitator, room.roomCode());
        var snapshot = service.snapshot(room.roomCode());
        var round = snapshot.activeRound();
        var storyId = snapshot.room().currentStoryId();

        service.castVote(facilitator, new VoteCastCommand(round.id(), "3"));
        service.revealVotes(facilitator, round.id());
        service.finalizeEstimate(facilitator, new com.scrumpokinggame.api.GameDtos.EstimateFinalizeCommand(storyId, "3"));

        var summary = service.roomSummary(room.roomCode(), facilitator);
        assertThat(summary.stories()).hasSize(1);
        assertThat(summary.stories().getFirst().title()).isEqualTo("Invoice PDF");
        assertThat(summary.stories().getFirst().finalEstimate()).isEqualTo("3");
    }

    @Test
    void duplicateOpenRoomNamesAreRejected() {
        service.createRoom(new CreateRoomRequest("Refinement", "Ana", null, null));

        assertThatThrownBy(() -> service.createRoom(new CreateRoomRequest(" refinement ", "Ben", null, null)))
                .isInstanceOf(GameException.class)
                .satisfies(error -> assertThat(((GameException) error).status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void snapshotJsonIncludesAvatarKeys() throws Exception {
        var mapper = JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", "avatar-2", null));
        service.joinRoom(room.roomCode(), new JoinRoomRequest("Ben", ParticipantRole.VOTER, "avatar-5"));

        String json = mapper.writeValueAsString(service.snapshot(room.roomCode()));

        assertThat(json).contains("\"avatarKey\":\"avatar-2\"");
        assertThat(json).contains("\"avatarKey\":\"avatar-5\"");
    }

    @Test
    void blankFacilitatorNameIsRejected() {
        assertThatThrownBy(() -> service.createRoom(new CreateRoomRequest("Refinement", "  ", null, null)))
                .isInstanceOf(GameException.class)
                .satisfies(error -> assertThat(((GameException) error).status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void recentRoomsIncludeParticipantCountsAndAvatars() {
        var room = service.createRoom(new CreateRoomRequest("Refinement", "Ana", "avatar-4", null));
        service.joinRoom(room.roomCode(), new JoinRoomRequest("Ben", ParticipantRole.VOTER, "avatar-5"));

        var recent = service.recentRooms();
        var snapshot = service.snapshot(room.roomCode());

        assertThat(recent).hasSize(1);
        assertThat(recent.getFirst().name()).isEqualTo("Refinement");
        assertThat(recent.getFirst().participantCount()).isEqualTo(2);
        assertThat(snapshot.participants().getFirst().avatarKey()).isEqualTo("avatar-4");
        assertThat(snapshot.participants().get(1).avatarKey()).isEqualTo("avatar-5");
    }
}
