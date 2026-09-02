import "server-only";
import { cookies } from "next/headers";
import { getSession, deleteSession } from "./db";

const COOKIE = "hc_session";

/**
 * The cookie carries an opaque random id and nothing else. Vote weight lives
 * server-side keyed to that id, so a user editing their own cookie can at
 * worst log themselves out.
 */
export async function setSessionCookie(id: string, maxAgeMs: number): Promise<void> {
  const jar = await cookies();
  jar.set(COOKIE, id, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: "/",
    maxAge: Math.floor(maxAgeMs / 1000),
  });
}

export async function readSession(): Promise<{ address: string; weight: bigint; mode: string } | null> {
  const jar = await cookies();
  const id = jar.get(COOKIE)?.value;
  if (!id) return null;
  return getSession(id);
}

export async function clearSession(): Promise<void> {
  const jar = await cookies();
  const id = jar.get(COOKIE)?.value;
  if (id) deleteSession(id);
  jar.delete(COOKIE);
}
