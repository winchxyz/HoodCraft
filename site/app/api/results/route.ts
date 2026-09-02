import { buildResults } from "@/lib/results";
import { json } from "@/lib/api";

/** Public tallies. No session needed - the standings are meant to be readable by anyone. */
export async function GET() {
  return json(await buildResults());
}
