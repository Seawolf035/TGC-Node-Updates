"""Read-only VRChat favorite-friend index, ported from Mobile VRChatFavoriteIndex.

Full group refresh or no commit. Cached for 15 minutes, Windows-user DPAPI
encrypted and per signed-in personal VRChat account. Never changes favorite
groups, friendships, Relay or TGC Nodes.
"""
from __future__ import annotations

import json
from pathlib import Path
import re
import time

from .pilot_enrollment import _atomic_bytes, _encrypt_for_user, _decrypt_for_user
from .vrchat_personal import USER_ID, VRChatApiError

GROUP_NAME = re.compile(r"[A-Za-z0-9_-]{1,64}\Z")
TTL_SECONDS = 15 * 60
MAX_GROUPS = 20
MAX_FILE = 65536


def _as_groups(value):
    if isinstance(value,dict):
        value=value.get("favoriteGroups")
    if not isinstance(value,list):
        raise VRChatApiError("VRChat did not return a valid favorite-friend group list")
    if len(value)>MAX_GROUPS:
        raise VRChatApiError("Too many favorite-friend groups for a complete refresh")
    result=[]
    for row in value:
        if not isinstance(row,dict):
            raise VRChatApiError("Invalid friend favorites group entry")
        name=row.get("name")
        if not isinstance(name,str) or not GROUP_NAME.fullmatch(name):
            raise VRChatApiError("Unknown favorite-friend group name")
        result.append(name)
    return result


def _friend_ids(payload):
    if not isinstance(payload,dict) or not isinstance(payload.get("favorites"),list):
        raise VRChatApiError("Incomplete VRChat favorite-friend group response")
    found=set()
    for entry in payload["favorites"]:
        if not isinstance(entry,dict):
            continue
        ident=entry.get("favoriteId") or entry.get("userId")
        nested=entry.get("user") or entry.get("friend")
        if isinstance(nested,dict) and USER_ID.fullmatch(str(nested.get("id",""))):
            ident=nested["id"]
        if isinstance(ident,str) and USER_ID.fullmatch(ident):
            found.add(ident)
    return found


def fetch_favorite_friend_ids(account):
    try:
        groups=_as_groups(account.get("/favorites/groups/friend"))
    except VRChatApiError as exc:
        if "rate limit" in str(exc).lower() or "429" in str(exc):
            raise
        # Same fallback as Android; do not retry failed group contents.
        groups=_as_groups(account.get("/favorite/groups?type=friend&n=60&offset=0"))
    result=set()
    for name in groups:
        result.update(_friend_ids(account.get("/favorites/groups/friend/"+name)))
    return frozenset(result)


class FavoriteFriendIndex:
    def __init__(self,directory):
        self.directory=Path(directory)

    def _path(self,account_id):
        if not isinstance(account_id,str) or not USER_ID.fullmatch(account_id):
            raise PermissionError("Sign in to a personal VRChat account first")
        return self.directory/("friend_favorites_"+account_id+".dpapi")

    def _read(self,account_id):
        path=self._path(account_id)
        try:
            if path.stat().st_size>MAX_FILE:
                raise ValueError("Favorite-friend cache is too large")
            payload=json.loads(_decrypt_for_user(path.read_bytes()))
            if (isinstance(payload,dict) and payload.get("schema")==1
                    and payload.get("account")==account_id):
                return payload
        except FileNotFoundError:
            return {}
        except (OSError,ValueError,UnicodeError):
            # Corrupt data is not accepted as favorites or overwritten until
            # a complete server fetch is available.
            return {}
        return {}

    def saved(self,account_id):
        data=self._read(account_id)
        raw=data.get("ids",[])
        return frozenset(x for x in raw if isinstance(x,str) and USER_ID.fullmatch(x)
                         ) if isinstance(raw,list) else frozenset()

    def fresh(self,account_id,at=None):
        data=self._read(account_id)
        timestamp=data.get("at")
        now=time.time() if at is None else at
        return (isinstance(timestamp,(int,float)) and not isinstance(timestamp,bool)
                and 0<=now-timestamp<TTL_SECONDS)

    def refresh(self,account,force=False,at=None):
        account_id=account.user_id
        if not force and self.fresh(account_id,at):
            return self.saved(account_id),False
        ids=fetch_favorite_friend_ids(account)  # fetch ALL before any write
        if not account.signed_in or account.user_id!=account_id:
            raise PermissionError("VRChat account changed during favorite refresh")
        timestamp=int(time.time() if at is None else at)
        payload=json.dumps({"schema":1,"account":account_id,
                            "at":timestamp,"ids":sorted(ids)},separators=(",",":"))
        _atomic_bytes(self._path(account_id),_encrypt_for_user(payload))
        return ids,True
