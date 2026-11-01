"""Owner-only Windows pilot enrollment against the ONE existing TGC Relay.

Never starts a watcher, uploads sightings, creates a second Relay, changes an
existing Node key, or shares a central owner key. A separate one-use staff_launcher
deployment is deliberately enrolled into a separate user-profile directory.
NOT a replacement for production Staff Launcher or Node enrollment.

On Windows, Node keys use user-bound CryptProtectData (DPAPI via pywin32).
No plaintext node keys, central keys or credentials are written to JSON.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import tempfile
from typing import Any
from urllib import parse, request
from uuid import uuid4


class PilotEnrollmentError(RuntimeError):
    pass


DEPLOYMENT_CODE = re.compile(r"TGC-DEPLOY(?:-[A-F0-9]{4}){5}")
RELAY_URL = re.compile(r"https://script\.google\.com/macros/s/[A-Za-z0-9_-]+/exec")
STAFF_ID = re.compile(r"\d{15,22}")


def get_pilot_dir() -> Path:
    if os.name != "nt":
        raise PilotEnrollmentError("Co-Companion owner pilot requires Windows")
    local = os.environ.get("LOCALAPPDATA")
    if not local:
        raise PilotEnrollmentError("Windows LOCALAPPDATA is unavailable")
    return Path(local) / "TGC" / "CoCompanionOwnerPilot"


def parse_deployment_link(value: str) -> tuple[str, str]:
    """Only exact trusted Apps Script /exec endpoint with one-use deploy code."""
    try:
        parts = parse.urlsplit(str(value).strip())
        relay_url = parse.urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))
        code = parse.parse_qs(parts.query).get("deploy", [""])[0].strip().upper()
    except Exception as exc:
        raise PilotEnrollmentError("Invalid TGC deployment link") from exc
    if not RELAY_URL.fullmatch(relay_url) or not DEPLOYMENT_CODE.fullmatch(code):
        raise PilotEnrollmentError("Paste the full official Staff Launcher deployment link")
    return relay_url, code


def _encrypt_for_user(secret: str) -> bytes:
    if os.name != "nt":
        raise PilotEnrollmentError("Windows DPAPI unavailable")
    try:
        import win32crypt
        return win32crypt.CryptProtectData(
            secret.encode("utf-8"), "TGC Co-Companion owner pilot", None,
            None, None, 0
        )
    except Exception as exc:
        raise PilotEnrollmentError("Could not encrypt Node key using Windows DPAPI") from exc


def _decrypt_for_user(ciphertext: bytes) -> str:
    if os.name != "nt":
        raise PilotEnrollmentError("Windows DPAPI unavailable")
    try:
        import win32crypt
        return win32crypt.CryptUnprotectData(ciphertext, None, None, None, 0)[1].decode("utf-8")
    except Exception as exc:
        raise PilotEnrollmentError("Could not decrypt Node key for this Windows user") from exc


def _atomic_bytes(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    import tempfile
    fd, name = tempfile.mkstemp(prefix=".tgc-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def _post(relay_url: str, payload: dict[str, Any]) -> dict[str, Any]:
    if not RELAY_URL.fullmatch(str(relay_url)):
        raise PilotEnrollmentError("Invalid TGC Relay URL")
    body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    req = request.Request(
        relay_url, data=body, method="POST",
        headers={"Content-Type": "application/json",
                 "User-Agent": "TGC-CoCompanion-OwnerPilot/0.1"}
    )
    try:
        with request.urlopen(req, timeout=20) as reply:
            result = json.loads(reply.read(131072).decode("utf-8"))
    except Exception as exc:
        raise PilotEnrollmentError("Could not contact the existing TGC Relay") from exc
    if not isinstance(result, dict) or result.get("ok") is not True:
        raise PilotEnrollmentError("Relay rejected verification or deployment")
    return result


class PilotEnrollment:
    """Enrollment is separate from installed Staff Launcher and its queue."""

    def __init__(self, directory: Path | None = None, post=_post):
        self.directory = directory if directory is not None else get_pilot_dir()
        self._post_impl = post
        self.config_path = self.directory / "enrollment.json"
        self.key_path = self.directory / "pilot_node_key.dpapi"
        self._config: dict[str, Any] = {}
        self._node_key = ""
        self._load_existing()

    @property
    def discord_id(self) -> str:
        return str(self._config.get("discord_id") or "")

    @property
    def node_id(self) -> str:
        return str(self._config.get("node_id") or "")

    @property
    def enrolled(self) -> bool:
        return (bool(self._node_key) and bool(self.node_id)
                and bool(STAFF_ID.fullmatch(self.discord_id))
                and bool(RELAY_URL.fullmatch(str(self._config.get("relay_url") or ""))))

    def _load_existing(self) -> None:
        if self.config_path.exists() and self.key_path.exists():
            try:
                candidate = json.loads(self.config_path.read_text(encoding="utf-8"))
                if isinstance(candidate, dict):
                    self._config = candidate
                    self._node_key = _decrypt_for_user(self.key_path.read_bytes())
            except (OSError, ValueError, PilotEnrollmentError):
                self._config, self._node_key = {}, ""

    def redeem(self, full_link: str) -> str:
        if self.enrolled:
            raise PilotEnrollmentError("This test installation is already enrolled")
        url, code = parse_deployment_link(full_link)
        install_id = uuid4().hex
        answer = self._post_impl(url, {
            "action": "redeem_deployment", "code": code,
            "client_type": "staff_launcher",
            "install_id": install_id, "version": "0.1.0-pilot",
        })
        node_id = str(answer.get("node_id") or "").strip()
        node_key = str(answer.get("node_key") or "")
        discord_id = str(answer.get("discord_id") or "").strip()
        if (answer.get("verified") is not True
                or answer.get("client_type") != "staff_launcher"
                or not node_id or len(node_key) < 16
                or not STAFF_ID.fullmatch(discord_id)):
            raise PilotEnrollmentError("Relay returned an invalid Staff Launcher enrollment")
        # Save only user-protected DPAPI key + nonsecret metadata.
        encrypted = _encrypt_for_user(node_key)
        config = {"relay_url": url, "node_id": node_id,
                  "discord_id": discord_id, "install_id": install_id}
        _atomic_bytes(self.key_path, encrypted)
        _atomic_bytes(self.config_path,
                      json.dumps(config, indent=2).encode("utf-8"))
        self._node_key = node_key
        self._config = config
        return discord_id

    def _auth_payload(self, action: str) -> dict[str, Any]:
        if not self.enrolled:
            raise PilotEnrollmentError("Owner pilot enrollment required")
        return {
            "action": action, "node_id": self.node_id,
            "node_key": self._node_key, "client_type": "staff_launcher",
            "version": "0.1.0-pilot",
        }

    def _post(self, payload: dict[str, Any], timeout=15) -> dict[str, Any]:
        # The only trusted endpoint is that of this exact enrolled Node.
        return self._post_impl(str(self._config["relay_url"]), payload)

    def verify_node(self) -> dict[str, Any]:
        value = self._post(self._auth_payload("node_status"))
        if (value.get("verified") is not True
                or value.get("client_type") != "staff_launcher"
                or value.get("verification_method") != "deployment_code"
                or value.get("discord_id") != self.discord_id
                or value.get("node_id") != self.node_id):
            raise PilotEnrollmentError("Original Staff Launcher deployment is no longer verified")
        return value

    def access_rpc(self) -> dict[str, Any]:
        # Lease validation is handled by tgc_cocompanion.staff_lease;
        # this operation never submits sightings.
        return self._post(self._auth_payload("cocompanion_access"))

    def clear_local(self) -> None:
        """Does not revoke the Node at the Relay or delete any old Staff Node."""
        self._config, self._node_key = {}, ""
        for p in (self.key_path, self.config_path):
            if p.exists():
                p.unlink()
