"""Transport-independent PCVR OSC command dispatcher (NO network listener).

A future encrypted pairing transport MUST supply authenticated_device from an
independently validated channel. Never accept client-provided user IDs,
permission flags or raw OSC bytes as authorization. The current GUI provides
no paired-device provider and cannot execute these commands.

The receiver supports only bounded, explicit actions; there is deliberately
no microphone hold/PTT, remote instance launching or arbitrary OSC route yet.
"""
from __future__ import annotations

from dataclasses import dataclass
from threading import RLock
import math
import re
import time
from typing import Callable

from .osc import LocalOscSender, avatar_parameter, chatbox, eye_height
from .permissions import AccessDenied, VerifiedAccess
from .sessions import SessionRouter


class RemoteCommandDenied(AccessDenied):
    pass


@dataclass(frozen=True)
class PairedDevice:
    """Trusted result from future secure transport, NOT incoming JSON."""

    device_id: str
    discord_id: str
    authorized_until: float

    def valid_for(self, owner_id: str, now: float) -> bool:
        return (
            bool(re.fullmatch(r"[A-Za-z0-9_-]{8,96}", self.device_id))
            and self.discord_id == owner_id
            and math.isfinite(self.authorized_until)
            and now < self.authorized_until
        )


@dataclass(frozen=True)
class OscRequest:
    """A strict high-level action; never accepts a raw OSC address or packet."""

    action: str
    target_pid: int
    sequence: int
    sent_at: float
    value: str | float | bool | int | None = None
    parameter: str | None = None


class PcvrOscBridge:
    """Gate paired Companion actions before local OSC delivery.

    No microphone controls are exposed, so dropped connections cannot leave a
    held microphone command active. Dispatch is serialized to prevent replay
    and same-device chatbox bursts.
    """

    def __init__(
        self,
        current_staff: Callable[[], VerifiedAccess | None],
        terms_accepted: Callable[[], bool],
        authenticated_device: Callable[[], PairedDevice | None],
        pcvr_mode_active: Callable[[], bool],
        sessions: SessionRouter,
        *,
        sender: LocalOscSender | None = None,
        clock: Callable[[], float] = time.time,
    ):
        self._staff = current_staff
        self._terms = terms_accepted
        self._device = authenticated_device
        self._pcvr = pcvr_mode_active
        self._sessions = sessions
        self._sender = sender or LocalOscSender()
        self._clock = clock
        self._last_sequence: dict[str, int] = {}
        self._last_chatbox: dict[str, float] = {}
        self._lock = RLock()

    def dispatch(self, request: OscRequest) -> None:
        with self._lock:
            now = self._clock()
            try:
                grant = self._staff()
                device = self._device()
                terms = self._terms() is True
                pcvr = self._pcvr() is True
            except Exception as exc:
                raise RemoteCommandDenied("Remote control authorization unavailable") from exc
            if not isinstance(grant, VerifiedAccess) or grant.valid_limit(now) < 1:
                raise RemoteCommandDenied("Staff authorization required")
            if not terms or not pcvr:
                raise RemoteCommandDenied("Desktop agreement or explicit PCVR mode required")
            if not isinstance(device, PairedDevice) or not device.valid_for(grant.discord_id, now):
                raise RemoteCommandDenied("Verified, paired device for this staff account required")
            if not isinstance(request.sequence, int) or isinstance(request.sequence, bool):
                raise RemoteCommandDenied("Invalid command sequence")
            if request.sequence < 1 or request.sequence <= self._last_sequence.get(device.device_id, 0):
                raise RemoteCommandDenied("Duplicate or out-of-order command")
            if not isinstance(request.sent_at, (float, int)) or not math.isfinite(request.sent_at):
                raise RemoteCommandDenied("Invalid command timestamp")
            if abs(now - request.sent_at) > 30:
                raise RemoteCommandDenied("Expired remote command")
            if not isinstance(request.target_pid, int) or isinstance(request.target_pid, bool):
                raise RemoteCommandDenied("VRChat target must be explicitly selected")
            target = self._sessions.target(request.target_pid)
            if request.action == "chatbox":
                if request.parameter is not None or not isinstance(request.value, str):
                    raise RemoteCommandDenied("Invalid chatbox request")
                last = self._last_chatbox.get(device.device_id, float("-inf"))
                if now - last < 5:
                    raise RemoteCommandDenied("Wait before sending another chatbox")
                message = chatbox(request.value)
            elif request.action == "eye_height":
                if request.parameter is not None or type(request.value) not in (float, int):
                    raise RemoteCommandDenied("Invalid avatar height")
                message = eye_height(request.value)
            elif request.action == "avatar_parameter":
                if not isinstance(request.parameter, str) or type(request.value) not in (bool, int, float):
                    raise RemoteCommandDenied("Invalid avatar parameter request")
                message = avatar_parameter(request.parameter, request.value)
            else:
                raise RemoteCommandDenied("Unsupported remote action")
            # All packets go to the selected, separately validated local PC
            # OSC port. Never directly to the Quest IP or an arbitrary host.
            self._sender.send(message, port=target.osc_port)
            self._last_sequence[device.device_id] = request.sequence
            if request.action == "chatbox":
                self._last_chatbox[device.device_id] = now
