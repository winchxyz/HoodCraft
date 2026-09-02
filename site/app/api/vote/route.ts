import { castVote, rateLimit } from "@/lib/db";
import { CANDIDATE_IDS } from "@/lib/candidates";
import { readSession } from "@/lib/session";
import { buildResults } from "@/lib/results";
import { json, fail, readJson, clientKey } from "@/lib/api";

/**
 * Casting or changing a vote. Weight is taken from the session the server
 * created at sign-in, never from the request - the body carries a candidate id
 * and nothing else that matters.
 */
export async function POST(request: Request) {
  if (!rateLimit(`vote:${clientKey(request)}`, 30, 60_000)) {
    return fail(429, "Too many requests", "Wait a minute and try again.");
  }

  const session = await readSession();
  if (!session) return fail(401, "Connect your wallet before voting.");

  const body = await readJson<{ candidateId?: string }>(request);
  if (!body?.candidateId || typeof body.candidateId !== "string" || !CANDIDATE_IDS.has(body.candidateId)) {
    return fail(400, "Unknown candidate.");
  }

  if (session.weight <= 0n) {
    return fail(
      403,
      "This wallet held no tokens at the snapshot block.",
      "Weight is fixed at the snapshot, so tokens acquired since do not count.",
    );
  }

  castVote(session.address, body.candidateId, session.weight);
  return json({ ok: true, candidateId: body.candidateId, results: await buildResults() });
}
