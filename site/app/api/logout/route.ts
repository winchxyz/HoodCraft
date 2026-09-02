import { clearSession } from "@/lib/session";
import { json } from "@/lib/api";

/** Ends the session. The vote already cast stays counted - it was signed for. */
export async function POST() {
  await clearSession();
  return json({ ok: true });
}
