import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { ThemeToggle } from "./theme";
import { createRoom, fetchRecentRooms, fetchRoomMetadata, fetchSnapshot, joinRoom, listStoredRoomCodes, roomUrl, sessionStorageKey, signOutToLobby } from "./api";
import { AVATAR_KEYS, avatarUrl, defaultRoomName, isAvatarKey, isCoffeeVote, type AvatarKey } from "./avatars";
import { clearProfile, defaultProfileAvatar, defaultProfileName, readProfile, saveProfile } from "./profile";
import { connectionLabel, EmptyState, FieldHint, LoadingList, PageIntro, SkipLink } from "./ui";
import type {
  ActiveRoundView,
  JoinRoomResponse,
  OutboundEvent,
  ParticipantRole,
  ParticipantView,
  RoomListItem,
  RoomMetadata,
  RoomSnapshot,
  StoredSession,
  StoryView,
  VoteSummary
} from "./types";

type AppTab = "Lobby" | "History" | "Profile";
type Route =
  | { name: "home" }
  | { name: "history" }
  | { name: "profile" }
  | { name: "room"; code: string };

const NAV_TARGETS: Record<AppTab, string> = {
  Lobby: "#/",
  History: "#/history",
  Profile: "#/profile"
};

const VOTING_DURATION_SECONDS = 10;

const AVATAR_CLASSES = ["avatar-indigo", "avatar-amber", "avatar-ink", "avatar-sky", "avatar-plum", "avatar-mint"];

function parseRoute(): Route {
  const hash = window.location.hash.replace(/^#/, "") || "/";
  const roomMatch = hash.match(/^\/room\/([A-Za-z0-9-]+)$/);
  if (roomMatch) {
    return { name: "room", code: roomMatch[1].toUpperCase() };
  }
  if (hash === "/history") {
    return { name: "history" };
  }
  if (hash === "/profile") {
    return { name: "profile" };
  }
  return { name: "home" };
}

function sessionParticipant(response: JoinRoomResponse): ParticipantView | undefined {
  return response.snapshot.participants.find((participant) => participant.id === response.participantId);
}

function saveSession(code: string, response: JoinRoomResponse) {
  const self = sessionParticipant(response);
  const session: StoredSession = {
    participantId: response.participantId,
    role: response.role,
    sessionToken: response.sessionToken,
    avatarKey: self?.avatarKey,
    displayName: self?.displayName
  };
  window.localStorage.setItem(sessionStorageKey(code), JSON.stringify(session));
}

function loadSession(code: string): StoredSession | null {
  const raw = window.localStorage.getItem(sessionStorageKey(code));
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    return null;
  }
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function displayStatus(value: string) {
  return value.toLowerCase().replaceAll("_", " ");
}

function avatarClass(index: number) {
  return AVATAR_CLASSES[index % AVATAR_CLASSES.length];
}

function formatNumber(value: number | null | undefined) {
  if (value == null) {
    return "-";
  }
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function distributionEntries(summary: VoteSummary) {
  return [...Object.entries(summary.distribution), ...Object.entries(summary.nonNumeric)].sort(([left], [right]) => {
    const leftNumber = Number(left);
    const rightNumber = Number(right);
    const leftIsNumber = !Number.isNaN(leftNumber);
    const rightIsNumber = !Number.isNaN(rightNumber);
    if (leftIsNumber && rightIsNumber) {
      return leftNumber - rightNumber;
    }
    if (leftIsNumber) {
      return -1;
    }
    if (rightIsNumber) {
      return 1;
    }
    return left.localeCompare(right);
  });
}

function consensus(summary: VoteSummary) {
  const entries = distributionEntries(summary);
  return entries.reduce<{ value: string; count: number } | null>((winner, [value, count]) => {
    if (!winner || count > winner.count) {
      return { value, count };
    }
    return winner;
  }, null);
}

function voteCount(summary: VoteSummary) {
  return summary.numericCount + Object.values(summary.nonNumeric).reduce((total, count) => total + count, 0);
}

type FacilitatorVotingActions = {
  showStart: boolean;
  showReveal: boolean;
  startStoryId: string | null;
  startStoryTitle: string | null;
  revealRoundId: string | null;
  needsStory: boolean;
};

function facilitatorVotingActions(snapshot: RoomSnapshot | null): FacilitatorVotingActions {
  const empty: FacilitatorVotingActions = {
    showStart: false,
    showReveal: false,
    startStoryId: null,
    startStoryTitle: null,
    revealRoundId: null,
    needsStory: false
  };
  if (!snapshot) {
    return empty;
  }

  const activeRound = snapshot.activeRound;
  if (activeRound?.status === "VOTING") {
    return empty;
  }

  if (snapshot.stories.length === 0) {
    return { ...empty, needsStory: true };
  }

  const pendingStory = snapshot.stories.find((story) => story.status === "PENDING");
  if (pendingStory) {
    return {
      ...empty,
      showStart: true,
      startStoryId: pendingStory.id,
      startStoryTitle: pendingStory.title
    };
  }

  return empty;
}

function StartTimerAction({
  snapshot,
  sendCommand,
  layout = "panel"
}: {
  snapshot: RoomSnapshot | null;
  sendCommand: (command: string, payload?: unknown) => void;
  layout?: "panel" | "inline" | "compact";
}) {
  const actions = facilitatorVotingActions(snapshot);

  if (!actions.showStart || !actions.startStoryId) {
    return null;
  }

  return (
    <div className={layout === "panel" ? "story-start-action" : layout === "compact" ? "story-start-action compact" : "inline-controls"}>
      {layout !== "compact" && (
        <p className="story-start-action-copy">
          {actions.startStoryTitle ? (
            <>
              Next up: <strong>{actions.startStoryTitle}</strong>
            </>
          ) : (
            "Ready to open the voting timer."
          )}
        </p>
      )}
      <button
        className="primary-action start-timer-action"
        type="button"
        onClick={() => sendCommand("story.activate", { storyId: actions.startStoryId })}
      >
        Start timer ({VOTING_DURATION_SECONDS}s)
      </button>
    </div>
  );
}

function votingSecondsRemaining(createdAt: string, nowMs = Date.now()) {
  const endMs = new Date(createdAt).getTime() + VOTING_DURATION_SECONDS * 1000;
  return Math.max(0, Math.ceil((endMs - nowMs) / 1000));
}

function useVotingSecondsRemaining(createdAt: string | undefined, active: boolean) {
  const [secondsLeft, setSecondsLeft] = useState(() => (createdAt && active ? votingSecondsRemaining(createdAt) : 0));

  useEffect(() => {
    if (!createdAt || !active) {
      setSecondsLeft(0);
      return;
    }

    const tick = () => setSecondsLeft(votingSecondsRemaining(createdAt));
    tick();
    const intervalId = window.setInterval(tick, 200);
    return () => window.clearInterval(intervalId);
  }, [active, createdAt]);

  return secondsLeft;
}

function VotingTimerOverlay({
  activeRound,
  isFacilitator,
  sendCommand
}: {
  activeRound: ActiveRoundView;
  isFacilitator: boolean;
  sendCommand: (command: string, payload?: unknown) => void;
}) {
  const autoRevealSent = useRef(false);
  const votingActive = activeRound.status === "VOTING";
  const secondsLeft = useVotingSecondsRemaining(activeRound.createdAt, votingActive);

  useEffect(() => {
    autoRevealSent.current = false;
  }, [activeRound.id]);

  useEffect(() => {
    if (!votingActive || secondsLeft > 0 || !isFacilitator || autoRevealSent.current) {
      return;
    }
    autoRevealSent.current = true;
    sendCommand("vote.reveal", { roundId: activeRound.id });
  }, [activeRound.id, isFacilitator, secondsLeft, sendCommand, votingActive]);

  if (!votingActive || secondsLeft <= 0) {
    return null;
  }

  return (
    <div className="voting-countdown-overlay" role="dialog" aria-live="polite" aria-label="Voting countdown">
      <div className="voting-countdown-card">
        <p className="eyebrow">Voting ends in</p>
        <div className={`voting-countdown-value ${secondsLeft <= 3 ? "urgent" : ""}`}>{secondsLeft}</div>
        {isFacilitator && (
          <button className="ghost-action reveal-now-action" type="button" onClick={() => sendCommand("vote.reveal", { roundId: activeRound.id })}>
            Reveal now
          </button>
        )}
      </div>
    </div>
  );
}

function StoryQuickAdd({ sendCommand }: { sendCommand: (command: string, payload?: unknown) => void }) {
  const [title, setTitle] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      return;
    }
    sendCommand("story.create", { title, description: "" });
    setTitle("");
  }

  return (
    <form className="story-quick-add" onSubmit={submit}>
      <label className="story-field-label">
        <FieldLabel required>Story title</FieldLabel>
        <input required value={title} maxLength={160} placeholder="Story title" onChange={(event) => setTitle(event.target.value)} />
      </label>
      <button className="primary-action" disabled={!title.trim()} type="submit">
        Add story
      </button>
    </form>
  );
}

