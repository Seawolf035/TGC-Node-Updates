"""Windows native OSC controls and personal-account scoped local presets.

No Relay calls. Never select an OSC port by guessing; every send requires
the actual running VRChat PID from the Staff Launcher process router.
Keep held microphone PTT tracked so UI logout/window close can release it.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
from time import monotonic
from typing import Callable

from .osc import (LocalOscSender, OscError, chatbox, eye_height,
                  avatar_parameter, voice_input, chatbox_typing)
from .pilot_enrollment import _atomic_bytes
from .sessions import SessionRouter
from .vrchat_personal import USER_ID

MAX_PHRASES = 24
MAX_ROTATION = 8


class PersonalOscPresets:
    def __init__(self, directory: Path):
        self.path = directory / "personal_osc_presets.json"
        self._cache = self._load()

    def _load(self) -> dict:
        try:
            data = json.loads(self.path.read_text(encoding="utf-8"))
            return data if isinstance(data, dict) and len(data) <= 40 else {}
        except (OSError, ValueError):
            return {}

    @staticmethod
    def _validate_name(name: str) -> str:
        if not isinstance(name, str) or not 1 <= len(name.strip()) <= 40:
            raise ValueError("Name must be 1–40 characters")
        return name.strip()

    def _read(self, user_id: str) -> list[dict]:
        if not USER_ID.fullmatch(user_id):
            raise PermissionError("Sign into personal VRChat before editing saved OSC presets")
        rows = self._cache.get(user_id, [])
        if not isinstance(rows, list):
            return []
        return [r for r in rows[:MAX_PHRASES] if isinstance(r, dict)]

    def list(self, user_id: str) -> tuple[tuple[str, str], ...]:
        return tuple((str(r.get("name") or ""), str(r.get("text") or ""))
                     for r in self._read(user_id))

    def save(self, user_id: str, name: str, message: str) -> None:
        title=self._validate_name(name)
        chatbox(message)  # validates chatbox text, character cap and NUL
        rows=[r for r in self._read(user_id)
              if str(r.get("name") or "").casefold()!=title.casefold()]
        if len(rows)>=MAX_PHRASES:
            raise ValueError("Maximum 24 saved OSC messages")
        rows.append({"name":title,"text":message})
        self._cache[user_id]=rows
        self._persist()

    def delete(self, user_id: str, name: str) -> None:
        rows=[r for r in self._read(user_id) if r.get("name")!=name]
        self._cache[user_id]=rows
        self._persist()

    def _persist(self):
        # Personal chatbox text, NOT any API token, password or credential.
        _atomic_bytes(self.path,json.dumps(self._cache,indent=2).encode("utf-8"))


class WindowsOscControls:
    def __init__(
        self, router: SessionRouter, permitted: Callable[[], bool],
        *, sender: LocalOscSender | None = None, clock: Callable[[], float] = monotonic,
    ):
        self._router=router
        self._permit=permitted
        self._sender=sender or LocalOscSender()
        self._clock=clock
        self._last_chat=-1e10
        self._voice_pid: int | None = None
        self._voice_port: int | None = None
        self._rotation: tuple[str, ...] = ()
        self._rotation_index=0
        self._rotation_pid: int | None = None
        self._last_rotation=-1e10
        self._interval=10

    def _send(self, pid: int, raw: bytes):
        if self._permit() is not True:
            raise PermissionError("Verified Co-Companion staff access and Terms required")
        session=self._router.target(pid)
        self._sender.send(raw, port=session.osc_port)
        return session.osc_port

    def send_chat(self, pid: int, text: str):
        now=self._clock()
        if now-self._last_chat < 1.8:
            raise OscError("Wait before sending another OSC chatbox")
        port=self._send(pid,chatbox(text))
        self._last_chat=now
        return port

    def send_height(self, pid: int, value: float):
        return self._send(pid,eye_height(value))

    def send_parameter(self, pid: int, name: str, value: bool | int | float):
        return self._send(pid,avatar_parameter(name,value))

    def set_voice(self, pid: int, pressed: bool):
        if type(pressed) is not bool:
            raise ValueError("Voice state must be a boolean")
        if pressed:
            if self._voice_pid is not None:
                raise OscError("PTT already held — release first")
            port=self._send(pid,voice_input(True))
            self._voice_pid=pid
            self._voice_port=port
        elif self._voice_port is not None:
            self.release_voice()
        else:
            self._send(pid,voice_input(False))

    def release_voice(self):
        """Always attempt release EVEN when staff permission expired.

        VRChat can otherwise leave microphone pressed when the lease expires.
        We only release to the ORIGINAL local port, never an arbitrary remote.
        """
        port=self._voice_port
        self._voice_port=None
        self._voice_pid=None
        if port is not None:
            self._sender.send(voice_input(False),port=port)

    def set_rotation(self,pid:int,messages:list[str],interval_seconds:int=10):
        if type(interval_seconds) is not int or interval_seconds<5 or interval_seconds>3600:
            raise ValueError("OSC rotation interval must be 5–3600 seconds")
        if not 1<=len(messages)<=MAX_ROTATION:
            raise ValueError("Choose 1–8 rotating chatboxes")
        for text in messages:
            chatbox(text)
        self._send(pid,chatbox_typing(False))
        self._rotation=tuple(messages)
        self._rotation_pid=pid
        self._rotation_index=0
        self._interval=interval_seconds
        self._last_rotation=-1e10

    def stop_rotation(self):
        self._rotation=()
        self._rotation_pid=None

    @property
    def rotating(self) -> bool:
        return bool(self._rotation)

    def rotate_tick(self) -> bool:
        """Called on Tk main thread, never via an unbounded background thread."""
        if not self.rotating:
            return False
        if self._permit() is not True:
            self.stop_rotation()
            return False
        now=self._clock()
        if now-self._last_rotation < self._interval or now-self._last_chat<1.8:
            return False
        pid=self._rotation_pid
        try:
            self._send(pid,chatbox(self._rotation[self._rotation_index]))
        except Exception:
            self.stop_rotation()
            raise
        self._last_chat=now
        self._last_rotation=now
        self._rotation_index=(self._rotation_index+1)%len(self._rotation)
        return True

    def shutdown(self):
        self.stop_rotation()
        self.release_voice()
