"use client";

import { useCallback, useEffect, useState } from "react";
import type { Candidate } from "@/lib/candidates";
import type { Results } from "@/lib/results";
import { formatShare, formatWeight, shortAddress } from "@/lib/format";
import { Tally } from "./Tally";
import { SealedEgg } from "./SealedEgg";

/* ---------------------------------------------------------------- wallets */

type Eip1193Provider = {
  request: (args: { method: string; params?: unknown[] }) => Promise<unknown>;
};

type ProviderDetail = {
  info: { uuid: string; name: string; icon: string; rdns: string };
  provider: Eip1193Provider;
};

/**
 * personal_sign takes the message hex-encoded. Doing it by hand here keeps
 * viem out of the client bundle entirely — the browser has no job that needs
 * it, since every signature it produces is checked on the server anyway.
 */
function toHex(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let out = "0x";
  for (const byte of bytes) out += byte.toString(16).padStart(2, "0");
  return out;
}

function errorMessage(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const code = (error as { code?: number }).code;
    // EIP-1193: 4001 is the user declining in their wallet, not a failure.
    if (code === 4001) return "Signature declined.";
    const message = (error as { message?: string }).message;
    if (typeof message === "string" && message) return message;
  }
  return "Something went wrong. Try again.";
}

type Session = { address: string; weight: string; votedFor: string | null };
type Phase = "idle" | "picking" | "connecting" | "signing" | "voting";

/* ------------------------------------------------------------------ view */

