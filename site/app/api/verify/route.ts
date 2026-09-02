import { getAddress, isAddress } from "viem";
import { consumeNonce, createSession, rateLimit } from "@/lib/db";
import { verifySignature, SESSION_TTL_MS } from "@/lib/siwe";
import { getConfig } from "@/lib/config";
import { balanceAtSnapshot, getPublicClient, ChainReadError } from "@/lib/chain";
import { setSessionCookie } from "@/lib/session";
import { json, fail, readJson, clientKey } from "@/lib/api";

/**
 * Step 2 of sign-in, and the only place vote weight is ever decided.
 *
 * The request body carries no balance and none is accepted if it does. Weight
 * comes from the server's own read of the chain at the pinned snapshot block.
 */
export async function POST(request: Request) {
  if (!rateLimit(`verify:${clientKey(request)}`, 20, 60_000)) {
    return fail(429, "Too many requests", "Wait a minute and try again.");
  }

  const body = await readJson<{ address?: string; nonce?: string; signature?: string }>(request);
  if (!body?.address || !isAddress(body.address)) return fail(400, "A valid wallet address is required.");
  if (!body.nonce || typeof body.nonce !== "string") return fail(400, "Missing nonce.");
  if (!body.signature || typeof body.signature !== "string" || !body.signature.startsWith("0x")) {
    return fail(400, "Missing signature.");
  }
  const address = getAddress(body.address);

  // Atomic claim. A replayed nonce loses the race and lands here.
  const record = consumeNonce(body.nonce, address);
  if (!record) return fail(401, "That sign-in request has expired or was already used.", "Try connecting again.");

  const valid = await verifySignature({
    message: record.message,
    address,
    signature: body.signature as `0x${string}`,
    client: getPublicClient(),
  });
  if (!valid) return fail(401, "Signature did not match that address.");

  const config = getConfig();
  if (!config.ok) {
    return fail(503, "Voting is not configured yet.", config.reason);
  }

  let weight: bigint;
  if (config.config.mode === "equal") {
    weight = 1n;
  } else {
    try {
      weight = await balanceAtSnapshot(address);
    } catch (error) {
      const detail = error instanceof ChainReadError ? error.message : "Unknown RPC failure.";
      return fail(502, "Could not read your balance from the chain.", detail);
    }
  }

  const id = createSession(address, weight, config.config.mode, SESSION_TTL_MS);
  await setSessionCookie(id, SESSION_TTL_MS);

  return json({ address, weight: weight.toString(), mode: config.config.mode });
}
