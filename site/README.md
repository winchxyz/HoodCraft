# HoodCraft — mascot vote

The site that decides which mascot unlocks the pet egg. Wallets sign in, the
server weighs each vote by token balance at a pinned block, and the winner
becomes the mod's second pet.

Built on the mod's own identity: the palette is sampled from the Robin's
textures, the item art is the shipped 16×16 sprites, and every surface uses
Minecraft's block bevel rather than a card shadow.

---

## Run it

```bash
npm install
npm run dev
```

It works immediately with no configuration. Out of the box `VOTE_MODE=equal`,
where every wallet that signs in counts once — real signatures, real sessions,
no token needed. Switch to token weighting when you have the contract.

---

## The two things to fill in

### 1. The ballot — [`lib/candidates.ts`](lib/candidates.ts)

Ships with four sealed slots. Replace them with the real mascots:

```ts
{
  id: "slot-i",          // stable! votes are stored against this
  name: "Candidate I",
  ticker: null,
  blurb: "...",
  kit: ["..."],
  art: null,             // "/mascots/whatever.jpg", or null for the sealed egg
  status: "sealed",      // "announced" once it has a name
  palette: ["#F0B23A", "#846426"],  // light face, dark face
}
```

**`id` is load-bearing.** Votes are rows keyed by candidate id, so renaming one
orphans every vote already cast for it. Pick ids you can live with before the
round opens.

A candidate with `art: null` renders a pixel egg in its own palette on a
recoloured missing-texture checker. That is the deliberate "not announced yet"
state, not a fallback for a broken image.

### 2. The token — `.env.local`

Copy `.env.example` to `.env.local` and set:

```ini
VOTE_MODE=token
CHAIN_ID=<numeric chain id>
RPC_URL=<your own endpoint — server-side only, never NEXT_PUBLIC_>
TOKEN_ADDRESS=<ERC-20 contract>
TOKEN_SNAPSHOT_BLOCK=<one fixed block number>
```

If the snapshot block is older than your node's pruning window, `RPC_URL` has
to be **archive-capable** or the historical read is rejected.

---

## How a vote is weighed

Three decisions, and they are the whole security model.

**The server reads the balance.** The browser is never asked what it holds and
no answer it volunteers is used. `balanceOf` is read over the server's own RPC
connection in [`lib/chain.ts`](lib/chain.ts). Anything else is a number the
voter can edit in devtools.

**The block is pinned.** Every balance is read at one fixed historical block.
Against a live balance, one pile of tokens votes, moves to a fresh wallet, and
votes again, as often as you like. Reading at a pinned block makes the second
wallet worth zero. `TOKEN_SNAPSHOT_BLOCK=latest` is rejected on purpose.

**Weight is linear.** Quadratic voting needs to know wallets are people.
Without that check it backfires — `√a + √b > √(a+b)`, so splitting a balance
across two wallets buys *more* power. Linear is the option that cannot be gamed
by splitting.

### Sign-in

EIP-4361 (Sign-In with Ethereum), with one narrowing: **the client never sends
the message back.** It receives a nonce and the message to sign, and returns
only `{address, nonce, signature}`. The server looks the message up by nonce
from its own store and verifies against that, so there is no message parser —
and a parser is exactly where this kind of flow usually springs a leak.

Nonces are single-use, bound to one address, and expire in five minutes. The
claim is atomic (`UPDATE ... WHERE used = 0`), so a replayed signature loses the
race. Smart-contract wallets are verified via EIP-1271 when an RPC is available.

Nothing signed here is a transaction. `personal_sign` moves no funds and
approves no spending.

---

## Scripts

| | |
| --- | --- |
| `npm run dev` | Dev server on :3000 |
| `npm run build` | Production build |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run test:e2e` | Vote-flow tests against a running server |
| `npm run reset` | Clear votes and sessions (`-- --all` deletes the db file) |

`test:e2e` signs with real keys and checks both the happy path and the attacks
the design is meant to stop: replayed nonces, signatures from the wrong key,
nonces reused across addresses, voting without a session, unknown candidate
ids, and a client trying to declare its own weight.

```bash
npm run dev          # in one terminal
npm run test:e2e     # in another
```

---

## Deploying

Set `APP_ORIGIN` to the real origin. Sign-in messages are bound to it, so
getting it wrong makes every signature fail.

**On the vote store:** votes live in SQLite via Node's built-in `node:sqlite`,
which needs a persistent filesystem. That rules out serverless platforms whose
disks reset between invocations — on Vercel or similar, votes will silently
vanish. Deploy to something with a real disk (a VM, or a container with a
mounted volume), or swap [`lib/db.ts`](lib/db.ts) for Postgres. The module is
small and every query is in that one file.

Requires **Node 22.5+** for `node:sqlite`; developed against Node 24.

---

## Layout

```
app/
  page.tsx            the site (server component)
  api/
    nonce/            step 1 — mint a nonce, return the message to sign
    verify/           step 2 — check the signature, read the balance, open a session
    vote/             cast or change a vote
    results/          public tallies
lib/
  candidates.ts       THE BALLOT — edit this
  config.ts           env parsing; never throws, reports what is missing
  chain.ts            balanceOf at the snapshot block
  siwe.ts             message construction and signature verification
  db.ts               the vote store
components/
  VoteClient.tsx      wallet discovery, sign-in, voting
  SealedEgg.tsx       the pixel egg for unannounced slots
  Tally.tsx           vote share as discrete blocks
```

Wallets are discovered via **EIP-6963**, so there is no hardcoded wallet list
and no WalletConnect project id to register for.
