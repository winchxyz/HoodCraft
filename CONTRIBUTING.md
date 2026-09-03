# Developing HoodCraft

**Minecraft 1.21.1 · NeoForge 21.1.249 · Java 21 · ModDevGradle 2.0.146**

```bash
./gradlew build          # jar to build/libs/
./gradlew runClient      # dev client
./gradlew runServer      # dev server
```

## Layout

```
textures/                     hand-drawn source art — the drawing folder, named freely
tools/                        asset pipeline, verification and screenshot scripts
docs/screenshots/             generated release media
src/main/java/com/hoodcraft/
  registry/                   every DeferredRegister; adding content starts here
  entity/                     mobs, and entity/ai/ for their goals
  block/                      HoodEggBlock — one class, parameterised per pet
  item/                       HoodBrushItem
  loot/                       RandomHoodEggFunction — holds the egg rate at 6.7%
  client/                     models, renderers, the shoulder layer
src/main/resources/
  assets/hoodcraft/           built resource tree — generated, do not hand-edit textures here
  data/hoodcraft/             loot tables, recipes, tags, worldgen
  data/minecraft/             the two vanilla overrides (see Conflicts)
  META-INF/accesstransformer.cfg
```

## Asset pipeline

Art is drawn into `textures/` under whatever filename is convenient. Two scripts turn that into the
resource tree the game reads; both only ever **read** from `textures/`, so redrawing and re-running
is always safe.

```bash
python tools/sync_assets.py           # copy hand-drawn art into assets/, normalising canvas size
python tools/sync_assets.py --check   # report what would change, write nothing
python tools/make_egg_textures.py     # generate the three egg hatch-stage sprites per pet
```

`sync_assets.py` never resamples: an off-size sprite has its drawn area cropped out of the
transparent margin and re-centred, so pixel art is never blurred by an accidental resize.

## Verifying

A compile proves the API calls are right. It says nothing about whether the loot rolls at the rate
you meant, whether the egg hatches on schedule, or whether the gravel really turns up in a cave. Two
scripts drive a running dev server over RCON and check those directly.

Add this to `run/server.properties` once:

```
enable-rcon=true
rcon.password=hoodtest
rcon.port=25575
```

Then, from a fresh world:

```bash
rm -rf run/hoodcraft-test && ./gradlew runServer     # one shell
python tools/verify_gameplay.py                      # another
python tools/verify_worldgen.py
```

Each stops the server when it finishes. `verify_gameplay.py` rolls the brush table 2,400 times and
asserts the outcome — wide enough not to flake, tight enough to catch a weighting mistake.

**Start from a fresh world each time.** Minecraft keys its scheduled-tick set on position and block
alone, so scheduling a tick where one is already pending is dropped silently — an egg placed where a
previous run left one inherits the old timer and is measured against the wrong substrate. Vanilla's
sniffer egg behaves the same way, so this is worth knowing rather than working around: two eggs
placed in the same spot within one hatch window share a clock.

**Brushing needs its own pass, and a connected client.** It is a use-item-over-time action driven
by holding right-click: there is no vanilla command equivalent, and synthetic mouse input does not
reach the game reliably. So the mod registers a `/hcbrush <pos>` command that runs a full dig
through the real item code, gated on `FMLEnvironment.production` so it exists only in dev.

```bash
./gradlew runServer          # one shell
./gradlew runScreenshots     # another - joins as "Dev", which the brush routine needs
python tools/verify_brushing.py
```

This gap is not hypothetical. The loot table rolling correctly says nothing about whether the item
ever reaches the ground, and the mod shipped once with a brush that dug through blocks and dropped
nothing at all.

**Run the client too, and read its log.** A headless server never builds an entity renderer, so it
boots happily past a model that cannot bake — and an invisible mob is not something a server-side
check will ever report. Grep a `runClient` log for `Failed to create model`.

## Screenshots

Release media is staged over RCON rather than composed by hand, so shots are reproducible when the
art changes.

```bash
./gradlew runServer          # one shell
./gradlew runScreenshots     # another — joins the dev server automatically as "Dev"
python tools/stage_screenshots.py            # every scene, into docs/screenshots/
python tools/stage_screenshots.py hero egg   # just these
```

Two things about this are less obvious than they look. The game renders through OpenGL, so
`PrintWindow` returns black and the capture has to be an on-screen copy — which means the window is
brought to the front for each shot. And a bird pinned in the air with `NoGravity` renders frozen
mid-wingbeat, because the model picks its pose from `onGround()`; perched birds are placed with
`Sitting:1b` instead, which sets the pose straight from NBT and does not depend on physics.

