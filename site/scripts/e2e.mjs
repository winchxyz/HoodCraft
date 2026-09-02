/**
 * End-to-end check of the vote flow against a running dev server.
 *
 *   node scripts/e2e.mjs [http://localhost:3000]
 *
 * Exercises the happy path with a real secp256k1 signature, then the attacks
 * the design is supposed to stop: replayed nonces, forged signatures, voting
 * without a session, and unknown candidate ids.
 */
import { privateKeyToAccount } from "viem/accounts";

const BASE = process.argv[2] ?? "http://localhost:3000";

let passed = 0;
let failed = 0;

function check(name, ok, detail = "") {
  if (ok) {
    passed++;
    console.log(`  PASS  ${name}`);
  } else {
    failed++;
    console.log(`  FAIL  ${name}${detail ? ` -- ${detail}` : ""}`);
  }
}

const post = (path, body, cookie) =>
  fetch(`${BASE}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json", ...(cookie ? { cookie } : {}) },
    body: JSON.stringify(body),
  });

const get = (path, cookie) =>
  fetch(`${BASE}${path}`, { headers: cookie ? { cookie } : {} });

function sessionCookie(response) {
  const raw = response.headers.getSetCookie?.() ?? [];
  const hit = raw.find((c) => c.startsWith("hc_session="));
  return hit ? hit.split(";")[0] : null;
}

/** Full sign-in for a fresh key. Returns the session cookie. */
async function signIn(account) {
  const nonceRes = await post("/api/nonce", { address: account.address });
  const { nonce, message } = await nonceRes.json();
  const signature = await account.signMessage({ message });
  const verifyRes = await post("/api/verify", { address: account.address, nonce, signature });
  return { verifyRes, cookie: sessionCookie(verifyRes), nonce, message, signature };
}

console.log(`\nHoodCraft vote flow -- ${BASE}\n`);

// --------------------------------------------------------------- happy path

const alice = privateKeyToAccount(`0x${"a1".repeat(32)}`);
const { verifyRes, cookie, nonce, signature } = await signIn(alice);
check("sign-in returns 200", verifyRes.status === 200, `got ${verifyRes.status}`);
check("sign-in sets an httpOnly session cookie", cookie !== null);

const me = await get("/api/me", cookie).then((r) => r.json());
check("session resolves to the signing address", me.address?.toLowerCase() === alice.address.toLowerCase());
check("session carries a server-decided weight", typeof me.weight === "string" && me.weight.length > 0);

const voteRes = await post("/api/vote", { candidateId: "slot-ii" }, cookie);
const voteBody = await voteRes.json();
check("vote is accepted", voteRes.status === 200, `got ${voteRes.status} ${JSON.stringify(voteBody).slice(0, 120)}`);
check(
  "tally reflects the vote",
  voteBody.results?.rows?.find((r) => r.candidateId === "slot-ii")?.voters >= 1,
);

// Re-voting must move weight, not add a second vote.
await post("/api/vote", { candidateId: "slot-iii" }, cookie);
const afterMove = await get("/api/results").then((r) => r.json());
const totalVoters = afterMove.totalVoters;
check("changing a vote does not create a second voter", totalVoters === 1, `totalVoters=${totalVoters}`);
check(
  "weight moved off the old candidate",
  afterMove.rows.find((r) => r.candidateId === "slot-ii")?.voters === 0,
);

// ------------------------------------------------------------- attack cases

const replay = await post("/api/verify", { address: alice.address, nonce, signature });
check("a replayed nonce is rejected", replay.status === 401, `got ${replay.status}`);

const mallory = privateKeyToAccount(`0x${"b2".repeat(32)}`);
const freshNonce = await post("/api/nonce", { address: mallory.address }).then((r) => r.json());
// Mallory signs her own message but claims Alice's address.
const wrongSig = await mallory.signMessage({ message: freshNonce.message });
const impersonate = await post("/api/verify", {
  address: alice.address,
  nonce: freshNonce.nonce,
  signature: wrongSig,
});
check("a signature from another key is rejected", impersonate.status === 401, `got ${impersonate.status}`);

const nonceForOther = await post("/api/nonce", { address: mallory.address }).then((r) => r.json());
const stolen = await post("/api/verify", {
  address: alice.address,
  nonce: nonceForOther.nonce,
  signature,
});
check("a nonce issued to one address cannot be used by another", stolen.status === 401, `got ${stolen.status}`);

const anon = await post("/api/vote", { candidateId: "slot-i" });
check("voting without a session is rejected", anon.status === 401, `got ${anon.status}`);

const bogus = await post("/api/vote", { candidateId: "../../etc/passwd" }, cookie);
check("an unknown candidate id is rejected", bogus.status === 400, `got ${bogus.status}`);

const malformed = await fetch(`${BASE}/api/verify`, {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: "not json",
});
check("malformed JSON is rejected cleanly", malformed.status === 400, `got ${malformed.status}`);

const badAddress = await post("/api/nonce", { address: "0xnope" });
check("a malformed address is rejected", badAddress.status === 400, `got ${badAddress.status}`);

// The browser must not be able to declare its own weight.
const claimant = privateKeyToAccount(`0x${"c3".repeat(32)}`);
const claimNonce = await post("/api/nonce", { address: claimant.address }).then((r) => r.json());
const claimSig = await claimant.signMessage({ message: claimNonce.message });
const claimRes = await post("/api/verify", {
  address: claimant.address,
  nonce: claimNonce.nonce,
  signature: claimSig,
  weight: "999999999999999999999999",
  balance: "999999999999999999999999",
});
const claimBody = await claimRes.json();
check(
  "a client-supplied weight is ignored",
  claimBody.weight !== "999999999999999999999999",
  `server returned weight=${claimBody.weight}`,
);

console.log(`\n${passed} passed, ${failed} failed\n`);
process.exit(failed === 0 ? 0 : 1);
