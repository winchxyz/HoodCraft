import "server-only";
import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { randomBytes } from "node:crypto";
import { databasePath } from "./config";

/**
 * node:sqlite ships with Node 22.5+, so the vote store needs no native module
 * and no build step. Balances are held as decimal TEXT rather than INTEGER:
 * an 18-decimal ERC-20 balance overflows both JS numbers and SQLite's signed
 * 64-bit INTEGER, so every total is summed as a BigInt in JS instead of SQL.
 */

type NonceRow = { nonce: string; address: string; message: string; issued_at: number; expires_at: number; used: number };
type SessionRow = { id: string; address: string; weight: string; mode: string; created_at: number; expires_at: number };
type VoteRow = { address: string; candidate_id: string; weight: string; voted_at: number };

const globalRef = globalThis as unknown as { __hoodcraftDb?: DatabaseSync };

function open(): DatabaseSync {
  const path = resolve(databasePath());
  mkdirSync(dirname(path), { recursive: true });
  const db = new DatabaseSync(path);
  db.exec("PRAGMA journal_mode = WAL");
  db.exec("PRAGMA foreign_keys = ON");
  db.exec(`
    CREATE TABLE IF NOT EXISTS nonces (
      nonce      TEXT PRIMARY KEY,
      address    TEXT NOT NULL,
      message    TEXT NOT NULL,
      issued_at  INTEGER NOT NULL,
      expires_at INTEGER NOT NULL,
      used       INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE IF NOT EXISTS sessions (
      id         TEXT PRIMARY KEY,
      address    TEXT NOT NULL,
      weight     TEXT NOT NULL,
      mode       TEXT NOT NULL,
      created_at INTEGER NOT NULL,
      expires_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS votes (
      address      TEXT PRIMARY KEY,
      candidate_id TEXT NOT NULL,
      weight       TEXT NOT NULL,
      voted_at     INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS rate (
      bucket       TEXT PRIMARY KEY,
      count        INTEGER NOT NULL,
      window_start INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_votes_candidate ON votes (candidate_id);
  `);
  return db;
}

export function getDb(): DatabaseSync {
  // Reused across dev hot reloads so we don't leak file handles.
  if (!globalRef.__hoodcraftDb) globalRef.__hoodcraftDb = open();
  return globalRef.__hoodcraftDb;
}

const now = () => Date.now();

// ---------------------------------------------------------------- nonces

export function newNonce(): string {
  return randomBytes(16).toString("hex");
}

/** The message embeds the nonce, so the nonce is minted first and stored alongside it. */
export function storeNonce(nonce: string, address: string, message: string, ttlMs: number): void {
  const db = getDb();
  db.prepare("INSERT INTO nonces (nonce, address, message, issued_at, expires_at) VALUES (?, ?, ?, ?, ?)").run(
    nonce,
    address.toLowerCase(),
    message,
    now(),
    now() + ttlMs,
  );
  db.prepare("DELETE FROM nonces WHERE expires_at < ?").run(now());
}

/**
 * Single-use. The UPDATE is the atomic claim - two concurrent verifies for the
 * same nonce cannot both see used=0, so a signature can never be replayed.
 */
export function consumeNonce(nonce: string, address: string): NonceRow | null {
  const db = getDb();
  const claimed = db
    .prepare("UPDATE nonces SET used = 1 WHERE nonce = ? AND address = ? AND used = 0 AND expires_at > ?")
    .run(nonce, address.toLowerCase(), now());
  if (claimed.changes !== 1) return null;
  return (db.prepare("SELECT * FROM nonces WHERE nonce = ?").get(nonce) as NonceRow) ?? null;
}

// -------------------------------------------------------------- sessions

export function createSession(address: string, weight: bigint, mode: string, ttlMs: number): string {
  const db = getDb();
  const id = randomBytes(32).toString("hex");
  db.prepare("INSERT INTO sessions (id, address, weight, mode, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)").run(
    id,
    address.toLowerCase(),
    weight.toString(),
    mode,
    now(),
    now() + ttlMs,
  );
  db.prepare("DELETE FROM sessions WHERE expires_at < ?").run(now());
  return id;
}

export function getSession(id: string): { address: string; weight: bigint; mode: string } | null {
  const row = getDb().prepare("SELECT * FROM sessions WHERE id = ? AND expires_at > ?").get(id, now()) as
    | SessionRow
    | undefined;
  if (!row) return null;
  return { address: row.address, weight: BigInt(row.weight), mode: row.mode };
}

export function deleteSession(id: string): void {
  getDb().prepare("DELETE FROM sessions WHERE id = ?").run(id);
}

// ----------------------------------------------------------------- votes

/** One row per address. Voting again replaces the previous choice. */
export function castVote(address: string, candidateId: string, weight: bigint): void {
  getDb()
    .prepare(
      `INSERT INTO votes (address, candidate_id, weight, voted_at) VALUES (?, ?, ?, ?)
       ON CONFLICT(address) DO UPDATE SET candidate_id = excluded.candidate_id,
                                          weight       = excluded.weight,
                                          voted_at     = excluded.voted_at`,
    )
    .run(address.toLowerCase(), candidateId, weight.toString(), now());
}

export function getVote(address: string): { candidateId: string; weight: bigint; votedAt: number } | null {
  const row = getDb().prepare("SELECT * FROM votes WHERE address = ?").get(address.toLowerCase()) as VoteRow | undefined;
  if (!row) return null;
  return { candidateId: row.candidate_id, weight: BigInt(row.weight), votedAt: row.voted_at };
}

export type Tally = { candidateId: string; weight: bigint; voters: number };

/** Summed in JS - SQLite's SUM would silently lose precision on 18-decimal balances. */
export function tally(): Tally[] {
  const rows = getDb().prepare("SELECT candidate_id, weight FROM votes").all() as Pick<
    VoteRow,
    "candidate_id" | "weight"
  >[];
  const acc = new Map<string, Tally>();
  for (const row of rows) {
    const current = acc.get(row.candidate_id) ?? { candidateId: row.candidate_id, weight: 0n, voters: 0 };
    current.weight += BigInt(row.weight);
    current.voters += 1;
    acc.set(row.candidate_id, current);
  }
  return [...acc.values()];
}

// ------------------------------------------------------------ rate limit

/** Fixed window. Returns true when the call is allowed. */
export function rateLimit(bucket: string, limit: number, windowMs: number): boolean {
  const db = getDb();
  const t = now();
  const row = db.prepare("SELECT * FROM rate WHERE bucket = ?").get(bucket) as
    | { bucket: string; count: number; window_start: number }
    | undefined;

  if (!row || t - row.window_start >= windowMs) {
    db.prepare(
      `INSERT INTO rate (bucket, count, window_start) VALUES (?, 1, ?)
       ON CONFLICT(bucket) DO UPDATE SET count = 1, window_start = excluded.window_start`,
    ).run(bucket, t);
    return true;
  }
  if (row.count >= limit) return false;
  db.prepare("UPDATE rate SET count = count + 1 WHERE bucket = ?").run(bucket);
  return true;
}
