import { readSession } from "@/lib/session";
import { getVote } from "@/lib/db";
import { json } from "@/lib/api";

export async function GET() {
  const session = await readSession();
  if (!session) return json({ connected: false });
  const vote = getVote(session.address);
  return json({
    connected: true,
    address: session.address,
    weight: session.weight.toString(),
    mode: session.mode,
    votedFor: vote?.candidateId ?? null,
  });
}
