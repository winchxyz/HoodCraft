#!/usr/bin/env python3
"""Check a built jar on a real NeoForge server, outside the dev environment.

The scripts next to this one drive `runClient` / `runServer`, which is the right place to test
whether the loot rolls at the rate you meant. It is the wrong place to test whether the thing you
are about to upload actually works, because the dev harness supplies a lot that a published jar has
to carry itself: resources come from `src/main/resources` rather than from inside the jar, and
anything the harness happened to register stays registered.

So this installs a clean NeoForge server into a cache directory, drops the jar into `mods/`, and
asks the running game whether the content is there. It also compares the jar against the source
resource tree, because a texture filtered out of the build looks perfectly fine on disk and shows up
in game as a missing-texture checkerboard.

    python tools/verify_release.py                       # newest jar in build/libs
    python tools/verify_release.py path/to/some.jar

The EULA is never accepted here. If `run/eula.txt` already says yes — it will, once you have run
`./gradlew runServer` — that answer is reused. Otherwise the script stops and asks you to do it.
"""

from __future__ import annotations

import collections
import json
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
NEOFORGE = "21.1.249"
CACHE = REPO / "build" / "release-test"
SERVER = CACHE / "server"
INSTALLER_URL = (
    "https://maven.neoforged.net/releases/net/neoforged/neoforge"
    f"/{NEOFORGE}/neoforge-{NEOFORGE}-installer.jar"
)
ARGS_FILE = f"@libraries/net/neoforged/neoforge/{NEOFORGE}/{'win' if sys.platform == 'win32' else 'unix'}_args.txt"

# Only the checks a server can answer. Rendering needs a client; the asset audit below covers the
# part of the client story that packaging can break.
CHECKS = [
    ("ray entity",       "summon hoodcraft:ray 0 -55 0",                                  "Summoned new"),
    ("cash cat entity",  "summon hoodcraft:cash_cat 2 -55 0",                             "Summoned new"),
    ("egg block",        "setblock 4 -55 0 hoodcraft:cash_cat_egg",                       "Changed the block"),
    ("hood brush",       'summon item 6 -55 0 {Item:{id:"hoodcraft:hood_brush",Count:1b}}',      "Summoned new"),
    ("black feather",    'summon item 6 -55 0 {Item:{id:"hoodcraft:black_feather",Count:1b}}',   "Summoned new"),
    ("ray spawn egg",    'summon item 6 -55 0 {Item:{id:"hoodcraft:ray_spawn_egg",Count:1b}}',   "Summoned new"),
    ("cat spawn egg",    'summon item 6 -55 0 {Item:{id:"hoodcraft:cash_cat_spawn_egg",Count:1b}}', "Summoned new"),
    ("brush loot table", "loot spawn 8 -55 0 loot hoodcraft:archaeology/hood_brushing",   "Dropped"),
    ("city loot table",  "loot spawn 8 -55 0 loot hoodcraft:archaeology/ancient_city",    "Dropped"),
    ("ray drop table",   "loot spawn 8 -55 0 loot hoodcraft:entities/ray",                "Dropped"),
]

EGG_ROLLS = 400
EGG_RATE = 1 / 15  # one entry of fifteen, vanilla's own sniffer-egg odds


def java21() -> Path:
    """The JDK the mod targets.

    `java` on PATH is not good enough to assume: this machine answers with a Java 8 JRE, which
    cannot even run the NeoForge installer. So look for a real 21 first and only fall back to PATH
    after checking what version it reports.
    """
    roots = [Path("C:/Program Files/Eclipse Adoptium"), Path("C:/Program Files/Java"),
             Path("C:/Program Files/Microsoft"), Path("/usr/lib/jvm")]
    candidates = []
    for root in roots:
        if root.is_dir():
            candidates += [d for d in root.iterdir() if d.is_dir() and "21" in d.name]
    for home in sorted(candidates):
        for exe in (home / "bin" / "java.exe", home / "bin" / "java"):
            if exe.exists():
                return exe

    on_path = shutil.which("java")
    if on_path:
        reported = subprocess.run([on_path, "-version"], capture_output=True, text=True).stderr
        if re.search(r'version "(2[1-9]|[3-9]\d)', reported):
            return Path(on_path)
        sys.exit(f"java on PATH is not 21+:\n{reported.strip().splitlines()[0]}")
    sys.exit("no Java found; this needs a JDK 21")


