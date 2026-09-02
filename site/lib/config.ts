import "server-only";
import { defineChain, getAddress, isAddress, type Address, type Chain } from "viem";

/**
 * Two ways to weigh a vote.
 *
 * "equal"  - every verified wallet counts once. Needs no token, no RPC, no chain.
 *            This is the default so the site runs the moment it is cloned.
 * "token"  - weight is the wallet's ERC-20 balance read from chain by the server
 *            at one pinned block. Needs the four TOKEN_* / CHAIN_* vars below.
 *
 * Weight is decided on the server in both modes. The browser never reports its
 * own balance, because a browser will happily report whatever it is told to.
 */
export type VoteMode = "equal" | "token";

export type TokenConfig = {
  chain: Chain;
  rpcUrl: string;
  token: Address;
  /** The block every balance is read at. Pinned, never "latest" - see note below. */
  snapshotBlock: bigint;
};

export type Config =
  | { mode: "equal"; token: null }
  | { mode: "token"; token: TokenConfig };

export type ConfigResult =
  | { ok: true; config: Config }
  | { ok: false; mode: VoteMode; missing: string[]; reason: string };

function read(name: string): string | null {
  const raw = process.env[name];
  if (raw == null) return null;
  const trimmed = raw.trim();
  return trimmed === "" ? null : trimmed;
}

let cached: ConfigResult | null = null;

/**
 * Never throws. An unconfigured deployment has to render a clear "not wired up
 * yet" state rather than a stack trace, because the contract address genuinely
 * may not exist yet when someone first runs this.
 */
export function getConfig(): ConfigResult {
  if (cached) return cached;
  cached = build();
  return cached;
}

function build(): ConfigResult {
  const modeRaw = (read("VOTE_MODE") ?? "equal").toLowerCase();
  if (modeRaw !== "equal" && modeRaw !== "token") {
    return {
      ok: false,
      mode: "equal",
      missing: ["VOTE_MODE"],
      reason: `VOTE_MODE must be "equal" or "token", got "${modeRaw}".`,
    };
  }
  const mode = modeRaw as VoteMode;

  if (mode === "equal") return { ok: true, config: { mode: "equal", token: null } };

  const chainIdRaw = read("CHAIN_ID");
  const rpcUrl = read("RPC_URL");
  const tokenRaw = read("TOKEN_ADDRESS");
  const blockRaw = read("TOKEN_SNAPSHOT_BLOCK");

  const missing: string[] = [];
  if (!chainIdRaw) missing.push("CHAIN_ID");
  if (!rpcUrl) missing.push("RPC_URL");
  if (!tokenRaw) missing.push("TOKEN_ADDRESS");
  if (!blockRaw) missing.push("TOKEN_SNAPSHOT_BLOCK");
  if (missing.length > 0) {
    return {
      ok: false,
      mode,
      missing,
      reason: "VOTE_MODE=token needs the chain and contract to read balances from.",
    };
  }

  const chainId = Number(chainIdRaw);
  if (!Number.isInteger(chainId) || chainId <= 0) {
    return { ok: false, mode, missing: ["CHAIN_ID"], reason: `CHAIN_ID must be a positive integer, got "${chainIdRaw}".` };
  }

  if (!isAddress(tokenRaw!)) {
    return { ok: false, mode, missing: ["TOKEN_ADDRESS"], reason: `TOKEN_ADDRESS is not a valid address: "${tokenRaw}".` };
  }

  // "latest" is deliberately not accepted. A moving snapshot lets one pile of
  // tokens vote repeatedly by hopping wallets between reads; pinning the block
  // is the whole defence, so it is not something to make optional.
  let snapshotBlock: bigint;
  try {
    snapshotBlock = BigInt(blockRaw!);
  } catch {
    return {
      ok: false,
      mode,
      missing: ["TOKEN_SNAPSHOT_BLOCK"],
      reason: `TOKEN_SNAPSHOT_BLOCK must be a block number, got "${blockRaw}". "latest" is not allowed - a moving snapshot lets the same tokens vote twice.`,
    };
  }
  if (snapshotBlock <= 0n) {
    return { ok: false, mode, missing: ["TOKEN_SNAPSHOT_BLOCK"], reason: "TOKEN_SNAPSHOT_BLOCK must be greater than zero." };
  }

  const chain = defineChain({
    id: chainId,
    name: read("CHAIN_NAME") ?? `Chain ${chainId}`,
    nativeCurrency: {
      name: read("CHAIN_CURRENCY_NAME") ?? "Ether",
      symbol: read("CHAIN_CURRENCY_SYMBOL") ?? "ETH",
      decimals: Number(read("CHAIN_CURRENCY_DECIMALS") ?? 18),
    },
    rpcUrls: { default: { http: [rpcUrl!] } },
    ...(read("EXPLORER_URL")
      ? { blockExplorers: { default: { name: "Explorer", url: read("EXPLORER_URL")! } } }
      : {}),
  });

  return {
    ok: true,
    config: {
      mode: "token",
      token: { chain, rpcUrl: rpcUrl!, token: getAddress(tokenRaw!), snapshotBlock },
    },
  };
}

/** Where sessions are bound. Set in production; derived from the request in dev. */
export function expectedOrigin(requestOrigin: string | null): string | null {
  return read("APP_ORIGIN") ?? requestOrigin;
}

export function databasePath(): string {
  return read("DATABASE_PATH") ?? "./data/votes.db";
}

/** Public, non-secret view of the config for the browser. Never includes RPC_URL. */
export function publicConfig() {
  const result = getConfig();
  if (!result.ok) {
    return { mode: result.mode, configured: false as const, missing: result.missing, reason: result.reason };
  }
  const { config } = result;
  if (config.mode === "equal") {
    return { mode: "equal" as const, configured: true as const };
  }
  return {
    mode: "token" as const,
    configured: true as const,
    chainId: config.token.chain.id,
    chainName: config.token.chain.name,
    tokenAddress: config.token.token,
    snapshotBlock: config.token.snapshotBlock.toString(),
    explorerUrl: config.token.chain.blockExplorers?.default.url ?? null,
  };
}
