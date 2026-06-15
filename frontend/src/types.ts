export type ParticipantRole = "FACILITATOR" | "VOTER" | "OBSERVER";
export type RoomStatus = "OPEN" | "ENDED" | "EXPIRED";
export type StoryStatus = "PENDING" | "ACTIVE" | "ESTIMATED" | "SKIPPED";
export type VoteRoundStatus = "VOTING" | "REVEALED" | "CANCELLED";

export type RoomView = {
  code: string;
  name: string;
  status: RoomStatus;
  votingScale: string[];
  currentStoryId: string | null;
  expiresAt: string;
};

export type ParticipantView = {
  id: string;
  displayName: string;
  role: ParticipantRole;
  avatarKey: string;
  online: boolean;
  hasVoted: boolean;
};

export type RoomListItem = {
  code: string;
  name: string;
  status: RoomStatus;
  participantCount: number;
  createdAt: string;
};

export type StoryView = {
  id: string;
  title: string;
  description: string | null;
  status: StoryStatus;
  finalEstimate: string | null;
  sortOrder: number;
};

export type VoteView = {
  participantId: string;
  value: string;
};

export type VoteSummary = {
  numericCount: number;
  average: number | null;
  median: number | null;
  min: number | null;
  max: number | null;
  distribution: Record<string, number>;
  nonNumeric: Record<string, number>;
};

export type ActiveRoundView = {
  id: string;
  storyId: string;
  status: VoteRoundStatus;
  roundNumber: number;
  createdAt: string;
  votes: VoteView[];
  summary: VoteSummary | null;
};

export type RoomSnapshot = {
  room: RoomView;
  participants: ParticipantView[];
  stories: StoryView[];
  activeRound: ActiveRoundView | null;
};

export type JoinRoomResponse = {
  roomCode: string;
  participantId: string;
  role: ParticipantRole;
  sessionToken: string;
  snapshot: RoomSnapshot;
};

export type RoomMetadata = {
  code: string;
  name: string;
  status: RoomStatus;
  votingScale: string[];
  expiresAt: string;
};

export type OutboundEvent =
  | { event: "room.snapshot"; payload: RoomSnapshot }
  | { event: "error"; payload: { code: string; message: string } };

export type StoredSession = {
  participantId: string;
  role: ParticipantRole;
  sessionToken: string;
  avatarKey?: string;
  displayName?: string;
};

