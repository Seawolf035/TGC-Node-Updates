"""VRChat instance routing: never guess which client receives OSC."""
from __future__ import annotations

from dataclasses import dataclass


class TargetUnclear(RuntimeError):
    pass


@dataclass(frozen=True)
class VRChatSession:
    pid: int
    osc_port: int | None
    mode: str = "desktop"

    def __post_init__(self) -> None:
        if self.pid <= 0 or self.mode not in ("desktop", "vr"):
            raise ValueError("Invalid VRChat session")
        if self.osc_port is not None and not 1 <= self.osc_port <= 65535:
            raise ValueError("Invalid OSC port")


class SessionRouter:
    def __init__(self) -> None:
        self._sessions: dict[int, VRChatSession] = {}

    def replace(self, sessions: list[VRChatSession]) -> None:
        pids = [item.pid for item in sessions]
        if len(pids) != len(set(pids)):
            raise ValueError("Duplicate VRChat PID")
        self._sessions = {item.pid: item for item in sessions}

    def target(self, selected_pid: int | None = None) -> VRChatSession:
        if selected_pid is None:
            if len(self._sessions) != 1:
                raise TargetUnclear("Select a VRChat instance (none or multiple detected)")
            session = next(iter(self._sessions.values()))
        else:
            session = self._sessions.get(selected_pid)
            if session is None:
                raise TargetUnclear("Selected VRChat instance is no longer running")
        if session.osc_port is None:
            raise TargetUnclear("Selected instance has no verified OSC port mapping")
        return session

    def list_sessions(self) -> tuple[VRChatSession, ...]:
        return tuple(self._sessions.values())
