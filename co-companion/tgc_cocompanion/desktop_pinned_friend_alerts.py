"""Opt-in pinned-friend online alerts while Co-Companion is open.

Desktop adaptation of Android PinnedFriendAlerts: account-bound, active
verified staff access, silent baseline, deduplicated offline->online events,
and no assumption that a user missing from a snapshot is offline. State is
Windows DPAPI-encrypted. No persistent service, new Node, Relay or uploader.
"""
from __future__ import annotations

import json
from pathlib import Path
import time

from .pilot_enrollment import _atomic_bytes, _encrypt_for_user, _decrypt_for_user
from .vrchat_personal import USER_ID

MIN_INTERVAL_SECONDS=15*60
MAX_FILE=32768


def online_state(row):
    return bool(row.get("isOnline",row.get("online",False)))


class PinnedFriendAlerts:
    def __init__(self,directory):
        self.directory=Path(directory)

    def _path(self,account):
        if not isinstance(account,str) or not USER_ID.fullmatch(account):
            raise PermissionError("Sign in to VRChat first")
        return self.directory/("pinned_friend_alerts_"+account+".dpapi")

    def _load(self,account):
        path=self._path(account)
        try:
            if path.stat().st_size>MAX_FILE:
                raise ValueError("Pinned friend alert preferences are too large")
            obj=json.loads(_decrypt_for_user(path.read_bytes()))
            return obj if isinstance(obj,dict) and obj.get("schema")==1 and obj.get(
                "account")==account else {}
        except FileNotFoundError:
            return {}
        except (OSError,ValueError,UnicodeError) as exc:
            raise ValueError("Pinned alert settings are unreadable; no changes were made") from exc

    def _save(self,account,data):
        payload=json.dumps(data,separators=(",",":"))
        if len(payload)>MAX_FILE-1024:
            raise ValueError("Pinned alert state is too large")
        _atomic_bytes(self._path(account),_encrypt_for_user(payload))

    def enabled(self,account):
        return self._load(account).get("enabled") is True

    def set_enabled(self,account,enabled):
        if type(enabled) is not bool:
            raise ValueError("Alert enable state must be boolean")
        # Enabling ALWAYS starts a new silent baseline; keep separately chosen
        # Windows notification preference when toggling general alerts.
        windows=self.windows_notifications(account)
        data={"schema":1,"account":account,"enabled":enabled,
              "windows":windows,"seeded":False,"last":0,"states":{}}
        self._save(account,data)

    def windows_notifications(self,account):
        return self._load(account).get("windows") is True

    def set_windows_notifications(self,account,enabled):
        if type(enabled) is not bool:
            raise ValueError("Windows notification preference must be boolean")
        data=self._load(account)
        if not data:
            data={"schema":1,"account":account,"enabled":False,
                  "seeded":False,"last":0,"states":{}}
        data["windows"]=enabled
        self._save(account,data)

    def last_check(self,account):
        return int(self._load(account).get("last",0))

    def observe(self,account,pinned,complete,at=None):
        """Caller supplies a *complete* online+offline VRChat friend snapshot."""
        state=self._load(account)
        if state.get("enabled") is not True:
            return ()
        at=int(time.time() if at is None else at)
        old=state.get("states",{})
        if not isinstance(old,dict):
            old={}
        actual={}
        for person in complete:
            if not isinstance(person,dict):
                continue
            uid=person.get("id")
            if isinstance(uid,str) and USER_ID.fullmatch(uid):
                actual[uid]=person
        newer={}
        notices=[]
        seen=set()
        seeded=state.get("seeded") is True
        for row in pinned:
            if not isinstance(row,dict):
                continue
            uid=row.get("id")
            if not isinstance(uid,str) or not USER_ID.fullmatch(uid) or uid in seen:
                continue
            seen.add(uid)
            person=actual.get(uid)
            if person is None:
                # Do not infer a missing/hidden friend is offline; clear its
                # baseline so reappearance cannot cause a false online alert.
                continue
            is_online=online_state(person)
            newer[uid]=is_online
            if seeded and uid in old and old[uid] is False and is_online:
                name=str(person.get("displayName") or row.get("name") or
                         "Pinned friend").strip()[:70]
                notices.append({"id":uid,"name":name})
        state.update({"seeded":True,"last":at,"states":newer})
        # Store before returning alerts to prevent duplicate announcements.
        self._save(account,state)
        return tuple(notices)