## Adding a pet

Every pet is the same five pieces. Nothing outside this list needs to change.

1. **Entity** — a class in `entity/`, plus a line in `HCEntities`, attributes and spawn rules in
   `HCCommonEvents`, and a `sounds.json` entry.
2. **Egg block** — one line in `HCBlocks` pointing `HoodEggBlock` at the new entity type. The
   30/15/5-minute hatch logic is already generic.
3. **Items** — the egg's `BlockItem` and a spawn egg in `HCItems`; add both to `HCCreativeTabs`.
4. **Art** — draw the entity texture and spawn egg into `textures/`, add the rows to `MAPPING` in
   `sync_assets.py` and a row to `EGGS` in `make_egg_textures.py`, then run both.
5. **Data** — blockstate, block/item models, lang keys, an entity loot table, a block loot table,
   and the egg's id added to `data/hoodcraft/tags/item/hood_eggs.json`.

Step 5's tag is the one that matters. The brush's loot table names a single weight-1 egg entry and
`RandomHoodEggFunction` picks the species out of that tag at roll time. That is what would keep the
overall egg chance at 6.7% however many pets exist — listing each egg as its own loot entry instead
would make the combined chance climb with every pet added, turning 6.7% into 26% at five pets.

**Re-enabling the egg.** It is currently switched off: the tag is empty and there is no egg entry in
`hood_brushing.json`. The other four items kept their original weights (4/4/3/3 = 14) rather than
being rescaled, so adding a weight-1 egg entry back makes 15 again and the rate lands on 6.67%
exactly — vanilla's own sniffer-egg odds in warm ocean ruins, which is where the number came from.

## Perching

The Ray uses its own `PerchOnOwnerGoal` rather than vanilla's `LandOnOwnersShoulderGoal`, because
the vanilla one cannot work here. It is entirely passive: it never moves the bird, it only waits for
its bounding box to happen to intersect the owner's. A parrot lands on you because its wander goal
eventually flies it into you by chance.

That falls apart with a `TemptGoal` in the mix — `TemptGoal.tick()` halts the mob at 2.5 blocks, and
`FollowOwnerGoal` stops at 1, where the boxes still do not overlap. Since wheat seeds are the taming
*and* breeding food, a player who has just tamed a Ray is almost always holding the one item that
guarantees it never touches them.

**This cannot be tested headlessly.** `getOwner()` resolves through `level().getPlayerByUUID()`,
which searches the level's player list — a NeoForge `FakePlayer` is not in it, so neither a GameTest
nor an RCON script can give the bird a real owner. In game:

```
/data get entity @s ShoulderEntityLeft
```

Naming `hoodcraft:ray` means it mounted and any invisibility is a rendering problem in
`RayOnShoulderLayer`. Staying empty means the goal is not firing. The two need completely
different fixes, so check which before changing anything.

## Conflicts

This mod overrides two vanilla files:

- `data/minecraft/worldgen/processor_list/ancient_city_generic_degradation.json`
- `data/minecraft/worldgen/processor_list/ancient_city_start_degradation.json`

Both reproduce vanilla's own processors verbatim and append one rule turning 30% of ancient cities'
cobbled deepslate into suspicious gravel. Ancient cities contain no gravel of their own, which is
why cobbled deepslate — the rubble already on their floors — is what gets swapped.

Both are needed because the ancient city template pools split between them: `city_center` uses the
*start* list, while the entrance, the city-centre walls and the outlying structures use the
*generic* one. Overriding only one covers only half the city. The third list,
`ancient_city_walls_degradation`, is deliberately left alone — gravel is gravity-affected, and the
outer wall is the one place a falling block would leave a hole.

A datapack override replaces a file rather than merging into it, so any other mod touching these two
will conflict: last datapack loaded wins, and the loser's changes vanish silently.

## Access transformer

`META-INF/accesstransformer.cfg` opens up `BrushableBlockEntity.lootTable`, `lootTableSeed` and
`brushCount`. The Hood Brush swaps its own loot table onto a suspicious block just before brushing
it, and has to know what state that block is in first: which table it already carries, and whether
anything has been rolled out of it yet.

It deliberately primes player-placed blocks too, which vanilla refuses to do. Vanilla's reason is
to stop sniffer eggs being farmed, but neither suspicious sand nor gravel can be obtained in
survival — both have empty loot tables and drop nothing when broken — so the only way one gets
placed by hand is creative or a command, where the loot could simply be spawned anyway. The
restriction protected nothing and its one visible effect was a brush that appeared broken.
