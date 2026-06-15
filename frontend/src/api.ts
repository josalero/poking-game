import type { JoinRoomResponse, ParticipantRole, RoomListItem, RoomMetadata, RoomSnapshot } from "./types";

type ApiErrorBody = {
  code?: string;
  message?: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    const error = (await response.json().catch(() => ({ message: response.statusText }))) as ApiErrorBody;
    throw new Error(error.message ?? "Request failed");
  }

  return response.json() as Promise<T>;
}

export function fetchRecentRooms(): Promise<RoomListItem[]> {
  return request<RoomListItem[]>("/api/rooms/recent");
}

export function createRoom(
  roomName: string,
  facilitatorName: string,
  facilitatorAvatarKey?: string
): Promise<JoinRoomResponse> {
  return request<JoinRoomResponse>("/api/rooms", {
    method: "POST",
    body: JSON.stringify({ roomName, facilitatorName, facilitatorAvatarKey })
  });
}

export function fetchRoomMetadata(code: string): Promise<RoomMetadata> {
  return request<RoomMetadata>(`/api/rooms/${encodeURIComponent(code)}`);
}

export function joinRoom(
  code: string,
  displayName: string,
  role: ParticipantRole,
  avatarKey?: string
): Promise<JoinRoomResponse> {
  return request<JoinRoomResponse>(`/api/rooms/${encodeURIComponent(code)}/join`, {
    method: "POST",
    body: JSON.stringify({ displayName, role, avatarKey })
  });
}

export function fetchSnapshot(code: string, token: string): Promise<RoomSnapshot> {
  return request<RoomSnapshot>(`/api/rooms/${encodeURIComponent(code)}/snapshot`, {
    headers: {
      "X-Participant-Token": token
    }
  });
}

export function sessionStorageKey(roomCode: string): string {
  return `scrum-poking:${roomCode.toUpperCase()}`;
}

export const SESSION_KEY_PREFIX = "scrum-poking:";

export function clearRoomSession(roomCode: string): void {
  window.localStorage.removeItem(sessionStorageKey(roomCode));
}

export function clearAllSessions(): void {
  const keysToRemove: string[] = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (key?.startsWith(SESSION_KEY_PREFIX)) {
      keysToRemove.push(key);
    }
  }
  keysToRemove.forEach((key) => window.localStorage.removeItem(key));
}

export function hasStoredSessions(): boolean {
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (key?.startsWith(SESSION_KEY_PREFIX)) {
      return true;
    }
  }
  return false;
}

export function listStoredRoomCodes(): string[] {
  const codes: string[] = [];
  for (let index = 0; index < window.localStorage.length; index += 1) {
    const key = window.localStorage.key(index);
    if (key?.startsWith(SESSION_KEY_PREFIX)) {
      codes.push(key.slice(SESSION_KEY_PREFIX.length));
    }
  }
  return codes.sort();
}

export function signOutToLobby(clearAll = true): void {
  if (clearAll) {
    clearAllSessions();
  }
  window.location.hash = "/";
}

export function roomUrl(roomCode: string): string {
  return `${window.location.origin}${window.location.pathname}#/room/${roomCode.toUpperCase()}`;
}
