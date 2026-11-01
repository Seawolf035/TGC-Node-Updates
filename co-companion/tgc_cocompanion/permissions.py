"""Fail-closed launch-slot policy.

This module is deliberately NOT an entitlement issuer. In production the
authorization provider MUST authenticate the user and retrieve a fresh,
owner-approved limit from the TGC backend. A locally edited JSON file,
Discord rank, or unvalidated client claim must never grant extra slots.
"""
from __future__ import annotations

from dataclasses import dataclass
from threading import RLock
from time import time
from typing import Callable
from uuid import uuid4


class AccessDenied(PermissionError):
    pass


@dataclass(frozen=True)
class VerifiedAccess:
    """Result returned by a trusted server-verifying authorization adapter."""

    discord_id: str
    is_verified: bool
    max_instances: int
    expires_at: float

    def valid_limit(self, now: float) -> int:
        if not self.is_verified or not self.discord_id.isdecimal():
            return 0
        if not 1 <= self.max_instances <= 4 or now >= self.expires_at:
            return 0
        return self.max_instances


class InstanceGate:
    """Reserve launch slots atomically so simultaneous clicks cannot exceed cap.

    active_count is the *actual count of managed VRChat processes*. Reservations
    are temporary launch-in-progress claims and must be released after success
    or failure. Existing sessions are never killed when a grant changes.
    """

    def __init__(self, get_verified_access: Callable[[], VerifiedAccess | None]):
        self._provider = get_verified_access
        self._pending: set[str] = set()
        self._lock = RLock()

    def allowed(self, *, now: float | None = None) -> int:
        access = self._provider()
        return access.valid_limit(time() if now is None else now) if access else 0

    def reserve(self, active_count: int, *, now: float | None = None) -> str:
        if not isinstance(active_count, int) or active_count < 0:
            raise ValueError("active_count must be a nonnegative integer")
        with self._lock:
            limit = self.allowed(now=now)
            if limit == 0:
                raise AccessDenied("Staff verification or online authorization required")
            if active_count + len(self._pending) >= limit:
                raise AccessDenied(f"Multi-instance limit reached ({limit} authorized)")
            token = uuid4().hex
            self._pending.add(token)
            return token

    def release(self, token: str) -> None:
        with self._lock:
            self._pending.discard(token)

    @property
    def pending_count(self) -> int:
        with self._lock:
            return len(self._pending)
