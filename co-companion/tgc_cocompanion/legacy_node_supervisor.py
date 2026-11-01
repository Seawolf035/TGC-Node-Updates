"""Guarded integration boundary for the existing StaffNodeService.

This is intentionally NOT connected to the UI, production Relay, or automatic
migration. The exact published legacy StaffNodeService source remains archived
unchanged in legacy/tgc_staff_node.py. A trusted local adapter must separately
supply an instance with the original Queue/DPAPI paths *only after* verified,
backed-up migration.

NEVER call start() merely because a Node folder or config file exists.
"""
from __future__ import annotations

from threading import RLock
from typing import Callable, Protocol

from .permissions import AccessDenied, VerifiedAccess


class EmbeddedNode(Protocol):
    verified_session: bool
    verified_data: dict

    def start(self) -> None: ...
    def stop(self) -> None: ...


class UnsafeNodeTakeover(AccessDenied):
    pass


class LegacyStaffNodeSupervisor:
    """Start the preserved embedded Node only when every gate proves readiness.

    This module does NOT implement migration approval, Windows old-Node process
    detection, a server authorization query or cross-process locking. Until
    those trusted dependencies are provided (default False), start() is
    deliberately unavailable. Never let local JSON set any of these gates.
    """

    def __init__(
        self,
        current_staff: Callable[[], VerifiedAccess | None],
        terms_accepted: Callable[[], bool],
        migration_approved: Callable[[], bool],
        old_node_stopped: Callable[[], bool],
        node_factory: Callable[[], EmbeddedNode],
        *,
        clock: Callable[[], float],
    ):
        self._staff = current_staff
        self._terms = terms_accepted
        self._migration = migration_approved
        self._old_stopped = old_node_stopped
        self._factory = node_factory
        self._clock = clock
        self._node: EmbeddedNode | None = None
        self._running = False
        self._lock = RLock()

    def _eligible_staff(self) -> VerifiedAccess:
        try:
            grant = self._staff()
            now = self._clock()
            if not isinstance(grant, VerifiedAccess) or grant.valid_limit(now) == 0:
                raise UnsafeNodeTakeover("Verified staff access required")
            if self._terms() is not True:
                raise UnsafeNodeTakeover("Co-Companion desktop Terms required")
            if self._migration() is not True:
                raise UnsafeNodeTakeover("Safe migration has not been approved")
            if self._old_stopped() is not True:
                raise UnsafeNodeTakeover("Old Node might still be running")
            return grant
        except UnsafeNodeTakeover:
            raise
        except Exception as exc:
            raise UnsafeNodeTakeover("Node authorization or migration status unavailable") from exc

    def start(self) -> bool:
        with self._lock:
            grant = self._eligible_staff()
            if self._running:
                return False
            if self._node is None:
                # The factory may load the old SQLite store and DPAPI key.
                # It is NEVER invoked until all safety checks pass.
                self._node = self._factory()
            node = self._node
            info = getattr(node, "verified_data", None)
            if not getattr(node, "verified_session", False) or not isinstance(info, dict):
                raise UnsafeNodeTakeover("Legacy Node session not Relay verified")
            if str(info.get("client_type") or "").strip().lower() != "staff_launcher":
                raise UnsafeNodeTakeover("Existing Node client type not approved for takeover")
            if str(info.get("verification_method") or "").strip().lower() != "deployment_code":
                raise UnsafeNodeTakeover("Legacy Node deployment proof missing")
            if str(info.get("discord_id") or "") != grant.discord_id:
                raise UnsafeNodeTakeover("Legacy Node belongs to a different staff identity")
            node.start()
            self._running = True
            return True

    def stop(self) -> bool:
        with self._lock:
            if not self._running:
                return False
            # Don't clear the running flag if the Node cannot stop. This is
            # safer than falsely permitting a second collector/uploader.
            self._node.stop()
            self._running = False
            return True

    @property
    def running(self) -> bool:
        with self._lock:
            return self._running

    def check_access(self) -> bool:
        """A periodic future UI tick must call this. Revoke if access expires."""
        with self._lock:
            if not self._running:
                return False
            try:
                self._eligible_staff()
            except UnsafeNodeTakeover:
                self.stop()
                return False
            return True
