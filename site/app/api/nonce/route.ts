import { getAddress, isAddress } from "viem";
import { newNonce, storeNonce, rateLimit } from "@/lib/db";
import { buildMessage, STATEMENT, NONCE_TTL_MS } from "@/lib/siwe";
import { getConfig, expectedOrigin } from "@/lib/config";
import { json, fail, readJson, clientKey, requestOrigin } from "@/lib/api";

/**
 * Step 1 of sign-in. Mints a single-use nonce and returns the exact message to
 * sign. The message is stored server-side; the client is never asked to send
 * it back, so it cannot alter what it claims to have signed.
 */
export async function POST(request: Request) {
  if (!rateLimit(`nonce:${clientKey(request)}`, 20, 60_000)) {
    return fail(429, "Too many requests", "Wait a minute and try again.");
  }

  const body = await readJson<{ address?: string }>(request);
  if (!body?.address || typeof body.address !== "string" || !isAddress(body.address)) {
    return fail(400, "A valid wallet address is required.");
  }
  const address = getAddress(body.address);

  const origin = expectedOrigin(requestOrigin(request));
  if (!origin) return fail(500, "Server origin could not be determined.");

  const config = getConfig();
  // Binding the signature to a chain id even in equal mode keeps the message
  // spec-valid. personal_sign is chain-agnostic, so this is a label, not a gate.
  const chainId = config.ok && config.config.mode === "token" ? config.config.token.chain.id : 1;

  const nonce = newNonce();
  const issuedAt = new Date();
  const expirationTime = new Date(issuedAt.getTime() + NONCE_TTL_MS);

  const message = buildMessage({
    domain: new URL(origin).host,
    address,
    statement: STATEMENT,
    uri: origin,
    chainId,
    nonce,
    issuedAt,
    expirationTime,
  });

  storeNonce(nonce, address, message, NONCE_TTL_MS);
  return json({ nonce, message, expiresAt: expirationTime.toISOString() });
}