def install_server(java: Path) -> None:
    if (SERVER / "libraries").is_dir():
        print(f"reusing server in {SERVER.relative_to(REPO)}")
        return
    CACHE.mkdir(parents=True, exist_ok=True)
    installer = CACHE / f"neoforge-{NEOFORGE}-installer.jar"
    if not installer.exists():
        print(f"downloading NeoForge {NEOFORGE} installer")
        urllib.request.urlretrieve(INSTALLER_URL, installer)
    print("installing server")
    SERVER.mkdir(parents=True, exist_ok=True)
    # cwd matters: the installer drops a .log beside wherever it is run from, and the repository
    # root is not the place for it.
    done = subprocess.run([str(java), "-jar", str(installer), "--installServer", str(SERVER)],
                          cwd=CACHE, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit(f"installer failed:\n{done.stdout[-2000:]}\n{done.stderr[-2000:]}")


def carry_over_eula() -> None:
    """Reuse the answer already given for this project. Never write a fresh one."""
    if (SERVER / "eula.txt").exists():
        return
    theirs = REPO / "run" / "eula.txt"
    if theirs.exists() and "eula=true" in theirs.read_text(encoding="utf-8"):
        shutil.copy(theirs, SERVER / "eula.txt")
        return
    sys.exit("run/eula.txt does not say eula=true. Run ./gradlew runServer once and accept it there,\n"
             "or write eula=true into build/release-test/server/eula.txt yourself.")


def run_server(java: Path, script) -> list[str]:
    """Start the server, hand the console to `script`, and return the full log."""
    lines: list[str] = []
    up = threading.Event()

    def pump(stream):
        for raw in iter(stream.readline, ""):
            lines.append(raw.rstrip("\n"))
            if "Done (" in raw:
                up.set()
        stream.close()

    proc = subprocess.Popen([str(java), "-Xmx2G", ARGS_FILE, "nogui"], cwd=SERVER,
                            stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, text=True, bufsize=1)
    threading.Thread(target=pump, args=(proc.stdout,), daemon=True).start()
    if not up.wait(300):
        proc.kill()
        print("\n".join(lines[-30:]))
        sys.exit("server never finished starting")

    def send(command: str) -> None:
        proc.stdin.write(command + "\n")
        proc.stdin.flush()

    send("forceload add 0 0")
    time.sleep(3)
    try:
        script(send, lines)
    finally:
        send("stop")
        try:
            proc.wait(timeout=120)
        except subprocess.TimeoutExpired:
            proc.kill()
    return lines


def strip(line: str) -> str:
    return re.sub(r"^\[[^\]]+\] \[[^\]]+\] ", "", line)


def check_content(send, lines) -> list[tuple[bool, str, str]]:
    results = []
    for name, command, expect in CHECKS:
        mark = len(lines)
        send(command)
        time.sleep(1.6)
        said = " | ".join(strip(l) for l in lines[mark:] if "/INFO]" in l or "/ERROR]" in l)[:200]
        results.append((expect in said, name, said))
    return results


def check_egg_rate(send, lines) -> collections.Counter:
    mark = len(lines)
    for _ in range(EGG_ROLLS):
        send("loot spawn 8 -55 0 loot hoodcraft:archaeology/hood_brushing")
    time.sleep(20)
    send("kill @e[type=item]")
    time.sleep(2)
    drops: collections.Counter = collections.Counter()
    for line in lines[mark:]:
        found = re.search(r"Dropped \d+ \[(.+?)\]", line)
        if found:
            drops[found.group(1)] += 1
    return drops


def audit_assets(jar: Path) -> list[str]:
    """Everything in the source tree must be in the jar, and nothing may point at a file that is not."""
    problems = []
    src = REPO / "src" / "main" / "resources"
    with zipfile.ZipFile(jar) as zf:
        names = set(zf.namelist())
        for path in src.rglob("*"):
            if path.is_file() and path.relative_to(src).as_posix() not in names:
                problems.append(f"not packaged: {path.relative_to(src).as_posix()}")

        def resolve(ref: str, kind: str, ext: str) -> str:
            namespace, _, rest = ref.partition(":")
            if not rest:
                namespace, rest = "minecraft", namespace
            return f"assets/{namespace}/{kind}/{rest}{ext}"

        for name in sorted(names):
            if not (name.startswith("assets/hoodcraft/") and name.endswith(".json")):
                continue
            try:
                doc = json.loads(zf.read(name))
            except Exception as bad:
                problems.append(f"unparseable: {name} ({bad})")
                continue
            refs = []
            if isinstance(doc.get("parent"), str):
                refs.append((doc["parent"], "models", ".json"))
            for value in (doc.get("textures") or {}).values():
                if isinstance(value, str) and not value.startswith("#"):
                    refs.append((value, "textures", ".png"))
            for variant in (doc.get("variants") or {}).values():
                for entry in (variant if isinstance(variant, list) else [variant]):
                    if isinstance(entry, dict) and "model" in entry:
                        refs.append((entry["model"], "models", ".json"))
            for ref, kind, ext in refs:
                target = resolve(ref, kind, ext)
                if target.startswith("assets/hoodcraft/") and target not in names:
                    problems.append(f"{name} -> missing {target}")
    return problems


def main() -> int:
    # Redirected stdout on Windows defaults to cp1252, which cannot encode the sigma below.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    if len(sys.argv) > 1:
        jar = Path(sys.argv[1]).resolve()
    else:
        built = sorted((REPO / "build" / "libs").glob("hoodcraft-*.jar"),
                       key=lambda p: p.stat().st_mtime)
        if not built:
            sys.exit("no jar in build/libs — run ./gradlew build first")
        jar = built[-1]
    print(f"testing {jar.name} ({jar.stat().st_size:,} bytes)\n")

    java = java21()
    install_server(java)
    carry_over_eula()

    mods = SERVER / "mods"
    if mods.exists():
        shutil.rmtree(mods)
    mods.mkdir()
    shutil.copy(jar, mods)

    # Always a fresh world. `setblock` reports failure when the block it is asked to place is
    # already there, so a world left over from the previous run fails the egg check for a reason
    # that has nothing to do with the jar.
    world = SERVER / "releasetest"
    if world.exists():
        shutil.rmtree(world)
    (SERVER / "server.properties").write_text(
        "online-mode=false\nlevel-name=releasetest\nview-distance=4\n"
        "simulation-distance=4\nmax-players=1\nspawn-protection=0\nsync-chunk-writes=false\n",
        encoding="utf-8")

    outcome: dict = {}

    def script(send, lines):
        outcome["content"] = check_content(send, lines)
        outcome["drops"] = check_egg_rate(send, lines)

    log = run_server(java, script)

    print("=" * 78)
    for ok, name, said in outcome["content"]:
        print(f"  [{'PASS' if ok else 'FAIL'}] {name:18s} {said}")
    content_ok = sum(1 for ok, *_ in outcome["content"] if ok)
    print(f"  {content_ok}/{len(outcome['content'])} content checks passed")

    drops = outcome["drops"]
    total = sum(drops.values())
    print(f"\n  brush table, {total} rolls:")
    for item, count in drops.most_common():
        print(f"    {item:22s} {count:4d}  {100 * count / total:5.2f}%")
    eggs = drops.get("Cash Cat Egg", 0)
    # Binomial: flag only a genuine departure, not the noise 400 rolls always carries.
    sigma = (EGG_ROLLS * EGG_RATE * (1 - EGG_RATE)) ** 0.5
    off = abs(eggs - EGG_ROLLS * EGG_RATE) / sigma
    rate_ok = off < 3
    print(f"  egg rate {100 * eggs / total:.2f}% against {100 * EGG_RATE:.2f}% nominal — {off:.1f}σ")

    problems = audit_assets(jar)
    print(f"\n  asset audit: {'clean' if not problems else str(len(problems)) + ' problems'}")
    for problem in problems[:20]:
        print("    ", problem)

    errors = [l for l in log if ("/ERROR]" in l or "/FATAL]" in l) and "advanced terminal" not in l.lower()]
    print(f"  error lines in server log: {len(errors)}")
    for line in errors[:10]:
        print("    ", line[:180])
    print("=" * 78)

    good = content_ok == len(outcome["content"]) and rate_ok and not problems and not errors
    print("READY TO PUBLISH" if good else "NOT READY")
    return 0 if good else 1


if __name__ == "__main__":
    raise SystemExit(main())