function App() {
  const [route, setRoute] = useState<Route>(() => parseRoute());

  useEffect(() => {
    const onHashChange = () => setRoute(parseRoute());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  let page;
  switch (route.name) {
    case "home":
      page = <Home />;
      break;
    case "history":
      page = <HistoryPage />;
      break;
    case "profile":
      page = <ProfilePage />;
      break;
    case "room":
      page = <RoomPage roomCode={route.code} />;
      break;
  }

  return (
    <>
      <SkipLink />
      {page}
    </>
  );
}

function resolvedIdentity(
  participant?: Pick<ParticipantView, "displayName" | "avatarKey"> | null,
  session?: StoredSession | null
) {
  const saved = readProfile();
  return {
    displayName: participant?.displayName ?? session?.displayName ?? saved?.displayName ?? "You",
    avatarKey: participant?.avatarKey ?? session?.avatarKey ?? saved?.avatarKey ?? null
  };
}

function AppTopBar({
  title = "Scrum Poker",
  onSignOut
}: {
  title?: string;
  onSignOut?: () => void;
}) {
  const profile = readProfile();

  return (
    <header className="app-topbar">
      <a className="brand-lockup" href="#/" aria-label="Scrum Poker lobby">
        {profile ? (
          <PlayerAvatar avatarKey={profile.avatarKey} name={profile.displayName} size="small" />
        ) : (
          <span className="avatar-bubble avatar-indigo" aria-hidden="true">
            SP
          </span>
        )}
        <span>{title}</span>
      </a>
      <div className="topbar-actions compact">
        {profile && (
          <span className="session-identity topbar-profile" title={profile.displayName}>
            <span className="topbar-profile-name">{profile.displayName}</span>
          </span>
        )}
        <ThemeToggle />
        {onSignOut && (
          <button className="ghost-action sign-out-button" type="button" onClick={onSignOut}>
            Sign out
          </button>
        )}
      </div>
    </header>
  );
}

function VoteCardLabel({ value }: { value: string }) {
  if (isCoffeeVote(value)) {
    return <img className="vote-card-icon" src={avatarUrl("coffee")} alt="Coffee break" />;
  }
  return <>{value}</>;
}

function PlayerAvatar({
  avatarKey,
  name,
  index = 0,
  size = "regular",
  highlight = false,
  facilitator = false
}: {
  avatarKey?: string | null;
  name: string;
  index?: number;
  size?: "small" | "regular" | "large";
  highlight?: boolean;
  facilitator?: boolean;
}) {
  const className = [
    "player-avatar",
    size,
    highlight ? "highlight" : "",
    facilitator ? "facilitator" : ""
  ]
    .filter(Boolean)
    .join(" ");

  if (avatarKey && isAvatarKey(avatarKey)) {
    return <img className={className} src={avatarUrl(avatarKey)} alt="" aria-hidden="true" />;
  }
  return <AvatarBubble name={name} index={index} size={size} />;
}

function ParticipantsAvatarStrip({
  currentParticipantId,
  participants
}: {
  currentParticipantId?: string;
  participants: ParticipantView[];
}) {
  if (participants.length === 0) {
    return null;
  }

  return (
    <div className="participants-avatar-strip" aria-label="Participants in this room">
      {participants.map((participant, index) => (
        <span
          className={[
            "participant-avatar-chip",
            participant.id === currentParticipantId ? "self" : "",
            participant.role === "FACILITATOR" ? "facilitator" : ""
          ]
            .filter(Boolean)
            .join(" ")}
          key={participant.id}
          title={`${participant.displayName} (${displayStatus(participant.role)})`}
        >
          <PlayerAvatar
            avatarKey={participant.avatarKey}
            facilitator={participant.role === "FACILITATOR"}
            highlight={participant.id === currentParticipantId}
            index={index}
            name={participant.displayName}
            size="small"
          />
        </span>
      ))}
    </div>
  );
}

function FieldLabel({ children, required = false, optional = false }: { children: ReactNode; required?: boolean; optional?: boolean }) {
  return (
    <span className="field-label">
      <span className="field-label-text">{children}</span>
      {required && (
        <>
          <span className="required-mark" aria-hidden="true">
            *
          </span>
          <span className="visually-hidden"> (required)</span>
        </>
      )}
      {optional && <span className="optional-mark">Optional</span>}
    </span>
  );
}

function RequiredFieldsNote() {
  return (
    <p className="form-required-note">
      Fields marked with <span className="required-mark" aria-hidden="true">*</span> are required.
    </p>
  );
}

function AvatarPicker({
  selected,
  onSelect,
  optional = true
}: {
  selected: AvatarKey;
  onSelect: (key: AvatarKey) => void;
  optional?: boolean;
}) {
  return (
    <div className="field-group">
      <FieldLabel optional={optional}>Avatar</FieldLabel>
      <div className="avatar-picker" aria-label="Choose an avatar">
      {AVATAR_KEYS.map((key, index) => (
        <button
          aria-label={`Avatar option ${index + 1}`}
          aria-pressed={selected === key}
          className={selected === key ? "avatar-choice active" : "avatar-choice"}
          key={key}
          type="button"
          onClick={() => onSelect(key)}
        >
          <img className="player-avatar large" src={avatarUrl(key)} alt="" />
        </button>
      ))}
      </div>
    </div>
  );
}

function AvatarBubble({ name, index = 0, size = "regular" }: { name: string; index?: number; size?: "small" | "regular" | "large" }) {
  return (
    <span className={`avatar-bubble ${avatarClass(index)} ${size}`} aria-hidden="true">
      {initials(name) || "SP"}
    </span>
  );
}

function Home() {
  const formRef = useRef<HTMLFormElement>(null);
  const [roomName, setRoomName] = useState(() => defaultRoomName());
  const [facilitatorName, setFacilitatorName] = useState(() => defaultProfileName());
  const [facilitatorAvatar, setFacilitatorAvatar] = useState<AvatarKey>(() => defaultProfileAvatar());
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!facilitatorName.trim()) {
      setError("Facilitator name is required.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const response = await createRoom(roomName, facilitatorName.trim(), facilitatorAvatar);
      saveProfile(facilitatorName.trim(), facilitatorAvatar);
      saveSession(response.roomCode, response);
      window.location.hash = `/room/${response.roomCode}`;
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Could not create room.");
    } finally {
      setBusy(false);
    }
  }

  function resetCreateForm() {
    setRoomName(defaultRoomName());
    setError(null);
    formRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
    formRef.current?.querySelector("input")?.focus();
  }

  return (
    <div className="screen dashboard-screen">
      <AppTopBar onSignOut={() => signOutToLobby()} />
      <main className="dashboard-layout" id="main-content">
        <section className="dashboard-hero" aria-labelledby="create-room-title">
          <div className="hero-copy">
            <p className="eyebrow">Agile Pulse</p>
            <h1 id="create-room-title">Ready for a new Sprint?</h1>
            <p className="lede">Start a live planning poker room and bring the team into the same estimate.</p>
          </div>
          <form className="hero-form" ref={formRef} onSubmit={submit}>
            <RequiredFieldsNote />
            <label htmlFor="room-name">
              <FieldLabel required>Room name</FieldLabel>
              <input
                autoComplete="off"
                id="room-name"
                maxLength={80}
                required
                value={roomName}
                onChange={(event) => setRoomName(event.target.value)}
              />
              <FieldHint>Use a sprint or team name your participants will recognize.</FieldHint>
            </label>
            <label htmlFor="facilitator-name">
              <FieldLabel required>Facilitator name</FieldLabel>
              <input
                autoComplete="name"
                id="facilitator-name"
                maxLength={40}
                placeholder="Your name"
                required
                value={facilitatorName}
                onChange={(event) => setFacilitatorName(event.target.value)}
              />
            </label>
            <AvatarPicker selected={facilitatorAvatar} onSelect={setFacilitatorAvatar} />
            {error && <p className="error-line" role="alert">{error}</p>}
            <button className="secondary-action hero-action" disabled={busy || !roomName.trim() || !facilitatorName.trim()} type="submit">
              {busy ? "Creating room..." : "Create new room"}
            </button>
          </form>
        </section>

        <section className="recent-section panel" aria-labelledby="recent-rooms-title">
          <div className="section-heading">
            <div>
              <h2 id="recent-rooms-title">Recent rooms</h2>
              <p className="section-lede">Pick up where your team left off.</p>
            </div>
            <button className="text-button" type="button" onClick={resetCreateForm}>
              New room
            </button>
          </div>
          <RecentRoomsList emptyActionLabel="Create a room" onEmptyAction={resetCreateForm} />
        </section>

        <aside className="deck-preview" aria-label="Default planning poker scale">
          <h2>Default cards</h2>
          <p className="section-lede">Fibonacci-style scale used in every new room.</p>
          <div className="mini-card-grid">
            {["0", "1", "2", "3", "5", "8", "13", "21", "?", "coffee"].map((value) => (
              <span key={value}>{isCoffeeVote(value) ? <VoteCardLabel value={value} /> : value}</span>
            ))}
          </div>
        </aside>
      </main>
      <BottomNav active="Lobby" />
    </div>
  );
}

function RecentRoomsList({
  emptyActionHref,
  emptyActionLabel,
  onEmptyAction
}: {
  emptyActionHref?: string;
  emptyActionLabel?: string;
  onEmptyAction?: () => void;
}) {
  const [recentRooms, setRecentRooms] = useState<RoomListItem[]>([]);
  const [recentLoading, setRecentLoading] = useState(true);

  useEffect(() => {
    fetchRecentRooms()
      .then((rooms) => setRecentRooms(Array.isArray(rooms) ? rooms : []))
      .catch(() => setRecentRooms([]))
      .finally(() => setRecentLoading(false));
  }, []);

  if (recentLoading) {
    return <LoadingList count={2} />;
  }

  if (recentRooms.length === 0) {
    return (
      <EmptyState
        actionHref={emptyActionHref}
        actionLabel={emptyActionLabel}
        message="Rooms you create or join will show up here for quick access."
        onAction={onEmptyAction}
        title="No recent rooms"
      />
    );
  }

  return (
    <div className="recent-list">
      {recentRooms.map((room) => (
        <article className="recent-room-card" key={room.code}>
          <span className="room-icon" aria-hidden="true">
            {room.code.slice(0, 2)}
          </span>
          <div className="recent-room-copy">
            <h3>{room.name}</h3>
            <p>
              {room.participantCount} player{room.participantCount === 1 ? "" : "s"} · {room.code}
            </p>
            <span className={`status-chip room-status ${room.status.toLowerCase()}`}>{displayStatus(room.status)}</span>
          </div>
          <button className="primary-action small" type="button" onClick={() => (window.location.hash = `/room/${room.code}`)}>
            Join
          </button>
        </article>
      ))}
    </div>
  );
}

const NAV_ITEMS: { tab: AppTab; icon: string; label: string }[] = [
  { tab: "Lobby", icon: "⌂", label: "Lobby" },
  { tab: "History", icon: "◷", label: "History" },
  { tab: "Profile", icon: "◎", label: "Profile" }
];

function BottomNav({ active }: { active: AppTab | null }) {
  return (
    <nav className="bottom-nav" aria-label="Application navigation">
      {NAV_ITEMS.map(({ tab, icon, label }) => (
        <a
          aria-current={active === tab ? "page" : undefined}
          className={active === tab ? "active" : ""}
          href={NAV_TARGETS[tab]}
          key={tab}
        >
          <span aria-hidden="true">{icon}</span>
          {label}
        </a>
      ))}
    </nav>
  );
}

function HistoryPage() {
  return (
    <div className="screen dashboard-screen">
      <AppTopBar title="History" onSignOut={() => signOutToLobby()} />
      <main className="dashboard-layout history-layout" id="main-content">
        <section className="recent-section panel" aria-labelledby="history-title">
          <PageIntro description="Recently active planning poker rooms on this server." id="history-title" title="Room history" />
          <RecentRoomsList emptyActionHref="#/" emptyActionLabel="Create a room" />
        </section>
      </main>
      <BottomNav active="History" />
    </div>
  );
}

function ProfilePage() {
  const [savedProfile, setSavedProfile] = useState(() => readProfile());
  const [savedRooms, setSavedRooms] = useState(() => listStoredRoomCodes());

  function handleSignOutEverywhere() {
    signOutToLobby();
  }

  function handleClearProfile() {
    clearProfile();
    setSavedProfile(null);
  }

  return (
    <div className="screen dashboard-screen">
      <AppTopBar title="Profile" onSignOut={() => signOutToLobby()} />
      <main className="dashboard-layout profile-layout" id="main-content">
        <section className="panel profile-panel" aria-labelledby="profile-title">
          <PageIntro description="Appearance and session preferences for this browser." id="profile-title" title="Profile" />

          <div className="settings-card">
            <div className="profile-row">
              <div>
                <strong>Saved profile</strong>
                <p className="muted">Your name and avatar are remembered for new rooms.</p>
              </div>
              {savedProfile && (
                <PlayerAvatar avatarKey={savedProfile.avatarKey} name={savedProfile.displayName} size="small" />
              )}
            </div>
            {savedProfile ? (
              <>
                <p className="saved-profile-name">{savedProfile.displayName}</p>
                <button className="ghost-action danger-action" type="button" onClick={handleClearProfile}>
                  Clear saved profile
                </button>
              </>
            ) : (
              <EmptyState
                message="Create or join a room to save your display name and avatar on this device."
                title="No saved profile"
              />
            )}
          </div>

          <div className="settings-card">
            <div className="profile-row">
              <div>
                <strong>Theme</strong>
                <p className="muted">Switch between light and dark mode.</p>
              </div>
              <ThemeToggle />
            </div>
          </div>

          <div className="settings-card">
            <div className="profile-row">
              <div>
                <strong>Saved sessions</strong>
                <p className="muted">Rooms you joined on this device.</p>
              </div>
              <span className="profile-badge">{savedRooms.length}</span>
            </div>

            {savedRooms.length > 0 ? (
              <ul className="profile-session-list">
                {savedRooms.map((code) => (
                  <li key={code}>
                    <a href={`#/room/${code}`}>{code}</a>
                  </li>
                ))}
              </ul>
            ) : (
              <EmptyState
                message="Join or create a room to store a session on this device."
                title="No saved sessions"
              />
            )}
          </div>

          <div className="settings-card danger-zone">
            <strong>Sign out everywhere</strong>
            <p className="muted">Clears saved profile and all room sessions on this browser.</p>
            <button className="ghost-action danger-action" type="button" onClick={handleSignOutEverywhere}>
              Sign out everywhere
            </button>
          </div>
        </section>
      </main>
      <BottomNav active="Profile" />
    </div>
  );
}

function RoomPage({ roomCode }: { roomCode: string }) {
  const [session, setSession] = useState<StoredSession | null>(() => loadSession(roomCode));
  const [metadata, setMetadata] = useState<RoomMetadata | null>(null);
  const [snapshot, setSnapshot] = useState<RoomSnapshot | null>(null);
  const [connection, setConnection] = useState<"idle" | "connecting" | "connected" | "closed">("idle");
  const [error, setError] = useState<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    setSession(loadSession(roomCode));
    setSnapshot(null);
    setError(null);
  }, [roomCode]);

  useEffect(() => {
    fetchRoomMetadata(roomCode)
      .then(setMetadata)
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Room not found."));
  }, [roomCode]);

  useEffect(() => {
    if (!session) {
      return;
    }

    let closedByEffect = false;
    setConnection("connecting");
    setError(null);

    fetchSnapshot(roomCode, session.sessionToken)
      .then(setSnapshot)
      .catch((caught) => setError(caught instanceof Error ? caught.message : "Could not load room."));

    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${protocol}://${window.location.host}/ws/rooms/${roomCode}?token=${encodeURIComponent(session.sessionToken)}`);
    socketRef.current = socket;

    socket.onopen = () => {
      if (!closedByEffect) {
        setConnection("connected");
      }
    };
    socket.onmessage = (event) => {
      const message = JSON.parse(event.data) as OutboundEvent;
      if (message.event === "room.snapshot") {
        setSnapshot(message.payload);
        setMetadata((previous) =>
          previous
            ? { ...previous, status: message.payload.room.status, name: message.payload.room.name }
            : {
                code: message.payload.room.code,
                name: message.payload.room.name,
                status: message.payload.room.status,
                votingScale: message.payload.room.votingScale,
                expiresAt: message.payload.room.expiresAt
              }
        );
      }
      if (message.event === "error") {
        setError(message.payload.message);
      }
    };
    socket.onclose = () => {
      if (!closedByEffect) {
        setConnection("closed");
      }
    };

    return () => {
      closedByEffect = true;
      socket.close();
    };
  }, [roomCode, session]);

  const sendCommand = useCallback((command: string, payload: unknown = {}) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      setError("Realtime connection is not open yet.");
      return;
    }
    socket.send(JSON.stringify({ command, payload }));
  }, []);

  if (!session) {
    return (
      <JoinRoom
        metadata={metadata}
        roomCode={roomCode}
        error={error}
        onJoined={(response) => {
          saveSession(roomCode, response);
          const self = sessionParticipant(response);
          setSession({
            participantId: response.participantId,
            role: response.role,
            sessionToken: response.sessionToken,
            avatarKey: self?.avatarKey,
            displayName: self?.displayName
          });
          setSnapshot(response.snapshot);
        }}
      />
    );
  }

  return (
    <RoomWorkspace
      connection={connection}
      error={error}
      metadata={metadata}
      roomCode={roomCode}
      sendCommand={sendCommand}
      session={session}
      snapshot={snapshot}
      onLeave={() => {
        socketRef.current?.close();
        signOutToLobby();
      }}
    />
  );
}

