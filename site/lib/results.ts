import "server-only";
import { connection } from "next/server";
import { CANDIDATES } from "./candidates";
import { tally } from "./db";
import { getConfig } from "./config";
import { tokenMeta } from "./chain";

export type ResultRow = {
  candidateId: string;
  /** Decimal string. Raw token units in token mode, a plain count in equal mode. */
  weight: string;
  voters: number;
  /** 0..1, to four places. Computed in BigInt then narrowed, never in floats. */
  share: number;
};

export type Results = {
  mode: "equal" | "token";
  rows: ResultRow[];
  totalWeight: string;
  totalVoters: number;
  decimals: number;
  symbol: string;
  leaderId: string | null;
  /** True once at least one vote is in - the UI shows a different empty state. */
  hasVotes: boolean;
};

const SCALE = 10_000n;

export async function buildResults(): Promise<Results> {
  // node:sqlite is synchronous, so without this the query resolves during
  // prerendering and the standings freeze at build time. Next documents this
  // exact hazard for synchronous database drivers.
  await connection();

  const counted = tally();
  const byId = new Map(counted.map((r) => [r.candidateId, r]));
  const totalWeight = counted.reduce((acc, r) => acc + r.weight, 0n);
  const totalVoters = counted.reduce((acc, r) => acc + r.voters, 0);

  const rows: ResultRow[] = CANDIDATES.map((candidate) => {
    const row = byId.get(candidate.id);
    const weight = row?.weight ?? 0n;
    return {
      candidateId: candidate.id,
      weight: weight.toString(),
      voters: row?.voters ?? 0,
      share: totalWeight === 0n ? 0 : Number((weight * SCALE) / totalWeight) / Number(SCALE),
    };
  });

  const config = getConfig();
  const mode = config.ok ? config.config.mode : "equal";
  const meta = mode === "token" ? await tokenMeta() : { symbol: "votes", decimals: 0 };

  let leaderId: string | null = null;
  let best = 0n;
  for (const row of rows) {
    const weight = BigInt(row.weight);
    if (weight > best) {
      best = weight;
      leaderId = row.candidateId;
    } else if (weight === best && weight > 0n) {
      leaderId = null; // A tie has no leader to crown.
    }
  }

  return {
    mode,
    rows,
    totalWeight: totalWeight.toString(),
    totalVoters,
    decimals: meta.decimals,
    symbol: meta.symbol,
    leaderId,
    hasVotes: totalVoters > 0,
  };
}
