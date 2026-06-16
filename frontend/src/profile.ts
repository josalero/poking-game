import { isAvatarKey, type AvatarKey } from "./avatars";

const COOKIE_NAME = "scrum-poking-profile";
const MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export type SavedProfile = {
  displayName: string;
  avatarKey: AvatarKey;
};

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      return trimmed.slice(prefix.length);
    }
  }
  return null;
}

export function readProfile(): SavedProfile | null {
  const raw = readCookie(COOKIE_NAME);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(decodeURIComponent(raw)) as { displayName?: string; avatarKey?: string };
    const displayName = typeof parsed.displayName === "string" ? parsed.displayName.trim().slice(0, 40) : "";
    if (!displayName || !parsed.avatarKey || !isAvatarKey(parsed.avatarKey)) {
      return null;
    }
    return { displayName, avatarKey: parsed.avatarKey };
  } catch {
    return null;
  }
}

export function saveProfile(displayName: string, avatarKey: AvatarKey): void {
  const trimmed = displayName.trim().slice(0, 40);
  if (!trimmed || !isAvatarKey(avatarKey)) {
    return;
  }

  const value = encodeURIComponent(JSON.stringify({ displayName: trimmed, avatarKey }));
  document.cookie = `${COOKIE_NAME}=${value}; path=/; max-age=${MAX_AGE_SECONDS}; SameSite=Lax`;
}

export function clearProfile(): void {
  const expired = "Thu, 01 Jan 1970 00:00:00 GMT";
  document.cookie = `${COOKIE_NAME}=; path=/; max-age=0; expires=${expired}; SameSite=Lax`;
}

export function defaultProfileName(): string {
  return readProfile()?.displayName ?? "";
}

export function defaultProfileAvatar(): AvatarKey {
  return readProfile()?.avatarKey ?? "avatar-1";
}
