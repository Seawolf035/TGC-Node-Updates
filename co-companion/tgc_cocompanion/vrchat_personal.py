"""Personal VRChat API account, isolated from the TGC Invite Relay/Node identity.

Ported from Mobile Companion VRChatApi.java's authenticated endpoints. A
personal VRChat password is used only for one HTTPS login; never stored.
The personal auth cookies are Windows-user DPAPI encrypted, never sent to the
TGC Relay, recorded in logs, or accepted from editable app configuration.
"""
from __future__ import annotations

import base64
import http.client
import json
from pathlib import Path
import re
import ssl
from threading import RLock
from typing import Callable
from urllib.parse import quote

from .pilot_enrollment import _atomic_bytes, _encrypt_for_user, _decrypt_for_user, get_pilot_dir

USER_ID = re.compile(r"usr_[0-9a-fA-F-]{36}\Z")
TWO_FA_CODE = re.compile(r"[A-Za-z0-9-]{4,32}\Z")
METHODS = {"totp", "emailotp", "otp"}
_ALLOWED_COOKIES = ("auth", "twoFactorAuth")
_AGENT = "TGC-CoCompanion/0.2 masonswan@usa.com"
MAX_RESPONSE = 1_048_576


class VRChatApiError(RuntimeError):
    pass


def _valid_route(route: str) -> str:
    if not isinstance(route, str) or not route.startswith("/") or ".." in route or "://" in route or "\\" in route:
        raise VRChatApiError("Invalid VRChat API route")
    return route


def _cookies_from_headers(headers: list[tuple[str, str]]) -> dict[str, str]:
    found = {}
    for name, raw in headers:
        if name.lower() != "set-cookie":
            continue
        first = raw.split(";", 1)[0]
        key, equals, value = first.partition("=")
        if equals and key in _ALLOWED_COOKIES and len(value) < 8192:
            found[key] = value
    return found


