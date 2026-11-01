"""Permission-aware replacement for Staff Launcher's PC VRChat launch slots.

Preserves the published Staff Launcher 1.1.6 command structure:
  launch.exe --osc=<input>:127.0.0.1:<output> [--no-vr]

The source's Steam launch.exe may spawn/hand off to a *different* VRChat.exe
PID. A spawn return PID is NOT trusted as the session PID. The injected
find_process(slot, exact_marker) adapter must discover the actual running
VRChat process and validate that exact OSC marker before using its PID.
No process discovery or executable launch is wired into the prototype GUI.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from threading import RLock
from time import time
from typing import Callable

from .permissions import AccessDenied, InstanceGate, VerifiedAccess
from .sessions import VRChatSession, SessionRouter


class LaunchDenied(AccessDenied):
    pass


@dataclass(frozen=True)
class LaunchSpec:
    slot: int
    mode: str
    executable: Path
    osc_input_port: int
    osc_output_port: int

    @property
    def marker(self) -> str:
        return f"--osc={self.osc_input_port}:127.0.0.1:{self.osc_output_port}"

    @property
    def argv(self) -> tuple[str, ...]:
        args = [str(self.executable), self.marker]
        if self.mode == "desktop":
            args.append("--no-vr")
        return tuple(args)


@dataclass(frozen=True)
class LaunchStatus:
    slot: int
    mode: str
    pid: int | None
    state: str


def build_launch_spec(
    slot: int, *, executable: str | Path, vr_slot: int = 0,
    base_in: int = 9001, base_out: int = 9101,
) -> LaunchSpec:
    if type(slot) is not int or not 1 <= slot <= 4:
        raise ValueError("Select a VRChat slot from 1 to 4")
    if type(vr_slot) is not int or not 0 <= vr_slot <= 4:
        raise ValueError("Select one VR slot from 0 to 4")
    if type(base_in) is not int or type(base_out) is not int:
        raise ValueError("OSC port bases must be integers")
    in_port, out_port = base_in + slot - 1, base_out + slot - 1
    if not (1024 <= in_port <= 65535 and 1024 <= out_port <= 65535):
        raise ValueError("Invalid per-instance OSC ports")
    if in_port == out_port:
        raise ValueError("OSC input/output ports must differ")
    exe = Path(executable)
    if exe.name.lower() != "launch.exe" or not exe.is_file():
        raise LaunchDenied("Choose the installed VRChat launch.exe")
    return LaunchSpec(slot, "vr" if slot == vr_slot else "desktop",
                      exe, in_port, out_port)


class ManagedVRChatLauncher:
    """Launch specific authorized slots, one VR client and up to four total.

    This class never kills or restarts VRChat, redeems Node credentials, reads
    or uploads logs, makes Relay requests, opens network ports, or bypasses
    authorization. A *trusted local* Windows process adapter supplies spawn
    and a validated real-VRChat PID finder. Call refresh periodically before
    exposing a process's OSC mapping in the eventual UI.
    """

    def __init__(
        self,
        current_staff: Callable[[], VerifiedAccess | None],
        terms_accepted: Callable[[], bool],
        spawn: Callable[[tuple[str, ...]], object],
        find_process: Callable[[int, str], int | None],
        *,
        executable: str | Path,
        vr_slot: int = 0,
        base_in: int = 9001,
        base_out: int = 9101,
        clock: Callable[[], float] = time,
    ):
        self._staff = current_staff
        self._terms = terms_accepted
        self._spawn = spawn
        self._find = find_process
        self._executable = executable
        self._vr_slot = vr_slot
        self._base_in = base_in
        self._base_out = base_out
        self._clock = clock
        self._gate = InstanceGate(current_staff)
        self._pending: dict[int, str] = {}
        self._running: dict[int, int] = {}
        self._lock = RLock()
        self.router = SessionRouter()

    def _spec(self, slot: int) -> LaunchSpec:
        return build_launch_spec(slot, executable=self._executable,
                                 vr_slot=self._vr_slot, base_in=self._base_in,
                                 base_out=self._base_out)

    @property
    def vr_slot(self) -> int:
        return self._vr_slot

    def refresh(self) -> tuple[LaunchStatus, ...]:
        """Reconcile discovered VRChat.exe PIDs; don't trust launch.exe PIDs.

        An unresolved in-flight launch remains reserved until actual VRChat
        appears or a staff operator performs a separately designed safe
        reconciliation. Never silently free an unresolved slot on timeout;
        a late VRChat launch could otherwise exceed the authorized limit.
        """
        with self._lock:
            running: dict[int, int] = {}
            sessions: list[VRChatSession] = []
            results = []
            for slot in range(1, 5):
                spec = self._spec(slot)
                pid = self._find(slot, spec.marker)
                if pid is not None:
                    if type(pid) is not int or pid <= 0:
                        raise RuntimeError("Process finder returned an invalid PID")
                    if pid in running.values():
                        raise RuntimeError("Two launch slots mapped to the same VRChat process")
                    running[slot] = pid
                    if slot in self._pending:
                        self._gate.release(self._pending.pop(slot))
                    # VRChat's OSC *receive* port is the --osc input side.
                    sessions.append(VRChatSession(pid, spec.osc_input_port, spec.mode))
                    results.append(LaunchStatus(slot, spec.mode, pid, "running"))
                elif slot in self._pending:
                    results.append(LaunchStatus(slot, spec.mode, None, "launching"))
                else:
                    results.append(LaunchStatus(slot, spec.mode, None, "stopped"))
            self._running = running
            self.router.replace(sessions)
            return tuple(results)

    def launch(self, slot: int) -> LaunchSpec:
        with self._lock:
            spec = self._spec(slot)
            try:
                terms = self._terms() is True
            except Exception as exc:
                raise LaunchDenied("Desktop Terms status unavailable") from exc
            if not terms:
                raise LaunchDenied("Accept the Co-Companion desktop Terms first")
            # Always check live process state; no stale launch-button cache.
            self.refresh()
            if slot in self._running or slot in self._pending:
                raise LaunchDenied("That VRChat slot is already running or launching")
            # All four slots use different fixed OSC mappings. The VR slot
            # setting permits at most ONE VR client through this controller.
            token = self._gate.reserve(len(self._running), now=self._clock())
            self._pending[slot] = token
            try:
                self._spawn(spec.argv)
            except BaseException:
                self._pending.pop(slot, None)
                self._gate.release(token)
                raise
            return spec

    def statuses(self) -> tuple[LaunchStatus, ...]:
        return self.refresh()

    def close(self) -> None:
        """Drop *local bookkeeping*; never terminate existing VRChat clients.

        A new instance of the controller must reconcile actual PC VRChat
        processes before launching. This is intentionally not a logout gate.
        """
        with self._lock:
            self._running = {}
            self.router.replace([])
            # Do not automatically free in-flight reservations while the
            # same launcher exists: their processes may start asynchronously.
