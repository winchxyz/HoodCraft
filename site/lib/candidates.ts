/**
 * THE BALLOT - this is the file to edit.
 *
 * Nothing else needs changing to run a real vote. Replace the sealed slots
 * below with the actual mascots, give each a stable `id` (ids are what votes
 * are recorded against, so changing one orphans the votes already cast), and
 * drop artwork into /public/mascots/.
 *
 * The slots ship sealed on purpose. Inventing names for real tokens would put
 * words in someone else's mouth, so they stay unnamed until you fill them in -
 * and a sealed egg is, conveniently, exactly what the mod is about.
 *
 * Each slot carries a colour-matched egg render. Set `art: null` on a slot with
 * no artwork and it falls back to a drawn pixel egg in its palette instead.
 */

export type CandidateStatus = "hatched" | "sealed" | "announced";

export type Candidate = {
  /** Stable. Votes are stored against this - renaming it discards those votes. */
  id: string;
  name: string;
  /** Token or project this mascot comes from. null while sealed. */
  ticker: string | null;
  blurb: string;
  /** What the mod gains if it wins. Shown as the pitch. */
  kit: string[];
  /** Path under /public, or null to render the sealed-egg placeholder. */
  art: string | null;
  status: CandidateStatus;
  /** Light and dark faces of its block swatch - top face and side face. */
  palette: [string, string];
};

/** Already in the mod. Not on the ballot: it is the bird you already follow. */
export const INCUMBENT: Candidate = {
  id: "robin",
  name: "The Robin",
  ticker: null,
  blurb: "Six hearts, tamed with wheat seeds, breeds in pairs. Drops the Black Feather.",
  kit: ["Perches on your shoulder", "Breeds, unlike a parrot", "Drops the Black Feather"],
  art: "/mascots/robin.jpg",
  status: "hatched",
  palette: ["#30E028", "#007A03"],
};

export const CANDIDATES: Candidate[] = [
  {
    id: "slot-i",
    name: "Candidate I",
    ticker: null,
    blurb: "Not yet announced.",
    kit: [],
    art: "/candidates/gold.jpg",
    status: "sealed",
    palette: ["#F0B23A", "#846426"],
  },
  {
    id: "slot-ii",
    name: "Candidate II",
    ticker: null,
    blurb: "Not yet announced.",
    kit: [],
    art: "/candidates/blue.jpg",
    status: "sealed",
    palette: ["#5AA9E6", "#26567F"],
  },
  {
    id: "slot-iii",
    name: "Candidate III",
    ticker: null,
    blurb: "Not yet announced.",
    kit: [],
    art: "/candidates/purple.jpg",
    status: "sealed",
    palette: ["#C86AD8", "#5C2A66"],
  },
  {
    id: "slot-iv",
    name: "Candidate IV",
    ticker: null,
    blurb: "Not yet announced.",
    kit: [],
    art: "/candidates/coral.jpg",
    status: "sealed",
    palette: ["#E86A5A", "#7F2B21"],
  },
];

export const CANDIDATE_IDS = new Set(CANDIDATES.map((c) => c.id));

export function findCandidate(id: string): Candidate | null {
  return CANDIDATES.find((c) => c.id === id) ?? null;
}
