# CurseForge submission

Everything needed to create the project, ready to paste. **The steps below have to be done by you**,
not automated: creating the project means signing in and accepting CurseForge's terms, and uploading
through their API needs a personal API token. Neither is something I can or should do on your behalf.

Once the project exists, uploads *can* be automated — see [Automating uploads](#automating-uploads).

---

## 1. Create the project

<https://console.curseforge.com/> → **Projects** → **Create Project**

| Field | Value |
| --- | --- |
| Name | `HoodCraft` |
| Summary | Tameable pets modelled on the mascots of Robinhood Chain tokens. |
| Category | **Mods** → *Adventure and RPG*, plus *Mobs* |
| Game | Minecraft |
| Mod loader | NeoForge |
| Licence | **GPL-3.0** — this is not optional, see the note at the bottom |
| Source URL | `https://github.com/winchxyz/HoodCraft` |
| Issues URL | `https://github.com/winchxyz/HoodCraft/issues` |

## 2. Description

Paste into the description editor (it accepts rich text; the headings and lists below map over
directly).

---

### HoodCraft

**Tameable pets modelled on the mascots of Robinhood Chain tokens.**

HoodCraft adds a small, self-contained progression that hangs off Minecraft's own archaeology loop.
It starts with a bird and a feather, and ends with you brushing dust off things nobody has touched
in a very long time.

**The Robin**

A Robinhood-green bird that behaves the way a parrot does — it flies, it perches on your shoulder,
it sits when you tell it to, and it never takes fall damage. Two things set it apart. You tame it
with **wheat seeds** rather than cookies, and unlike a parrot, a tamed pair can be **bred**. Look for
them in forest biomes.

**The Black Feather**

What a Robin drops. It is the only feather the Hood Brush can be built from.

**The Hood Brush**

A Black Feather, a gold ingot and a stick. It handles exactly like the vanilla brush — 64 uses,
4.8 seconds a block — but it uncovers a different set of things: a nautilus shell, an emerald,
leather boots or a stone hoe.

**Suspicious gravel in ancient cities**

Thirty percent of the loose cobbled deepslate on an ancient city's floors becomes suspicious gravel,
so there is somewhere new for a brush to be used. Vanilla's suspicious sand still behaves exactly as
it always did.

Unlike a vanilla brush, the Hood Brush also works on suspicious blocks you placed yourself, which is
handy for testing and no exploit — neither block can be obtained in survival at all. A block that has
already been brushed keeps whatever it rolled, so results cannot be re-rolled.

**Work in progress**

The pet egg block — three hatch stages, hatching in 30 minutes, 15 on wool, or 5 on slime or honey —
is built and tested, but deliberately cannot be obtained yet. With only one mob, and one that already
spawns in the world, an egg for it would be a circular reward. It comes back when there is a second
pet worth finding.

**Requirements**

Minecraft 1.21.1 and NeoForge 21.1.x. Nothing else — the Robin's model was rebuilt against vanilla,
so there is no dependency on Citadel despite the geometry originating in a mod that needs it.

**Credits**

The Robin's model geometry and bird calls derive from the Blue Jay in
[Alex's Mobs](https://www.curseforge.com/minecraft/mc-mods/alexs-mobs) by Alexthe666, used under
GPL-3.0.

---

## 3. Images

Upload from `docs/screenshots/`. All are 1600×900, comfortably over CurseForge's minimum.

| File | Use | Caption |
| --- | --- | --- |
| `hero.png` | **Main image / thumbnail** | Robins perched on a log |
| `portrait.png` | Gallery | The Robin, tamed with wheat seeds |
| `brush.png` | Gallery | The Hood Brush over suspicious sand and gravel |
| `items.png` | Gallery | Black Feather, Hood Brush, Robin Egg |
| `egg.png` | Gallery *(optional)* | Hatch stages on wool, slime and honey — WIP |

For the project avatar use `docs/branding/robin-avatar.jpg` (1408×1408). The same art, resized to
256×256, is `src/main/resources/hoodcraft.png` — the logo NeoForge shows in the in-game mod list.

`docs/branding/banner.jpg` (2448×816) is the wordmark banner. CurseForge has no banner slot of its
own, but it is the right image for a Modrinth gallery header, a Discord embed, or the top of a
forum post.

## 4. Upload the file

**Files** → **Upload File**

| Field | Value |
| --- | --- |
| File | `build/libs/hoodcraft-0.1.0.jar` |
| Display name | `HoodCraft 0.1.0` |
| Release type | **Beta** — the egg is WIP and shoulder perching still wants real play-testing |
| Game versions | 1.21.1 |
| Mod loader | NeoForge |
| Changelog | See below |

**Changelog for 0.1.0**

> First release.
>
> - The Robin: a tameable, breedable, shoulder-perching bird. Wheat seeds tame and breed it.
> - Black Feather drop, and the Hood Brush crafted from it.
> - Hood Brush uncovers a nautilus shell, an emerald, leather boots or a stone hoe.
> - Suspicious gravel now generates in ancient cities.
> - The pet egg block exists but is not obtainable yet — it returns with the second pet.

---

## Automating uploads

Once the project exists and you have its numeric ID, later releases can be pushed from CI. It needs
a CurseForge API token stored as a repository secret — **create the token yourself** at
<https://legacy.curseforge.com/account/api-tokens> and add it under
*Settings → Secrets and variables → Actions* as `CURSEFORGE_TOKEN`. Never paste a token into a file
or a chat.

A `.github/workflows/release.yml` using `Kir-Antipov/mc-publish` would then handle GitHub Releases,
CurseForge and Modrinth from one tag push. Worth setting up after the first manual upload, so the
first submission gets reviewed with a human behind it.

## A note on the licence

GPL-3.0 is inherited, not chosen: the Robin's geometry and sounds come from Alex's Mobs, which is
GPL-3.0 and copyleft. It has two practical consequences worth knowing before you publish:

- **The licence field on CurseForge must say GPL-3.0.** Picking "All Rights Reserved" would be a
  licence violation.
- **Anyone you give the jar to is entitled to the source.** The public GitHub repository satisfies
  this, which is a good reason to keep it public.
