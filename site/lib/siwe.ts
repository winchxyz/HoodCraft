import "server-only";
import { recoverMessageAddress, type Address, type PublicClient } from "viem";

/**
 * Sign-In with Ethereum (EIP-4361), with one deliberate narrowing.
 *
 * The client never sends the message back to us. It sends only the address,
 * the nonce and the signature; the server looks the message up by nonce from
 * its own store and verifies against that. So there is no message parser here
 * at all - and a parser is exactly where this kind of flow usually springs a
 * leak, because anything the client can shape, the client can lie about.
 *
 * Nothing signed here is a transaction. personal_sign moves no funds and can
 * approve no spend; it only proves control of the address.
 */

export type SiweParams = {
  domain: string;
  address: Address;
  statement: string;
  uri: string;
  chainId: number;
  nonce: string;
  issuedAt: Date;
  expirationTime: Date;
};

/** Exact EIP-4361 layout. Blank lines around the statement are part of the spec. */
export function buildMessage(p: SiweParams): string {
  return [
    `${p.domain} wants you to sign in with your Ethereum account:`,
    p.address,
    "",
    p.statement,
    "",
    `URI: ${p.uri}`,
    "Version: 1",
    `Chain ID: ${p.chainId}`,
    `Nonce: ${p.nonce}`,
    `Issued At: ${p.issuedAt.toISOString()}`,
    `Expiration Time: ${p.expirationTime.toISOString()}`,
  ].join("\n");
}

/**
 * True when `signature` over `message` really was produced by `address`.
 *
 * Tries plain EOA recovery first, then EIP-1271 if a client is available, so
 * Safe and other smart-contract wallets are not shut out. A smart-contract
 * wallet produces a signature that recovers to nothing, which is why the
 * fallback exists rather than being an optimisation.
 */
export async function verifySignature(args: {
  message: string;
  address: Address;
  signature: `0x${string}`;
  client?: PublicClient | null;
}): Promise<boolean> {
  const { message, address, signature, client } = args;

  try {
    const recovered = await recoverMessageAddress({ message, signature });
    if (recovered.toLowerCase() === address.toLowerCase()) return true;
  } catch {
    // Malformed signature, or a contract signature that cannot be recovered.
    // Fall through to the EIP-1271 path rather than failing outright.
  }

  if (!client) return false;
  try {
    return await client.verifyMessage({ address, message, signature });
  } catch {
    return false;
  }
}

export const NONCE_TTL_MS = 5 * 60 * 1000;
export const SESSION_TTL_MS = 24 * 60 * 60 * 1000;

export const STATEMENT =
  "Sign in to vote for the next HoodCraft mascot. This is a signature, not a transaction - it moves no funds and grants no spending approval.";
