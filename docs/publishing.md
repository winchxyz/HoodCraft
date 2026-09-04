# Publishing HoodCraft

One document for both Modrinth and CurseForge, because the description, images and changelog are the
same on each and two copies would drift apart. Platform-specific fields are in their own sections.

**What you have to do yourself.** Creating either project means signing in and accepting that
platform's terms, and uploading through their APIs needs a personal token. Neither is something I can
do for you, and tokens should never be pasted into a file or a chat. Everything below is ready to
paste once you are signed in.

---

## Pre-flight

Done on 4 September 2026, against `build/libs/hoodcraft-0.2.0.jar` (287,286 bytes):

| Check | Result |
| --- | --- |
| Loads on a clean NeoForge 21.1.249 dedicated server, outside the dev environment | starts in about eight seconds, no errors from the mod |
| Entities, blocks, items and spawn eggs registered | Ray, Cash Cat, Cash Cat Egg block, Hood Brush, Black Feather, both spawn eggs — **10 of 10** summon and place |
| Loot tables parse and roll | `archaeology/hood_brushing`, `archaeology/ancient_city` and `entities/ray` all produce their intended drops |
| `random_hood_egg`, the custom loot function, works in production | across five runs, **125 eggs in 2,000 rolls — 6.25%** against a nominal 6.67%. That is 0.75σ, ordinary sampling noise, and all five loot entries appear |
| Every source resource reaches the jar | 47 of 47 |
| Model and texture references resolve inside the jar | 21 references, none dangling |
| `neoforge.mods.toml` | modId `hoodcraft`, version `0.2.0`, licence `GPL-3.0-only`, issue tracker and `displayURL` set, NeoForge `[21.1.0,)`, Minecraft `[1.21.1,1.22)`, `hoodcraft.png` present |
| GPL-3.0 §4 — the licence travels with the program | the full text now ships at `META-INF/LICENSE` inside the jar. It did not before; the repository was compliant and the download was not |

The dev environment hides packaging faults — a resource filtered out of the build, a codec only ever
registered by the dev harness — so this ran against a real server rather than `runServer`. It is
repeatable for the next release:

```bash
./gradlew build
python tools/verify_release.py
```

