/**
 * Clears the vote store.
 *
 *   node scripts/reset-votes.mjs [--all]
 *
 * By default this wipes votes, sessions, nonces and rate-limit buckets, which
 * is what you want between test runs or before opening a real round. Pass
 * --all to delete the database file outright.
 */
import { DatabaseSync } from "node:sqlite";
import { existsSync, rmSync } from "node:fs";
import { resolve } from "node:path";

const path = resolve(process.env.DATABASE_PATH ?? "./data/votes.db");

if (!existsSync(path)) {
  console.log(`Nothing to do - no database at ${path}`);
  process.exit(0);
}

if (process.argv.includes("--all")) {
  for (const suffix of ["", "-wal", "-shm"]) rmSync(path + suffix, { force: true });
  console.log(`Deleted ${path}`);
  process.exit(0);
}

const db = new DatabaseSync(path);
const before = db.prepare("SELECT COUNT(*) AS n FROM votes").get().n;
for (const table of ["votes", "sessions", "nonces", "rate"]) {
  db.exec(`DELETE FROM ${table}`);
}
db.close();
console.log(`Cleared ${before} vote(s) and all sessions from ${path}`);
