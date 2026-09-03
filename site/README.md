# HoodCraft — mascot vote

The site that decides which mascot unlocks the pet egg. Wallets sign in, the
server weighs each vote by token balance at a pinned block, and the winner
becomes the mod's second pet.

Built on the mod's own identity: the palette is sampled from the Robin's
textures, and every surface uses Minecraft's block bevel rather than a card
shadow.

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
  art: "/candidates/gold.jpg",      // or null to fall back to a drawn egg
  status: "sealed",      // "announced" once it has a name
  palette: ["#F0B23A", "#846426"],  // light face, dark face
}
```

**`id` is load-bearing.** Votes are rows keyed by candidate id, so renaming one
orphans every vote already cast for it. Pick ids you can live with before the
round opens.

Each slot ships with a colour-matched egg render in `public/candidates/`.
Set `art: null` on a slot with no artwork and it falls back to a drawn pixel
egg in its palette on a recoloured missing-texture checker -- a deliberate
"not announced yet" state rather than a broken image.

Swapping an image? **Use a new filename.** Next's image optimiser caches by
URL, so new bytes under an old name keep serving the old picture.

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

## Can the site steal anyone's funds?

No, and not because of a promise -- because of what it is able to ask for.

The site only ever calls `personal_sign`. That produces a signature over a
plain text message. It cannot move a token, cannot approve a spender, and
cannot touch a contract. There is no `eth_sendTransaction` anywhere in this
codebase, the server holds no private keys, and nobody is ever asked for a seed
phrase. Even a total compromise of the server gets an attacker the vote
database -- which is public information anyway -- and nothing else.

**The real risk is different, and worth understanding.** If someone takes over
the domain or the deployment, they can serve a page that asks a visitor's
wallet for something else: a transaction, or a token approval. Visitors who are
used to signing here may approve it. That is how wallet drainers work in
practice -- they do not break cryptography, they get a person to sign
something.

So the security work is about keeping control of what gets served:

- **A strict Content-Security-Policy** ([`proxy.ts`](proxy.ts)) with a
  per-request nonce and `strict-dynamic`. An injected `<script>` does not run,
  even if an attacker gets HTML onto the page. `connect-src 'self'` means the
  page cannot talk to any third-party endpoint, so nothing can be exfiltrated
  to one either. `frame-ancestors 'none'` stops the page being framed to
  clickjack a wallet prompt.
- **No third-party JavaScript at all.** No analytics, no tag manager, no CDN
  script, no wallet SDK. Wallets are found through EIP-6963, which is a browser
  event, not a download. Every third-party script is someone else's ability to
  change your page; there are none here, and it is worth keeping it that way.
- **Four runtime dependencies**: `next`, `react`, `react-dom`, `viem`. A small
  dependency tree is a small supply-chain surface.
- **Lock down the accounts.** Realistically the most likely way this gets
  attacked is not the code -- it is the registrar or the Railway login. Turn on
  2FA for both, and enable registrar/transfer lock on the domain.

If you ever add a feature that needs a transaction, that changes this analysis
completely. Until then, the honest summary is: signing in here is as safe as
signing a message can be, and the thing to guard is the deployment.

---

## Deploying to Railway

Railway works because it offers a **persistent volume**, which this app needs.

1. **New project -> Deploy from GitHub repo**, pick this repository.
2. **Add a Volume**, mount path `/data`.
3. **Variables** (Settings -> Variables):

   ```ini
   DATABASE_PATH=/data/votes.db      # must be inside the volume
   APP_ORIGIN=https://your-domain    # exact public origin, no trailing slash
   VOTE_MODE=token
   CHAIN_ID=...
   RPC_URL=...                       # secret; never NEXT_PUBLIC_
   TOKEN_ADDRESS=...
   TOKEN_SNAPSHOT_BLOCK=...
   ```
4. **Settings -> Networking -> Custom Domain**, then add the CNAME Railway
   gives you at your registrar.
5. Update `APP_ORIGIN` to the custom domain and redeploy.

[`railway.json`](railway.json) sets the build and start commands, a health
check on `/api/results`, and pins `numReplicas` to 1.

**Three ways to lose every vote, all avoidable:**

- **No volume, or `DATABASE_PATH` outside it.** The container filesystem is
  wiped on every deploy. The database must live under `/data`.
- **More than one replica.** SQLite is one file on one disk; a second instance
  cannot share it. Scale up only after moving [`lib/db.ts`](lib/db.ts) to
  Postgres -- every query is in that one file.
- **`APP_ORIGIN` not matching the real origin.** Sign-in messages are bound to
  it, so a mismatch makes every signature fail. Symptom: nobody can sign in.

On the first deploy to a new domain, consider setting `CSP_REPORT_ONLY=true`.
The policy is then reported but not enforced, so if some wallet trips it you
see the violation in the browser console instead of a broken site. Remove the
variable once you have connected a wallet successfully.

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