**Not covered:** nobody but you has play-tested it, and no client has run the *packaged* jar (the dev
client reads the same resource tree, so the jar's assets were checked statically instead). Keep that
in mind when reading the release-channel note further down — it is the reason the instinct is to ship
this as a beta, and the reason 0.2.0 rather than 1.0.0 is the right number.

---

## Description

Both platforms take Markdown. Paste as-is.

---

### HoodCraft

**Tameable pets modelled on the mascots of Robinhood Chain tokens.**

A small, self-contained progression that hangs off Minecraft's own archaeology loop. It starts with a
bird and a feather, and ends with you brushing dust off something that hatches.

Two pets so far. More arrive as more mascots do.

**The Ray**

A green bird that behaves the way a parrot does — it flies, it perches on your shoulder, it sits when
told, and it never takes fall damage. Two things set it apart: you tame it with **wheat seeds** rather
than cookies, and unlike a parrot, a tamed pair can be **bred**. Forest biomes.

**The Cash Cat**

The crying cat. It sits where it spawns and weeps, which is its whole character. **Cooked salmon**
tames and breeds it, but salmon will not cheer it up — only a **gold ingot** does that, and only for
one Minecraft day, during which it drops the slouch, stops crying and behaves like an ordinary cat.
Feeding it *any* ingot is also a lottery: one time in ten thousand it turns up a buried treasure map.
Creepers and phantoms fear it exactly as they fear a vanilla cat.

**The Black Feather**

What a Ray drops, and the only feather the Hood Brush can be built from.

**The Hood Brush**

A Black Feather, a gold ingot and a stick. It handles exactly like the vanilla brush — 64 uses,
4.8 seconds a block — but uncovers a different set of things: a nautilus shell, an emerald, leather
boots, a stone hoe, or at **6.7%** a pet egg.

**Pet eggs**

Brushing is the only way to get one. Place it and wait: 30 minutes to hatch, 15 on wool, 5 on slime or
honey, through three visible cracking stages. The block underneath is re-read at every stage, so
moving an egg part-way through changes the time left.

Only pets you cannot otherwise find get an egg. The Ray spawns in forests and is where the Black
Feather comes from, so an egg for it would be a circular reward. The Cash Cat has one.

**Suspicious gravel in ancient cities**

Thirty percent of the loose cobbled deepslate on an ancient city's floors becomes suspicious gravel,
so a brush has somewhere new to be used. Vanilla's suspicious sand behaves as it always did.

Unlike a vanilla brush, the Hood Brush also works on suspicious blocks you placed yourself — handy for
testing and no exploit, since neither block can be obtained in survival at all. A block that has
already been brushed keeps whatever it rolled, so results cannot be re-rolled.

**Reference**

| | |
| --- | --- |
| Ray | 6 hearts · wheat seeds to tame (1 in 3) and breed · forest biomes |
| Cash Cat | 10 hearts · cooked salmon to tame (1 in 3) and breed · plains, savanna, taiga |
| Cheering a Cash Cat | one gold ingot, one Minecraft day (24,000 ticks) |
| Treasure map | any ingot, 1 in 10,000 |
| Hood Brush | 64 uses · 96 ticks (4.8 s) a block |
| Pet egg from brushing | 6.7% |
| Egg hatch | 30 min · 15 on wool · 5 on slime or honey |

**Requirements**

Minecraft 1.21.1 and NeoForge 21.1.x. Nothing else. The Ray's model was rebuilt against vanilla, so
there is no dependency on Citadel despite the geometry originating in a mod that needs it. The Cash
Cat is built on vanilla's own cat mesh, reposed.

**Credits and licence**

Released under **GPL-3.0-only**. The licence is inherited rather than chosen: the Ray's model geometry
and its bird calls derive from the Blue Jay in **Alex's Mobs** by Alexthe666, which is GPL-3.0 and
copyleft. The Cash Cat's geometry is vanilla's cat, reposed, and it uses vanilla cat sounds.
Source: <https://github.com/winchxyz/HoodCraft>

---

## Modrinth

Sign in, hover your avatar, **Create a project**. A project stays a private draft until you submit it,
and it cannot be submitted with no versions attached — so upload the jar before looking for the submit
button. Review takes about 24–48 hours.

| Field | Value |
| --- | --- |
| Name | `HoodCraft` |
| Slug | `hoodcraft` |
| Summary | Tameable pets modelled on the mascots of Robinhood Chain tokens, found by brushing suspicious sand and gravel. The Ray and the Cash Cat first, with more to follow. |
| Project type | Mod |
| Categories | **Adventure**, **Mobs** |
| Client side | **Required** — entity renderers and models |
| Server side | **Required** — entities, worldgen and loot live here |
| Licence | `GPL-3.0-only` |
| Source | `https://github.com/winchxyz/HoodCraft` |
| Issues | `https://github.com/winchxyz/HoodCraft/issues` |
| Icon | `src/main/resources/hoodcraft.png` (256×256) |

Modrinth reviews every new project before it goes public. Two of their content rules bear on this one,
so get them right the first time rather than in a rejection:

- **Clear and Honest Function.** The description has to describe what the mod actually does. The one
  above does. Do not add claims about the token, a price, a launch, or anything that reads as
  financial advice — that is where a crypto-adjacent project gets refused, not the theme itself.
- **Copyright and Reuploads.** Reused licensed content must credit its source. The Alex's Mobs
  attribution belongs in the project description, not only in the GitHub README — it is in the Credits
  block above.

Leave the vote site out. It is a private repository with no public URL, and a link to a token-weighted
vote is exactly the thing that turns a mod page into an advert.

## CurseForge

<https://authors.curseforge.com/#/projects/create/choose-game> — the authors portal, not the studios
console. Pick **Minecraft**, and the form fills in with the fields below.

| Field | Value |
| --- | --- |
| Name | `HoodCraft` |
| Summary | Tameable pets modelled on the mascots of Robinhood Chain tokens, found by brushing suspicious sand and gravel. The Ray and the Cash Cat first, with more to follow. |
| Game | Minecraft |
| Category | **Mods** → *Adventure and RPG*, plus *Mobs* |
| Mod loader | NeoForge |
| Licence | **GPL-3.0** — not optional, see below |
| Source URL | `https://github.com/winchxyz/HoodCraft` |
| Issues URL | `https://github.com/winchxyz/HoodCraft/issues` |
| Avatar | `docs/branding/ray-avatar.jpg` (1408×1408) |

CurseForge also reviews first uploads by hand, and is stricter than Modrinth about the licence field
matching reality.

---

## Images

| File | Size | Use |
| --- | --- | --- |
| `docs/screenshots/hero.png` | 1600×900 | Main gallery image — Rays perched on a log |
| `docs/screenshots/cashcat.png` | 1622×953 | A Cash Cat sitting and crying; the one behind has been fed a gold ingot |
| `docs/screenshots/portrait.png` | 1600×900 | The Ray in profile |
| `docs/screenshots/brush.png` | 1600×900 | The Hood Brush over suspicious sand and gravel |
| `docs/screenshots/egg.png` | 1622×953 | Hatch stages on honey, slime and wool |
| `docs/screenshots/items.png` | 1622×953 | Black Feather, Hood Brush, Cash Cat Egg |

`docs/branding/banner.jpg` (2448×816) is the wordmark. Neither platform has a header slot for it, but
it suits a Discord embed or a forum post. `docs/branding/social-preview.jpg` (1280×640) is the GitHub
social card.

## The file

| Field | Value |
| --- | --- |
| File | `build/libs/hoodcraft-0.2.0.jar` |
| Display name | `HoodCraft 0.2.0` |
| Version number (Modrinth) | `0.2.0` |
| Release channel | **Release** on both — see below |
| Game version | 1.21.1 |
| Loader | NeoForge |
| Dependencies | none |

**Release, not Beta.** The instinct is to ship a first upload as Beta, and the *Not covered* note
under Pre-flight is the honest reason to want to. But CurseForge requires a project to have at least
one **Release** file before it will sync with the CurseForge app — a Beta-only project is browsable
on the site and not installable from the launcher, which is how most people would get it. The
version number is already doing the work Beta would have done: 0.2.0 says early on its own.

Modrinth has no such constraint, but there is no reason for the two to disagree.

### Why 0.2.0 and not 0.1.0

`v0.1.0` is already a tag on GitHub, and it points seventeen commits back — to a build with the
**Robin**, before it was renamed, and before the Cash Cat existed at all. Publishing the current jar
as 0.1.0 would leave the version on Modrinth and CurseForge pointing at source that is not the source
it was built from. Under GPL-3.0 that is not merely untidy: anyone given the binary is entitled to
*its* source, and `v0.1.0` is not it.

Moving the existing tag would break it for anyone who has already fetched it, so the version moves
forward instead. 0.2.0 is also the honest semantic number — the entity ID changed from
`hoodcraft:robin` to `hoodcraft:ray`, which breaks any world that used the old one, and a whole
second mob arrived.

If you would rather launch on a round number, 1.0.0 is yours to take; nothing below depends on which
you pick beyond the strings.

**Changelog**

> First public release. Two pets, and an archaeology loop that leads to the second one.
>
> - **The Ray** — a tameable, breedable, shoulder-perching bird. Wheat seeds tame and breed it. Spawns
>   in forests, drops the Black Feather. Renamed from the Robin; its entity ID is now `hoodcraft:ray`,
>   so a world that used the old one will not carry the mob over.
> - **The Cash Cat** — sits and cries. Cooked salmon tames and breeds it; a gold ingot cheers it up for
>   one Minecraft day. Any ingot is a 1-in-10,000 buried treasure map. Creepers and phantoms fear it.
> - **The Hood Brush** — Black Feather, gold ingot, stick. Uncovers a nautilus shell, an emerald,
>   leather boots, a stone hoe, or at 6.7% a Cash Cat Egg.
> - **Cash Cat Egg** — three hatch stages. 30 minutes, 15 on wool, 5 on slime or honey.
> - **Suspicious gravel** now generates in ancient cities, on 30% of the loose cobbled deepslate.

---

## Later releases

Once both projects exist and you have their IDs, tag pushes can publish to GitHub Releases, CurseForge
and Modrinth in one step with `Kir-Antipov/mc-publish` in `.github/workflows/release.yml`. It needs two
secrets, and **you should create both yourself**:

- `CURSEFORGE_TOKEN` — <https://legacy.curseforge.com/account/api-tokens>
- `MODRINTH_TOKEN` — <https://modrinth.com/settings/pats>, scoped to *Create versions* only

Add them under *Settings → Secrets and variables → Actions*. Worth doing after the first manual upload,
so the submission that gets reviewed has a human behind it.

## A note on the licence

GPL-3.0 is inherited, not chosen — the Ray's geometry and sounds come from Alex's Mobs, which is
copyleft. Two practical consequences:

- **The licence field on both platforms must say GPL-3.0.** "All Rights Reserved" would be a licence
  violation.
- **Anyone you give the jar to is entitled to the source.** The public GitHub repository satisfies
  this, which is the reason to keep it public.