def https_transport(method: str, route: str, cookie: str, authorization: str = "",
                    body: dict | None = None) -> tuple[object, dict]:
    """Allowlisted HTTPS only; redirects denied so auth never leaks hosts."""
    route = _valid_route(route)
    conn = http.client.HTTPSConnection("api.vrchat.cloud", timeout=17, context=ssl.create_default_context())
    headers = {"User-Agent": _AGENT, "Accept": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    if authorization:
        headers["Authorization"] = authorization
    payload = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        payload = json.dumps(body, separators=(",", ":")).encode("utf-8")
        if len(payload) > 16_384:
            raise VRChatApiError("VRChat payload too large")
    try:
        conn.request(method, "/api/1" + route, body=payload, headers=headers)
        response = conn.getresponse()
        data = response.read(MAX_RESPONSE + 1)
        if len(data) > MAX_RESPONSE:
            raise VRChatApiError("VRChat response too large")
        status = response.status
        returned = _cookies_from_headers(response.getheaders())
    except (OSError, TimeoutError, http.client.HTTPException) as exc:
        raise VRChatApiError("VRChat API connection failed") from exc
    finally:
        conn.close()
    if status == 429:
        raise VRChatApiError("VRChat rate limit reached — retry later")
    if status == 401:
        raise VRChatApiError("VRChat login expired or unauthorized")
    if not 200 <= status < 300:
        raise VRChatApiError(f"VRChat API HTTP {status}")
    try:
        parsed = json.loads(data.decode("utf-8")) if data else {}
    except (UnicodeError, ValueError) as exc:
        raise VRChatApiError("VRChat returned invalid JSON") from exc
    return parsed, returned


def _cookie_header(tokens: dict) -> str:
    parts = []
    for key in _ALLOWED_COOKIES:
        token = tokens.get(key, "")
        if token:
            if not isinstance(token, str) or any(x in token for x in (";", "\r", "\n")) or len(token) > 8192:
                raise VRChatApiError("Invalid VRChat session token")
            parts.append(key + "=" + token)
    return "; ".join(parts)


class PersonalVRChat:
    """Staff personal VRChat session — NOT Node/Relay authorization."""

    def __init__(self, directory: Path | None = None,
                 transport: Callable = https_transport):
        self.directory = directory if directory is not None else get_pilot_dir()
        self._transport = transport
        self._store = self.directory / "personal_vrchat_session.dpapi"
        self._lock = RLock()
        self._pending: dict | None = None
        self._session: dict | None = None
        self._load()

    def _load(self):
        try:
            raw = self._store.read_bytes()
            data = json.loads(_decrypt_for_user(raw))
            if (isinstance(data, dict) and USER_ID.fullmatch(str(data.get("userId") or ""))
                    and data.get("auth") and not data.get("needs2fa")):
                _cookie_header(data)
                self._session = data
        except Exception:  # Invalid DPAPI/session data must never unlock the account
            self._session = None

    @property
    def signed_in(self) -> bool:
        return bool(self._session and self._session.get("auth"))

    @property
    def user_id(self) -> str:
        return str((self._session or {}).get("userId") or "")

    @property
    def display_name(self) -> str:
        return str((self._session or {}).get("displayName") or "")

    def _save(self):
        if self._session is None:
            return
        _atomic_bytes(self._store, _encrypt_for_user(json.dumps(self._session)))

    def login(self, username: str, password: str) -> list[str]:
        """Returns pending 2FA methods or [] on complete login."""
        if not isinstance(username, str) or not isinstance(password, str) or not username.strip() or not password:
            raise VRChatApiError("VRChat email/username and password required")
        if self.signed_in:
            raise VRChatApiError("Sign out before switching personal VRChat accounts")
        self._pending = None
        creds = base64.b64encode((username + ":" + password).encode("utf-8")).decode("ascii")
        result, cookies = self._transport("GET", "/auth/user", "", "Basic " + creds)
        if not isinstance(result, dict) or not cookies.get("auth"):
            raise VRChatApiError("VRChat did not issue a login session")
        methods = result.get("requiresTwoFactorAuth")
        if isinstance(methods, list) and methods:
            options = [str(m) for m in methods if m in METHODS]
            if not options:
                raise VRChatApiError("Unsupported VRChat two-factor mode")
            self._pending = {"auth": cookies["auth"]}
            return options
        self._finish_login(result, cookies)
        return []

    def verify_2fa(self, code: str, mode: str = "totp") -> str:
        if mode not in METHODS or not isinstance(code, str) or not TWO_FA_CODE.fullmatch(code.strip()):
            raise VRChatApiError("Invalid VRChat 2FA mode/code")
        if not self._pending or not self._pending.get("auth"):
            raise VRChatApiError("Sign in with VRChat credentials first")
        pending = self._pending
        result, updated = self._transport("POST", "/auth/twofactorauth/" + mode + "/verify",
                                           _cookie_header(pending), "", {"code": code.strip()})
        if not isinstance(result, dict) or result.get("verified") is not True:
            raise VRChatApiError("VRChat did not verify the code")
        pending.update(updated)
        me, updated = self._transport("GET", "/auth/user", _cookie_header(pending))
        pending.update(updated)
        self._finish_login(me, pending)
        self._pending = None
        return self.display_name

    def _finish_login(self, profile: object, tokens: dict):
        if not isinstance(profile, dict) or not USER_ID.fullmatch(str(profile.get("id") or "")):
            raise VRChatApiError("VRChat did not return a valid personal user")
        if not tokens.get("auth"):
            raise VRChatApiError("VRChat auth cookie missing")
        data = {k: tokens[k] for k in _ALLOWED_COOKIES if tokens.get(k)}
        data.update({"userId": profile["id"], "displayName": str(profile.get("displayName") or "")})
        with self._lock:
            self._session = data
            self._save()

    def get(self, route: str):
        with self._lock:
            if not self.signed_in:
                raise VRChatApiError("Sign in to your personal VRChat account first")
            current = self._session.copy()
        answer, headers = self._transport("GET", _valid_route(route), _cookie_header(current))
        if headers:
            with self._lock:
                if self._session is not None and self._session.get("auth") == current.get("auth"):
                    self._session.update(headers)
                    self._save()
        return answer

    def current_user(self) -> dict:
        result = self.get("/auth/user")
        if not isinstance(result, dict) or result.get("id") != self.user_id:
            raise VRChatApiError("Personal VRChat account changed; sign in again")
        return result

    def browse(self, category: str, *, offset: int = 0, query: str = "") -> list[dict]:
        if type(offset) is not int or offset < 0 or offset > 9960 or offset % 60:
            raise VRChatApiError("Invalid VRChat page offset")
        from urllib.parse import urlencode
        routes = {
            "online": "/auth/user/friends?n=60&offline=false&offset=" + str(offset),
            "offline": "/auth/user/friends?n=60&offline=true&offset=" + str(offset),
            "worlds": "/worlds/favorites?n=60&offset=" + str(offset),
            "recent": "/worlds/recent?n=60&offset=" + str(offset),
            "inbox": "/notifications?n=60&offset=" + str(offset),
        }
        if category == "groups":
            if offset:
                raise VRChatApiError("Groups do not have a paginated view")
            routes[category] = "/users/" + self.user_id + "/groups"
        if category in ("search_users", "search_worlds"):
            if not 2 <= len(query.strip()) <= 80:
                raise VRChatApiError("Search text must be between 2 and 80 characters")
            endpoint = "/users" if category == "search_users" else "/worlds"
            routes[category] = endpoint + "?" + urlencode({"search":query.strip(),"n":60,"offset":offset})
        route = routes.get(category)
        if route is None:
            raise VRChatApiError("Unsupported personal VRChat list")
        result = self.get(route)
        if not isinstance(result, list) or len(result) > 100:
            raise VRChatApiError("Unexpected VRChat list response")
        return [row for row in result if isinstance(row, dict)]

    def friend_snapshot(self) -> set[str]:
        """Never save partial friend snapshots or report false achievement unlocks."""
        result = set()
        for category in ("online", "offline"):
            finished = False
            for offset in range(0, 9961, 60):
                page = self.browse(category, offset=offset)
                for user in page:
                    uid = user.get("id")
                    if isinstance(uid, str) and USER_ID.fullmatch(uid):
                        result.add(uid)
                if len(page) < 60:
                    finished = True
                    break
            if not finished:
                raise VRChatApiError("VRChat friend list exceeded safe page limit")
        return result

    def logout(self):
        with self._lock:
            self._session = None
            self._pending = None
            try:
                self._store.unlink(missing_ok=True)
            except OSError:
                raise VRChatApiError("Could not clear saved personal VRChat session")
