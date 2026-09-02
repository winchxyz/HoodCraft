import Image from "next/image";
import { CANDIDATES, INCUMBENT } from "@/lib/candidates";
import { buildResults } from "@/lib/results";
import { getConfig, publicConfig } from "@/lib/config";
import { VoteClient } from "@/components/VoteClient";

const REPO = "https://github.com/winchxyz/HoodCraft";

export default async function Page() {
  const results = await buildResults();
  const config = getConfig();
  const info = publicConfig();

  return (
    <>
      <header className="siteheader">
        <div className="wrap between">
          <a href="#top" className="siteheader__mark">
            <span style={{ color: "var(--green-lit)" }}>HOOD</span>
            <span>CRAFT</span>
          </a>
          <nav className="siteheader__nav hide-sm">
            <a href="#ballot">Vote</a>
            <a href="#loop">The loop</a>
            <a href="#weight">Weighting</a>
          </nav>
          <a className="btn btn--sm" href="#ballot">
            Vote
          </a>
        </div>
      </header>

      <main id="top">
        {/* ------------------------------------------------------------ hero */}
        <section className="hero">
          <div className="hero__banner">
            <Image
              src="/brand/banner.jpg"
              alt="HoodCraft — follow the bird, hatch your mascot"
              width={2448}
              height={816}
              priority
              className="hero__bannerimg"
            />
            <div className="hero__scrim" />
          </div>

          <div className="wrap hero__body">
            <p className="eyebrow">Mascot vote · round one</p>
            <h1 className="hero__title">
              One bird.
              <br />
              One locked egg.
            </h1>
            <p className="hero__lede">
              The pet egg stays locked while there is one mob, because nothing can hatch into
              itself. Your vote picks the second mascot.
            </p>

            {/* Says what a vote is worth, in words rather than jargon. */}
            <div className="row hero__facts">
              <span className="chip">
                {results.mode === "token" ? (
                  <>
                    Your vote is weighted by the <strong>tokens you held</strong>
                  </>
                ) : (
                  <>
                    Every wallet counts <strong>once</strong>
                  </>
                )}
              </span>
              {info.configured && info.mode === "token" && (
                <span className="chip">
                  measured at block&nbsp;<strong>{info.snapshotBlock}</strong>
                </span>
              )}
            </div>
          </div>
        </section>

        {/* ---------------------------------------------------------- ballot */}
        <section className="section section--tight" id="ballot">
          <div className="wrap">
            <p className="eyebrow">The ballot</p>
            <h2>Pick what hatches next.</h2>

            <article className="incumbent incumbent--slim panel">
              <div className="incumbent__art">
                {/* Sits just under the hero, so lazy-loading it only buys a visible pop-in. */}
                <Image
                  src="/mascots/robin.jpg"
                  alt=""
                  width={1408}
                  height={1408}
                  loading="eager"
                  className="incumbent__photo"
                />
              </div>
              <div>
                <span className="chip chip--live">Already hatched</span>
                <h3 style={{ marginTop: 10 }}>{INCUMBENT.name}</h3>
                <p className="small muted" style={{ marginTop: 4 }}>
                  Not on the ballot — it is the bird you already follow.
                </p>
              </div>
            </article>

            <VoteClient
              candidates={CANDIDATES}
              initialResults={results}
              configured={config.ok}
              configNote={config.ok ? null : config.reason}
            />
          </div>
        </section>

        {/* ------------------------------------------------------------ loop */}
        <section className="section" id="loop">
          <div className="wrap">
            <p className="eyebrow">The loop it plugs into</p>
            <h2>Bird, feather, brush, dust.</h2>

            <ol className="chain">
              {[
                {
                  src: "/items/robin.jpg",
                  name: "The Robin",
                  text: "Six hearts. Tamed with wheat seeds, and unlike a parrot, a pair breeds.",
                },
                {
                  src: "/items/feather.jpg",
                  name: "The Black Feather",
                  text: "What a Robin drops. The only feather the brush takes.",
                },
                {
                  src: "/items/brush.jpg",
                  name: "The Hood Brush",
                  text: "Feather, gold ingot, stick. Sixty-four uses.",
                },
                {
                  src: "/items/egg-mystery.jpg",
                  name: "The Egg",
                  text: "Locked until this vote resolves.",
                  locked: true,
                },
              ].map((step, index) => (
                <li key={step.name} className={step.locked ? "chain__step chain__step--locked" : "chain__step"}>
                  <div className="chain__tile block">
                    <Image src={step.src} alt="" width={1408} height={1408} className="chain__photo" />
                  </div>
                  <span className="chain__index display">{String(index + 1).padStart(2, "0")}</span>
                  <h3>{step.name}</h3>
                  <p className="small muted">{step.text}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        {/* ---------------------------------------------------------- weight */}
        <section className="section" id="weight">
          <div className="wrap">
            <p className="eyebrow">How weight works</p>
            <h2>Why you cannot vote twice.</h2>

            <div className="rules">
              <div className="rule panel">
                <span className="rule__num display">01</span>
                <h3>The server reads the balance</h3>
                <p className="small muted">
                  Your browser is never asked what it holds. Anything it volunteers is a number you
                  could edit in devtools.
                </p>
              </div>
              <div className="rule panel">
                <span className="rule__num display">02</span>
                <h3>The block is pinned</h3>
                <p className="small muted">
                  Against a live balance, one pile of tokens votes, moves wallet, and votes again. A
                  fixed block makes the second wallet worth zero.
                </p>
              </div>
              <div className="rule panel">
                <span className="rule__num display">03</span>
                <h3>Weight is linear</h3>
                <p className="small muted">
                  Quadratic needs to know wallets are people. Without that,{" "}
                  <span className="mono">&radic;a + &radic;b &gt; &radic;(a+b)</span> — splitting your
                  bag would buy more power.
                </p>
              </div>
            </div>

            {/* No source link until the site's own code is published: REPO is the
                mod, and pointing "check the source" at it would be misleading. */}
            <p className="small muted" style={{ marginTop: 20 }}>
              Signing in only proves the wallet is yours. It is not a transaction — no funds move,
              and nothing is approved to spend.
            </p>
          </div>
        </section>
      </main>

      <footer className="sitefooter">
        <div className="wrap between">
          <div>
            <p className="display" style={{ fontSize: 14, marginBottom: 8 }}>
              Follow the bird. Hatch your mascot.
            </p>
            <p className="small muted" style={{ margin: 0 }}>
              NeoForge mod for Minecraft 1.21.1, GPL-3.0. Not affiliated with Mojang or Robinhood.
            </p>
          </div>
          <div className="row">
            <a className="btn btn--ghost btn--sm" href={REPO}>
              GitHub
            </a>
            <a className="btn btn--ghost btn--sm" href={`${REPO}/releases`}>
              Download
            </a>
          </div>
        </div>
      </footer>
    </>
  );
}
