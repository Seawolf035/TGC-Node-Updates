"""TGC Relay Co-Companion staff lease adapter (feature-branch contract).

The corresponding *unpublished* Relay feature branch implements
`cocompanion_access`. This class never creates credentials, enrolls a Node,
sends requests by itself, or accepts client-provided instance allowances.
The caller must supply an authenticated *existing* StaffNodeService RPC,
using its own DPAPI-protected node_id/node_key and HTTPS relay endpoint.

Until that backend is deployed and the Windows Node can migrate safely, the
real GUI does not create this adapter. Offline tests use synthetic responses.
"""
from __future__ import annotations

from datetime import datetime, timezone
import math
import re
from threading import RLock
from time import time
from typing import Callable, Mapping, Any

from .permissions import VerifiedAccess


class StaffLeaseRejected(PermissionError):
    pass


class RelayStaffLease:
    """Hold short-lived, server-derived owner grant; fail closed on any error.

    `request_lease` is an authenticated HTTPS request to the owner-controlled
    Relay. It must receive a server-created JSON reply. Never connect it to
    local settings JSON or untrusted device messages.

    A successful lease is only an authorization source for the Windows
    Co-Companion. It does NOT approve Node migration or prove Terms consent.
    """

    def __init__(
        self,
        request_lease: Callable[[], Mapping[str, Any]],
        *,
        enrolled_discord_id: str,
        enrolled_node_id: str,
        clock: Callable[[], float] = time,
        max_server_lease_seconds: int = 180,
    ):
        if not isinstance(enrolled_discord_id, str) or not re.fullmatch(r"\d{15,22}", enrolled_discord_id):
            raise ValueError("Expected enrolled staff Discord identity")
        if not isinstance(enrolled_node_id, str) or not enrolled_node_id.strip():
            raise ValueError("Expected previously enrolled Node identity")
        if not 1 <= max_server_lease_seconds <= 180:
            raise ValueError("Invalid server lease bound")
        self._request = request_lease
        self._staff_id = enrolled_discord_id
        self._node_id = enrolled_node_id
        self._clock = clock
        self._bound = max_server_lease_seconds
        self._access: VerifiedAccess | None = None
        self._lock = RLock()

    def current(self) -> VerifiedAccess | None:
        with self._lock:
            if self._access is not None and self._access.valid_limit(self._clock()) == 0:
                self._access = None
            return self._access

    def invalidate(self) -> None:
        with self._lock:
            self._access = None

    def refresh(self) -> VerifiedAccess:
        """Explicitly request fresh authenticated staff lease.

        A failed refresh drops the previous grant immediately, including an
        owner-approved extra-slot grant, so a revoked permission doesn't
        survive a failed status check.
        """
        with self._lock:
            self._access = None
            try:
                response = self._request()
                access = self._validate(response, now=self._clock())
            except Exception as exc:
                raise StaffLeaseRejected("Co-Companion staff verification unavailable") from exc
            self._access = access
            return access

    def _validate(self, response: Mapping[str, Any], *, now: float) -> VerifiedAccess:
        if not isinstance(response, Mapping):
            raise StaffLeaseRejected("Malformed server lease")
        if response.get("ok") is not True or response.get("verified") is not True:
            raise StaffLeaseRejected("Staff access not verified")
        if response.get("client_type") != "staff_launcher" or response.get("verification_method") != "deployment_code":
            raise StaffLeaseRejected("Not an enrolled Staff Launcher Node")
        if response.get("discord_id") != self._staff_id or response.get("node_id") != self._node_id:
            raise StaffLeaseRejected("Relay responded for the wrong device or staff account")
        quota = response.get("max_instances")
        if type(quota) is not int or not 1 <= quota <= 4:
            raise StaffLeaseRejected("Owner quota missing or invalid")
        raw_expiry = response.get("expires_at")
        if not isinstance(raw_expiry, str):
            raise StaffLeaseRejected("Server lease expiration missing")
        try:
            expires = datetime.fromisoformat(raw_expiry.replace("Z", "+00:00"))
        except ValueError as exc:
            raise StaffLeaseRejected("Invalid server lease expiration") from exc
        if expires.tzinfo is None:
            raise StaffLeaseRejected("Server lease must be UTC-aware")
        expiry_ts = expires.astimezone(timezone.utc).timestamp()
        if not math.isfinite(expiry_ts) or not now < expiry_ts <= now + self._bound:
            raise StaffLeaseRejected("Server lease expired or longer than allowed")
        return VerifiedAccess(self._staff_id, True, quota, expiry_ts)


def legacy_staff_node_request(node_service: Any) -> Callable[[], Mapping[str, Any]]:
    """Construct read-only lease RPC using the already verified legacy client.

    Does not call redeem_deployment(), verify(), start() or open any files;
    the caller is responsible for lifecycle/migration guards. Authentication
    comes from the existing legacy Node's `_auth_payload` and `_post`.
    """
    def request() -> Mapping[str, Any]:
        if getattr(node_service, "verified_session", False) is not True:
            raise StaffLeaseRejected("Node is not verified")
        details = getattr(node_service, "verified_data", {})
        if not isinstance(details, dict) or details.get("verified") is not True:
            raise StaffLeaseRejected("Missing existing verified Relay Node status")
        if str(details.get("client_type") or "").lower() != "staff_launcher":
            raise StaffLeaseRejected("Wrong legacy Node client type")
        payload = node_service._auth_payload("cocompanion_access")
        if payload.get("action") != "cocompanion_access":
            raise StaffLeaseRejected("Invalid Relay request")
        return node_service._post(payload, timeout=15)
    return request
