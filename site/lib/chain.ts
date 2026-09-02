import "server-only";
import { createPublicClient, erc20Abi, http, type Address, type PublicClient } from "viem";
import { getConfig } from "./config";

/**
 * Every balance in this file is read by the server from its own RPC endpoint.
 * Nothing the browser says about its holdings is used anywhere - the browser
 * is not a source of truth about itself, and treating it as one is how
 * token-gated votes get drained by anyone who can open devtools.
 */

const globalRef = globalThis as unknown as { __hoodcraftClient?: PublicClient };

export function getPublicClient(): PublicClient | null {
  const result = getConfig();
  if (!result.ok || result.config.mode !== "token") return null;
  if (!globalRef.__hoodcraftClient) {
    globalRef.__hoodcraftClient = createPublicClient({
      chain: result.config.token.chain,
      transport: http(result.config.token.rpcUrl),
    }) as PublicClient;
  }
  return globalRef.__hoodcraftClient;
}

export class ChainReadError extends Error {
  constructor(message: string, readonly cause?: unknown) {
    super(message);
    this.name = "ChainReadError";
  }
}

/**
 * The wallet's balance at the pinned snapshot block.
 *
 * Reading at a fixed historical block is what stops one balance voting more
 * than once: moving tokens to a fresh wallet after the snapshot moves nothing,
 * because the new wallet held zero at that block.
 *
 * Needs an archive-capable RPC if the snapshot is older than the node's
 * pruning window - a plain node will reject the historical call.
 */
export async function balanceAtSnapshot(address: Address): Promise<bigint> {
  const result = getConfig();
  if (!result.ok || result.config.mode !== "token") {
    throw new ChainReadError("Token mode is not configured.");
  }
  const client = getPublicClient();
  if (!client) throw new ChainReadError("No RPC client available.");
  const { token, snapshotBlock } = result.config.token;

  try {
    return await client.readContract({
      address: token,
      abi: erc20Abi,
      functionName: "balanceOf",
      args: [address],
      blockNumber: snapshotBlock,
    });
  } catch (cause) {
    throw new ChainReadError(
      `Could not read balanceOf at block ${snapshotBlock}. If the snapshot is old, the RPC needs archive access.`,
      cause,
    );
  }
}

let metaCache: { symbol: string; decimals: number } | null = null;

export async function tokenMeta(): Promise<{ symbol: string; decimals: number }> {
  if (metaCache) return metaCache;
  const result = getConfig();
  if (!result.ok || result.config.mode !== "token") return { symbol: "VOTE", decimals: 0 };
  const client = getPublicClient();
  if (!client) return { symbol: "VOTE", decimals: 0 };
  const { token } = result.config.token;

  try {
    const [symbol, decimals] = await Promise.all([
      client.readContract({ address: token, abi: erc20Abi, functionName: "symbol" }),
      client.readContract({ address: token, abi: erc20Abi, functionName: "decimals" }),
    ]);
    metaCache = { symbol, decimals };
    return metaCache;
  } catch {
    // A token that does not implement the optional metadata calls still votes fine.
    return { symbol: "TOKEN", decimals: 18 };
  }
}
