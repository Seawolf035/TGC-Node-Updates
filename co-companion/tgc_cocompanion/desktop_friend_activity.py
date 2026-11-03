"""Manual account-scoped local VRChat friend change history.

Based on Mobile Companion FriendLogStore: first complete snapshot seeds baseline,
subsequent complete snapshots generate changes; 60-second minimum, 5,000 events.
Windows DPAPI encryption (same logged-in Windows user), no background polling,
Relay calls, cookie storage, or sightings uploader.
"""
from __future__ import annotations

import json
from pathlib import Path
import time

from .pilot_enrollment import _atomic_bytes, _encrypt_for_user, _decrypt_for_user
from .vrchat_personal import USER_ID

MAX_EVENTS=5000
MAX_FILE_BYTES=8_000_000
MIN_POLL_SECONDS=60
FIELDS=("location","status","statusDescription","name")


def normalize_friends(complete):
    result={}
    for friend in complete:
        if not isinstance(friend,dict):continue
        uid=friend.get("id")
        if not isinstance(uid,str) or not USER_ID.fullmatch(uid):continue
        result[uid]={
            "name":str(friend.get("displayName") or uid)[:96],
            "status":str(friend.get("status") or "unknown")[:80],
            "statusDescription":str(friend.get("statusDescription") or "")[:160],
            "location":str(friend.get("location") or "unknown")[:160],
            "online":bool(friend.get("isOnline",friend.get("online",False))),
        }
    return result


def detect_friend_changes(prior,current,at):
    changes=[]
    def event(uid,user,kind,previous=""):
        return {"time":int(at),"userId":uid,"name":user["name"],
                "type":kind,"location":user["location"],"status":user["status"],
                "statusDescription":user["statusDescription"],"previous":str(previous)[:160]}
    for uid,user in current.items():
        old=prior.get(uid)
        if old is None:
            changes.append(event(uid,user,"friend_added"));continue
        if old.get("online")!=user["online"]:
            changes.append(event(uid,user,"online" if user["online"] else "offline"))
        for field in FIELDS:
            previous=old.get(field,"")
            if previous!=user[field]:
                changes.append(event(uid,user,field,previous))
    for uid,user in prior.items():
        if uid not in current:
            changes.append(event(uid,user,"friend_removed"))
    return changes


class FriendActivityStore:
    def __init__(self,directory:Path):
        self.directory=Path(directory)

    def _path(self,user_id):
        if not isinstance(user_id,str) or not USER_ID.fullmatch(user_id):
            raise PermissionError("Sign in to personal VRChat to view friend history")
        return self.directory/("friend_activity_"+user_id+".dpapi")

    def _read(self,user_id):
        path=self._path(user_id)
        try:
            if path.stat().st_size > MAX_FILE_BYTES:
                raise ValueError("Local friend history is too large")
            raw=json.loads(_decrypt_for_user(path.read_bytes()))
            if not isinstance(raw,dict) or raw.get("schema")!=1 or raw.get("account")!=user_id:
                return {}
            return raw
        except FileNotFoundError:
            return {}
        except (OSError,ValueError,UnicodeError):
            raise ValueError("Saved friend history is unreadable. Existing data was not overwritten.")

    def events(self,user_id):
        events=self._read(user_id).get("events",[])
        if not isinstance(events,list):return ()
        return tuple(e for e in events[-MAX_EVENTS:] if isinstance(e,dict))

    def observe(self,user_id,complete,now=None):
        """Caller MUST provide complete online+offline friend list, never partial."""
        current=normalize_friends(complete)
        at=int(time.time() if now is None else now)
        data=self._read(user_id)
        if at-int(data.get("lastPoll",0))<MIN_POLL_SECONDS:
            raise ValueError("Capture friend changes at most once per minute")
        old=data.get("snapshot",{})
        if not isinstance(old,dict):old={}
        events=data.get("events",[])
        if not isinstance(events,list):events=[]
        changes=detect_friend_changes(old,current,at) if data.get("initialized") is True else []
        updated={
            "schema":1,"account":user_id,"initialized":True,
            "lastPoll":at,"snapshot":current,
            "events":(events+changes)[-MAX_EVENTS:],
        }
        # Validate full JSON shape and size BEFORE overwriting.
        payload=json.dumps(updated,ensure_ascii=False,separators=(",",":"))
        if len(payload.encode("utf-8"))>MAX_FILE_BYTES-1024:
            raise ValueError("Friend snapshot is too large; nothing was changed")
        _atomic_bytes(self._path(user_id),_encrypt_for_user(payload))
        return len(changes)