function JoinRoom({
  metadata,
  roomCode,
  error,
  onJoined
}: {
  metadata: RoomMetadata | null;
  roomCode: string;
  error: string | null;
  onJoined: (response: JoinRoomResponse) => void;
}) {
  const savedProfile = readProfile();
  const [displayName, setDisplayName] = useState(() => defaultProfileName());
  const [role, setRole] = useState<ParticipantRole>("VOTER");
  const [avatarChoice, setAvatarChoice] = useState<AvatarKey>(() => defaultProfileAvatar());
  const [busy, setBusy] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setJoinError(null);
    try {
      const response = await joinRoom(roomCode, displayName, role, avatarChoice);
      saveProfile(displayName.trim(), avatarChoice);
      onJoined(response);
    } catch (caught) {
      setJoinError(caught instanceof Error ? caught.message : "Could not join room.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="screen join-screen-wrap">
      <main className="join-screen" id="main-content">
        <div className="join-screen-top">
          <a className="text-button join-back" href="#/">
            ← Back to lobby
          </a>
          <div className="join-screen-actions">
            <ThemeToggle />
            <button className="ghost-action sign-out-button" type="button" onClick={() => signOutToLobby()}>
              Sign out
            </button>
          </div>
        </div>
        <div className="join-brand">
          {savedProfile ? (
            <PlayerAvatar avatarKey={savedProfile.avatarKey} name={savedProfile.displayName} size="large" />
          ) : (
            <span className="floating-mark" aria-hidden="true">
              SP
            </span>
          )}
          <h1>Join room</h1>
        </div>
        <section className="join-card panel" aria-labelledby="join-room-title">
          <p className="eyebrow">Room {roomCode}</p>
          <h2 id="join-room-title">{metadata?.name ?? "Loading room..."}</h2>
          <p className="lede">Enter your name, pick an avatar, and choose how you want to participate.</p>
          <form className="form-grid" onSubmit={submit}>
            <RequiredFieldsNote />
            <label htmlFor="join-display-name">
              <FieldLabel required>Your name</FieldLabel>
              <input
                autoComplete="name"
                id="join-display-name"
                maxLength={40}
                placeholder="e.g. Agile Master"
                required
                value={displayName}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </label>
            <AvatarPicker selected={avatarChoice} onSelect={setAvatarChoice} />
            <div className="field-group">
              <span className="field-label">
                <span className="field-label-text">Role</span>
              </span>
              <div className="segmented" role="radiogroup" aria-label="Participant role">
                <button
                  aria-checked={role === "VOTER"}
                  className={role === "VOTER" ? "active" : ""}
                  role="radio"
                  type="button"
                  onClick={() => setRole("VOTER")}
                >
                  Voter
                </button>
                <button
                  aria-checked={role === "OBSERVER"}
                  className={role === "OBSERVER" ? "active" : ""}
                  role="radio"
                  type="button"
                  onClick={() => setRole("OBSERVER")}
                >
                  Observer
                </button>
              </div>
              <FieldHint>Voters select estimates. Observers watch without voting.</FieldHint>
            </div>
            {(joinError || error) && <p className="error-line" role="alert">{joinError || error}</p>}
            <button className="primary-action join-action" disabled={busy || !displayName.trim()} type="submit">
              {busy ? "Entering room..." : "Join game"}
            </button>
          </form>
        </section>
      </main>
    </div>
  );
}

function RoomWorkspace({
  connection,
  error,
  metadata,
  onLeave,
  roomCode,
  sendCommand,
  session,
  snapshot
}: {
  connection: string;
  error: string | null;
  metadata: RoomMetadata | null;
  onLeave: () => void;
  roomCode: string;
  sendCommand: (command: string, payload?: unknown) => void;
  session: StoredSession;
  snapshot: RoomSnapshot | null;
}) {
  const activeStory = useMemo(() => {
    if (!snapshot?.room.currentStoryId) {
      return null;
    }
    return snapshot.stories.find((story) => story.id === snapshot.room.currentStoryId) ?? null;
  }, [snapshot]);
  const isFacilitator = session.role === "FACILITATOR";
  const facilitator = snapshot?.participants.find((participant) => participant.role === "FACILITATOR");
  const currentParticipant = snapshot?.participants.find((participant) => participant.id === session.participantId);
  const selfIdentity = resolvedIdentity(currentParticipant, session);
  const moderator = facilitator?.displayName ?? "Facilitator";
  const participantCount = snapshot?.participants.length ?? 0;

  return (
    <div className="screen room-screen">
      <header className="app-topbar room-topbar">
        <a className="brand-lockup" href="#/" aria-label="Scrum Poker lobby">
          <PlayerAvatar avatarKey={facilitator?.avatarKey} facilitator name={moderator} index={0} />
          <span>Scrum Poker</span>
        </a>
        <div className="topbar-actions">
          <ThemeToggle />
          <span className="session-identity" title={selfIdentity.displayName}>
            <PlayerAvatar avatarKey={selfIdentity.avatarKey} highlight name={selfIdentity.displayName} size="small" />
            <span>You</span>
          </span>
          <span className={`connection-pill ${connection}`}>{connectionLabel(connection)}</span>
          <button className="ghost-action" type="button" onClick={() => navigator.clipboard.writeText(roomUrl(roomCode))}>
            Copy link
          </button>
          <button className="ghost-action sign-out-button" type="button" onClick={onLeave}>
            Sign out
          </button>
        </div>
      </header>

      {snapshot?.activeRound?.status === "VOTING" && snapshot.activeRound.createdAt && (
        <VotingTimerOverlay activeRound={snapshot.activeRound} isFacilitator={isFacilitator} sendCommand={sendCommand} />
      )}

      <main className="workspace" id="main-content">
        <section className="room-intro">
          <div>
            <div className="moderator-chip">
              <PlayerAvatar avatarKey={facilitator?.avatarKey} facilitator name={moderator} index={0} size="small" />
              <p className="eyebrow">Moderator: {moderator}</p>
            </div>
            <h1>{snapshot?.room.name ?? metadata?.name ?? "Planning poker"}</h1>
            <p className="lede room-lede">Estimate stories together in real time.</p>
            <div className="chip-row">
              <span>Room {roomCode}</span>
              <span>{participantCount} participant{participantCount === 1 ? "" : "s"}</span>
              <span className={`status-chip room-status ${(snapshot?.room.status ?? metadata?.status ?? "OPEN").toLowerCase()}`}>
                {displayStatus(snapshot?.room.status ?? metadata?.status ?? "OPEN")}
              </span>
            </div>
            <ParticipantsAvatarStrip currentParticipantId={session.participantId} participants={snapshot?.participants ?? []} />
          </div>
          {isFacilitator && !facilitatorVotingActions(snapshot).needsStory && (
            <StartTimerAction layout="compact" sendCommand={sendCommand} snapshot={snapshot} />
          )}
        </section>

        {error && <p className="error-banner" role="alert">{error}</p>}

        {!snapshot ? (
          <div aria-busy="true" aria-label="Loading room" className="workspace-loading" role="status">
            <LoadingList count={2} />
          </div>
        ) : (
        <div className="workspace-grid">
          <section className="main-column">
            <StoryStage
              activeRound={snapshot?.activeRound ?? null}
              activeStory={activeStory}
              isFacilitator={isFacilitator}
              sendCommand={sendCommand}
              session={session}
              snapshot={snapshot}
            />
            <VotingTable activeRound={snapshot?.activeRound ?? null} session={session} snapshot={snapshot} />
          </section>

          <aside className="side-column">
            <StoriesPanel isFacilitator={isFacilitator} sendCommand={sendCommand} snapshot={snapshot} />
            <ParticipantsPanel currentParticipantId={session.participantId} snapshot={snapshot} />
            <SummaryPanel snapshot={snapshot} />
          </aside>
        </div>
        )}
      </main>
      <BottomNav active={null} />
    </div>
  );
}

function StoryStage({
  activeRound,
  activeStory,
  isFacilitator,
  sendCommand,
  session,
  snapshot
}: {
  activeRound: ActiveRoundView | null;
  activeStory: StoryView | null;
  isFacilitator: boolean;
  sendCommand: (command: string, payload?: unknown) => void;
  session: StoredSession;
  snapshot: RoomSnapshot | null;
}) {
  const [selectedValue, setSelectedValue] = useState<string | null>(null);

  useEffect(() => {
    setSelectedValue(null);
  }, [activeRound?.id]);

  const canVote = Boolean(activeRound && activeRound.status === "VOTING" && session.role !== "OBSERVER");
  const currentParticipant = snapshot?.participants.find((participant) => participant.id === session.participantId);
  const secondsLeft = useVotingSecondsRemaining(activeRound?.createdAt, activeRound?.status === "VOTING");

  if (!snapshot) {
    return (
      <section aria-busy="true" aria-label="Loading room" className="stage empty-state" role="status">
        <LoadingList count={1} />
      </section>
    );
  }

  if (!activeStory || !activeRound) {
    const actions = facilitatorVotingActions(snapshot);
    const host = snapshot.participants.find((participant) => participant.role === "FACILITATOR");

    return (
      <section className="stage empty-state">
        <div className="stage-icon-wrap">
          <PlayerAvatar avatarKey={host?.avatarKey} facilitator index={0} name={host?.displayName ?? "Facilitator"} size="large" />
        </div>
        <h2>No active vote round</h2>
        {isFacilitator ? (
          <>
            {actions.needsStory ? (
              <>
                <p>Add a story, then start the timer when your team is ready.</p>
                <StoryQuickAdd sendCommand={sendCommand} />
              </>
            ) : (
              <>
                <p>When everyone is ready, start the voting timer for the next story.</p>
                <StartTimerAction layout="panel" sendCommand={sendCommand} snapshot={snapshot} />
              </>
            )}
          </>
        ) : (
          <p>Waiting for the facilitator to start voting.</p>
        )}
      </section>
    );
  }

  return (
    <section className={`stage ${activeRound.status === "REVEALED" ? "revealed" : ""}`} aria-labelledby="active-story-title">
      <div className="story-heading">
        <div>
          <p className="eyebrow">Round {activeRound.roundNumber}</p>
          <h2 id="active-story-title">{activeStory.title}</h2>
          {activeStory.description && <p>{activeStory.description}</p>}
        </div>
        <div className="story-heading-actions">
          <span className={`status-chip ${activeRound.status.toLowerCase()}`}>{displayStatus(activeRound.status)}</span>
          {activeRound.status === "VOTING" && activeRound.createdAt && secondsLeft > 0 && (
            <span className="inline-timer" aria-live="polite">
              {secondsLeft}s left
            </span>
          )}
        </div>
      </div>

      <div className="estimation-deck" aria-label="Voting cards">
        {snapshot.room.votingScale.map((value) => (
          <button
            className={selectedValue === value ? "vote-card selected" : "vote-card"}
            disabled={!canVote}
            key={value}
            type="button"
            onClick={() => {
              setSelectedValue(value);
              sendCommand("vote.cast", { roundId: activeRound.id, value });
            }}
          >
            <VoteCardLabel value={value} />
          </button>
        ))}
      </div>

      <p className="vote-note">
        {activeRound.status === "REVEALED"
          ? "Votes are revealed."
          : currentParticipant?.hasVoted
            ? "Your vote is in."
            : canVote
              ? "Select your estimate."
              : "Waiting for the facilitator."}
      </p>
    </section>
  );
}

function ParticipantsPanel({
  currentParticipantId,
  snapshot
}: {
  currentParticipantId?: string;
  snapshot: RoomSnapshot | null;
}) {
  return (
    <section className="panel">
      <div className="section-heading compact">
        <h2>Live Voting</h2>
        <span>{snapshot ? `${snapshot.participants.filter((participant) => participant.hasVoted).length}/${snapshot.participants.length} voted` : "Loading"}</span>
      </div>
      <div className="participant-grid">
        {snapshot?.participants.map((participant, index) => (
          <ParticipantCard currentParticipantId={currentParticipantId} index={index} key={participant.id} participant={participant} />
        ))}
      </div>
    </section>
  );
}

function ParticipantCard({
  participant,
  index,
  currentParticipantId
}: {
  participant: ParticipantView;
  index: number;
  currentParticipantId?: string;
}) {
  return (
    <article
      className={[
        "participant-card",
        participant.hasVoted ? "voted" : "waiting",
        participant.online ? "" : "offline",
        participant.id === currentParticipantId ? "self" : "",
        participant.role === "FACILITATOR" ? "facilitator" : ""
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <div className="participant-avatar-wrap">
        {!participant.hasVoted && <span className="pulse-ring" aria-hidden="true" />}
        <PlayerAvatar
          avatarKey={participant.avatarKey}
          facilitator={participant.role === "FACILITATOR"}
          highlight={participant.id === currentParticipantId}
          index={index}
          name={participant.displayName}
          size="large"
        />
        <span className="vote-badge" aria-label={participant.hasVoted ? "Voted" : "Waiting"}>
          {participant.hasVoted ? "V" : ""}
        </span>
      </div>
      <strong>{participant.displayName}</strong>
      <small>{displayStatus(participant.role)}</small>
    </article>
  );
}

function StoriesPanel({
  isFacilitator,
  sendCommand,
  snapshot
}: {
  isFacilitator: boolean;
  sendCommand: (command: string, payload?: unknown) => void;
  snapshot: RoomSnapshot | null;
}) {
  const [title, setTitle] = useState("");

  function addStory(event: FormEvent) {
    event.preventDefault();
    if (!title.trim()) {
      return;
    }
    sendCommand("story.create", { title, description: "" });
    setTitle("");
  }

  return (
    <section className="panel">
      <div className="section-heading compact">
        <h2>Stories</h2>
        <span>{snapshot?.stories.length ?? 0}</span>
      </div>
      {isFacilitator && (
        <>
          <form className="compact-form story-add-form" onSubmit={addStory}>
            <label className="story-field-label">
              <FieldLabel required>Story title</FieldLabel>
              <input required value={title} maxLength={160} placeholder="Add a story title" onChange={(event) => setTitle(event.target.value)} />
            </label>
            <button className="primary-action small" disabled={!title.trim()} type="submit">
              Add story
            </button>
          </form>
          <StartTimerAction layout="panel" sendCommand={sendCommand} snapshot={snapshot} />
        </>
      )}
      <div className="story-list">
        {snapshot?.stories.length ? (
          snapshot.stories.map((story) => (
            <div className="story-row" key={story.id}>
              <div>
                <strong>{story.title}</strong>
                <small>
                  {displayStatus(story.status)}
                  {story.finalEstimate ? `, ${story.finalEstimate} pts` : ""}
                </small>
              </div>
              {story.status === "ACTIVE" && <span className="story-status-pill">Timer running</span>}
              {story.status === "PENDING" && <span className="story-status-pill pending">Queued</span>}
            </div>
          ))
        ) : (
          <p className="muted">{isFacilitator ? "Add a story, then start the timer." : "No stories yet."}</p>
        )}
      </div>
    </section>
  );
}

function VotingTable({
  activeRound,
  session,
  snapshot
}: {
  activeRound: ActiveRoundView | null;
  session: StoredSession;
  snapshot: RoomSnapshot | null;
}) {
  const votesByParticipant = useMemo(() => {
    const map = new Map<string, string>();
    activeRound?.votes.forEach((vote) => map.set(vote.participantId, vote.value));
    return map;
  }, [activeRound?.votes]);

  if (!snapshot || !activeRound) {
    return null;
  }

  const voters = snapshot.participants.filter((participant) => participant.role !== "OBSERVER");

  return (
    <section className={activeRound.status === "REVEALED" ? "results-panel revealed-results" : "results-panel"} aria-labelledby="votes-title">
      <div className="results-header">
        <h2 id="votes-title">{activeRound.status === "REVEALED" ? "Results" : "Votes"}</h2>
        {activeRound.summary && <SummaryStats summary={activeRound.summary} />}
      </div>
      {activeRound.summary && <DistributionBars summary={activeRound.summary} />}
      <div className={activeRound.status === "REVEALED" ? "revealed-grid" : "vote-table"}>
        {voters.map((participant, index) => {
          const revealedValue = votesByParticipant.get(participant.id);
          if (activeRound.status === "REVEALED") {
            return (
              <article className={participant.id === session.participantId ? "revealed-card self" : "revealed-card"} key={participant.id}>
                <PlayerAvatar avatarKey={participant.avatarKey} index={index} name={participant.displayName} size="regular" />
                <span>{participant.displayName}</span>
                <strong>{revealedValue ? <VoteCardLabel value={revealedValue} /> : "-"}</strong>
              </article>
            );
          }
          return (
            <div className={participant.id === session.participantId ? "vote-row self" : "vote-row"} key={participant.id}>
              <div className="vote-row-participant">
                <PlayerAvatar avatarKey={participant.avatarKey} index={index} name={participant.displayName} size="small" />
                <span>{participant.displayName}</span>
              </div>
              <strong>{participant.hasVoted ? "Locked" : "Waiting"}</strong>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function SummaryStats({ summary }: { summary: VoteSummary }) {
  const winningVote = consensus(summary);
  return (
    <dl className="summary-cards">
      <div className="summary-card consensus-card">
        <dt>Consensus</dt>
        <dd>{winningVote?.value ? <VoteCardLabel value={winningVote.value} /> : "-"}</dd>
      </div>
      <div className="summary-card">
        <dt>Average</dt>
        <dd>{formatNumber(summary.average)}</dd>
      </div>
      <div className="summary-card">
        <dt>Votes</dt>
        <dd>{voteCount(summary)}</dd>
      </div>
    </dl>
  );
}

function DistributionBars({ summary }: { summary: VoteSummary }) {
  const entries = distributionEntries(summary);
  const max = Math.max(...entries.map(([, count]) => count), 1);
  const winningVote = consensus(summary);

  if (!entries.length) {
    return null;
  }

  return (
    <div className="distribution-panel">
      <div className="distribution-header">
        <span>Estimate</span>
        <span>Votes</span>
      </div>
      <div className="distribution-bars" aria-label="Vote distribution">
        {entries.map(([value, count]) => {
          const width = `${Math.max((count / max) * 100, 8)}%`;
          const isConsensus = value === winningVote?.value;
          return (
            <div className={isConsensus ? "distribution-row consensus" : "distribution-row"} key={value}>
              <span>{isCoffeeVote(value) ? <VoteCardLabel value={value} /> : value}</span>
              <div className="distribution-track">
                <div className="distribution-fill" style={{ width }} />
                <strong>
                  {count} vote{count === 1 ? "" : "s"}
                </strong>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function SummaryPanel({ snapshot }: { snapshot: RoomSnapshot | null }) {
  const estimated = snapshot?.stories.filter((story) => story.status === "ESTIMATED") ?? [];

  function copyMarkdown() {
    const lines = ["# Planning Poker Summary", ""];
    estimated.forEach((story) => {
      lines.push(`- ${story.title}: ${story.finalEstimate ?? "not set"}`);
    });
    navigator.clipboard.writeText(lines.join("\n"));
  }

  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>Summary</h2>
        <button disabled={!estimated.length} type="button" onClick={copyMarkdown}>
          Copy
        </button>
      </div>
      {estimated.length ? (
        <div className="summary-list">
          {estimated.map((story) => (
            <div className="summary-row" key={story.id}>
              <span>{story.title}</span>
              <strong>{story.finalEstimate}</strong>
            </div>
          ))}
        </div>
      ) : (
        <p className="muted">No finalized estimates yet.</p>
      )}
    </section>
  );
}

export default App;
