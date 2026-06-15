export const AVATAR_KEYS = ["avatar-1", "avatar-2", "avatar-3", "avatar-4", "avatar-5", "avatar-6"] as const;

export type AvatarKey = (typeof AVATAR_KEYS)[number];

export function avatarUrl(key: string): string {
  return `/avatars/${key}.svg`;
}

export function isAvatarKey(key: string): key is AvatarKey {
  return (AVATAR_KEYS as readonly string[]).includes(key);
}

export function defaultRoomName(date = new Date()): string {
  const label = date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  return `Sprint Planning ${label}`;
}

export function isCoffeeVote(value: string): boolean {
  return value.toLowerCase() === "coffee";
}
