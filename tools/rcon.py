#!/usr/bin/env python3
"""Minimal RCON client for driving a running HoodCraft dev server.

The dev server needs RCON switched on in `run/server.properties`:

    enable-rcon=true
    rcon.password=hoodtest
    rcon.port=25575
"""

from __future__ import annotations

import socket
import struct
import sys
import time

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "hoodtest"


class Rcon:
    def __init__(self, host: str = HOST, port: int = PORT, password: str = PASSWORD):
        self.host, self.port, self.password = host, port, password
        self.sock: socket.socket | None = None
        self.rid = 0

    def connect(self, timeout: float = 240) -> bool:
        """Retry until the server finishes booting and opens the RCON port."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                sock = socket.create_connection((self.host, self.port), timeout=10)
                sock.settimeout(120)
                self.sock = sock
                if self._auth():
                    return True
                sys.exit("RCON auth failed - check rcon.password in run/server.properties")
            except (ConnectionRefusedError, OSError):
                time.sleep(3)
        return False

    def cmd(self, command: str) -> str:
        self._send(2, command)
        return self._recv()[2]

    def passed(self, command: str) -> bool:
        """True when a bare `execute if/unless` reports its test passed.

        `execute ... run say X` is not usable for this: the text goes to chat and RCON gets
        an empty body back. Without `run`, execute returns its own pass/fail as the result.
        """
        return "Test passed" in self.cmd(command)

    # ------------------------------------------------------------- protocol

    def _send(self, ptype: int, body: str) -> int:
        self.rid += 1
        payload = struct.pack("<ii", self.rid, ptype) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)
        return self.rid

    def _recv(self) -> tuple[int, int, str]:
        (length,) = struct.unpack("<i", self._read_exactly(4))
        data = self._read_exactly(length)
        rid, ptype = struct.unpack("<ii", data[:8])
        return rid, ptype, data[8:-2].decode("utf8", "replace")

    def _read_exactly(self, n: int) -> bytes:
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("RCON connection closed")
            buf += chunk
        return buf

    def _auth(self) -> bool:
        sent = self._send(3, self.password)
        return self._recv()[0] == sent

    def gametime(self) -> int:
        """Current world game time in ticks."""
        out = self.cmd("time query gametime")
        digits = "".join(ch for ch in out if ch.isdigit())
        if not digits:
            raise RuntimeError(f"could not read gametime from {out!r}")
        return int(digits)

    def sprint(self, ticks: int) -> int:
        """Run `ticks` game ticks as fast as the server can, and wait until they have happened.

        `tick sprint` returns straight away and sprints in the background, and `tick query` says
        nothing about whether a sprint is in flight - so waiting on it silently does not wait at
        all, and every check afterwards races the sprint. Game time is the honest signal: it only
        moves when ticks actually run.
        """
        target = self.gametime() + ticks
        self.cmd(f"tick sprint {ticks}")
        for _ in range(600):
            now = self.gametime()
            if now >= target:
                return now
            time.sleep(0.5)
        raise TimeoutError(f"sprint of {ticks} ticks did not complete")


class Checks:
    """Collects pass/fail lines and prints them as they happen."""

    def __init__(self):
        self.results: list[bool] = []

    def __call__(self, label: str, ok: bool, detail: str = "") -> bool:
        self.results.append(bool(ok))
        print(f"  [{'PASS' if ok else 'FAIL'}] {label}" + (f"  {detail}" if detail else ""))
        return bool(ok)

    def summary(self) -> int:
        print(f"\n{sum(self.results)}/{len(self.results)} checks passed")
        return 0 if all(self.results) else 1
