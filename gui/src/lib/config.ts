/** Avatars are served by the original from its own storage; this port stores none. */
export function getAvatarUrl(avatarUrl?: string) {
  return avatarUrl || null;
}