export function VoteClient({
  candidates,
  initialResults,
  configured,
  configNote,
}: {
  candidates: Candidate[];
  initialResults: Results;
  configured: boolean;
  configNote: string | null;
}) {
  const [wallets, setWallets] = useState<ProviderDetail[]>([]);
  const [results, setResults] = useState<Results>(initialResults);
  const [session, setSession] = useState<Session | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);

  /* EIP-6963: wallets announce themselves, so there is no hardcoded list and
     no WalletConnect project id to sign up for. */
  useEffect(() => {
    const found = new Map<string, ProviderDetail>();
    const onAnnounce = (event: Event) => {
      const detail = (event as CustomEvent<ProviderDetail>).detail;
      if (!detail?.info?.uuid || found.has(detail.info.uuid)) return;
      found.set(detail.info.uuid, detail);
      setWallets([...found.values()]);
    };
    window.addEventListener("eip6963:announceProvider", onAnnounce as EventListener);
    window.dispatchEvent(new Event("eip6963:requestProvider"));
    return () => window.removeEventListener("eip6963:announceProvider", onAnnounce as EventListener);
  }, []);

  const refreshResults = useCallback(async () => {
    try {
      const response = await fetch("/api/results", { cache: "no-store" });
      if (response.ok) setResults(await response.json());
    } catch {
      /* A failed refresh just leaves the last good standings on screen. */
    }
  }, []);

  // Restore an existing session on load, and re-check when the tab regains focus.
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const response = await fetch("/api/me", { cache: "no-store" });
        const data = await response.json();
        if (cancelled) return;
        setSession(data.connected ? { address: data.address, weight: data.weight, votedFor: data.votedFor } : null);
      } catch {
        /* Not signed in is the safe assumption. */
      }
    };
    load();
    const onFocus = () => { load(); refreshResults(); };
    window.addEventListener("focus", onFocus);
    return () => { cancelled = true; window.removeEventListener("focus", onFocus); };
  }, [refreshResults]);

  const connect = useCallback(async (detail: ProviderDetail) => {
    setError(null);
    setPhase("connecting");
    try {
      const accounts = (await detail.provider.request({ method: "eth_requestAccounts" })) as string[];
      const address = accounts?.[0];
      if (!address) throw new Error("That wallet returned no account.");

      const nonceResponse = await fetch("/api/nonce", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ address }),
      });
      const nonceData = await nonceResponse.json();
      if (!nonceResponse.ok) throw new Error(nonceData.detail ?? nonceData.error);

      setPhase("signing");
      const signature = (await detail.provider.request({
        method: "personal_sign",
        params: [toHex(nonceData.message), address],
      })) as string;

      const verifyResponse = await fetch("/api/verify", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ address, nonce: nonceData.nonce, signature }),
      });
      const verifyData = await verifyResponse.json();
      if (!verifyResponse.ok) throw new Error(verifyData.detail ?? verifyData.error);

      setSession({ address: verifyData.address, weight: verifyData.weight, votedFor: null });
      setPhase("idle");
      refreshResults();
    } catch (caught) {
      setError(errorMessage(caught));
      setPhase("idle");
    }
  }, [refreshResults]);

  const disconnect = useCallback(async () => {
    await fetch("/api/logout", { method: "POST" });
    setSession(null);
    setError(null);
  }, []);

  const vote = useCallback(async (candidateId: string) => {
    setError(null);
    setPhase("voting");
    try {
      const response = await fetch("/api/vote", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ candidateId }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.detail ?? data.error);
      setResults(data.results);
      setSession((current) => (current ? { ...current, votedFor: candidateId } : current));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPhase("idle");
    }
  }, []);

  const busy = phase !== "idle" && phase !== "picking";
  const hasWeight = session !== null && BigInt(session.weight || "0") > 0n;

  return (
    <div className="ballot">
      <div className="ballot__bar panel">
        {session ? (
          <div className="between" style={{ width: "100%" }}>
            <div className="row">
              <span className="chip chip--live">Signed in</span>
              <span className="mono">{shortAddress(session.address)}</span>
              <span className="chip">
                Weight&nbsp;<strong>{formatWeight(session.weight, results.decimals)}</strong>&nbsp;{results.symbol}
              </span>
            </div>
            <button className="btn btn--ghost btn--sm" onClick={disconnect}>
              Sign out
            </button>
          </div>
        ) : (
          <div className="between" style={{ width: "100%" }}>
            <div>
              <h3 style={{ marginBottom: 6 }}>Connect to vote</h3>
              <p className="small muted" style={{ margin: 0 }}>
                A signature, not a transaction. No funds move.
              </p>
            </div>
            {wallets.length === 0 ? (
              <span className="chip">No browser wallet detected</span>
            ) : (
              <div className="row">
                {wallets.map((wallet) => (
                  <button
                    key={wallet.info.uuid}
                    className="btn"
                    onClick={() => connect(wallet)}
                    disabled={busy}
                  >
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={wallet.info.icon} alt="" width={18} height={18} aria-hidden />
                    {phase === "connecting" ? "Check wallet…" : phase === "signing" ? "Sign to continue…" : wallet.info.name}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {error && (
        <p className="notice notice--bad small" role="alert">
          {error}
        </p>
      )}

      {!configured && (
        <p className="notice small">
          <strong>Voting is not wired up yet.</strong> {configNote}
        </p>
      )}

      <div className="ballot__grid">
        {candidates.map((candidate) => {
          const row = results.rows.find((r) => r.candidateId === candidate.id);
          const share = row?.share ?? 0;
          const isChoice = session?.votedFor === candidate.id;
          const isLeader = results.leaderId === candidate.id && results.hasVotes;

          return (
            <article key={candidate.id} className={isChoice ? "candidate panel candidate--chosen" : "candidate panel"}>
              <div
                className={candidate.art ? "candidate__art" : "candidate__art candidate__art--sealed"}
                style={
                  candidate.art
                    ? undefined
                    : ({
                        "--seal-dark": candidate.palette[1],
                        "--seal-light": `color-mix(in srgb, ${candidate.palette[1]} 82%, #ffffff)`,
                      } as React.CSSProperties)
                }
              >
                {candidate.art ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={candidate.art} alt={candidate.name} className="candidate__photo" />
                ) : (
                  <SealedEgg palette={candidate.palette} />
                )}
                {isLeader && <span className="candidate__crown">Leading</span>}
              </div>

              <div className="candidate__body">
                <div className="between" style={{ alignItems: "baseline" }}>
                  <h3>{candidate.name}</h3>
                  {candidate.ticker && <span className="mono muted">{candidate.ticker}</span>}
                </div>
                <p className="small muted">{candidate.blurb}</p>

                <Tally
                  share={share}
                  palette={candidate.palette}
                  label={`${candidate.name}: ${formatShare(share)} of the vote`}
                />

                <div className="between small">
                  <span className="display" style={{ fontSize: 18 }}>{formatShare(share)}</span>
                  <span className="muted mono">
                    {formatWeight(row?.weight ?? "0", results.decimals)} {results.symbol} · {row?.voters ?? 0}{" "}
                    {row?.voters === 1 ? "wallet" : "wallets"}
                  </span>
                </div>

                <button
                  className={isChoice ? "btn btn--gold" : "btn"}
                  onClick={() => vote(candidate.id)}
                  disabled={!session || !hasWeight || busy || !configured}
                  aria-disabled={!session || !hasWeight || busy || !configured}
                >
                  {isChoice ? "Your vote" : phase === "voting" ? "Recording…" : "Vote"}
                </button>
              </div>
            </article>
          );
        })}
      </div>

      <div className="between small muted">
        <span>
          {results.hasVotes
            ? `${results.totalVoters} ${results.totalVoters === 1 ? "wallet has" : "wallets have"} voted · ${formatWeight(results.totalWeight, results.decimals)} ${results.symbol} counted`
            : "No votes cast yet. The first one sets the pace."}
        </span>
        {session && hasWeight && <span>Voting again moves your weight.</span>}
        {session && !hasWeight && <span>Held nothing at the snapshot, so it cannot vote.</span>}
      </div>
    </div>
  );
}
