"""Mobile Companion parity: per-account pinned friends and group shortcuts.

Only local, atomic preferences, NEVER changes VRChat friends/groups or uploads
user lists. Friend pins match mobile FriendPinStore (12, newest first, orderable),
group pins match GroupToolkitStore (16, appended). No credentials or roles saved.
"""
from __future__ import annotations

import json
from pathlib import Path
import re

from .pilot_enrollment import _atomic_bytes
from .vrchat_personal import USER_ID

GROUP_ID = re.compile(r"grp_[0-9a-fA-F-]{36}\Z")
MAX_FRIEND_PINS = 12
MAX_GROUP_PINS = 16


class SocialShortcuts:
    def __init__(self, directory: Path):
        self.path = Path(directory) / "personal_social_shortcuts.json"

    def _read(self):
        try:
            if self.path.stat().st_size > 65536:
                return {}
            data=json.loads(self.path.read_text(encoding="utf-8"))
            return data.get("accounts",{}) if (
                isinstance(data,dict) and data.get("schema")==1
                and isinstance(data.get("accounts"),dict)
                and len(data["accounts"])<=40) else {}
        except (OSError,ValueError,UnicodeError,TypeError):
            return {}

    @staticmethod
    def _account(account):
        if not isinstance(account,str) or not USER_ID.fullmatch(account):
            raise PermissionError("Sign into your personal VRChat account first")
        return account

    @staticmethod
    def _kind(kind):
        if kind=="friends": return USER_ID,MAX_FRIEND_PINS,64
        if kind=="groups": return GROUP_ID,MAX_GROUP_PINS,90
        raise ValueError("Expected friend or group shortcuts")

    def list(self, account, kind):
        self._account(account)
        pattern,limit,maxname=self._kind(kind)
        data=self._read().get(account,{})
        raw=data.get(kind,[]) if isinstance(data,dict) else []
        found=set()
        rows=[]
        for row in raw[:limit*2] if isinstance(raw,list) else []:
            if not isinstance(row,dict): continue
            uid=row.get("id")
            if not isinstance(uid,str) or not pattern.fullmatch(uid) or uid in found:
                continue
            name=str(row.get("name") or ("Friend" if kind=="friends" else "Group")
                     ).strip()[:maxname]
            found.add(uid)
            rows.append({"id":uid,"name":name or "Unknown"})
            if len(rows)==limit:break
        return rows

    def _save(self,account,kind,rows):
        data=self._read()
        if account not in data and len(data)>=40:
            raise ValueError("Too many personal VRChat shortcut profiles")
        info=data.setdefault(account,{})
        info[kind]=rows
        _atomic_bytes(self.path,json.dumps(
            {"schema":1,"accounts":data},ensure_ascii=False,
            separators=(",",":")).encode("utf-8"))

    def set_pinned(self,account,kind,identifier,name,pin=True):
        account=self._account(account)
        pattern,limit,maxname=self._kind(kind)
        if not isinstance(identifier,str) or not pattern.fullmatch(identifier):
            raise ValueError("Invalid VRChat friend/group ID")
        entries=[r for r in self.list(account,kind) if r["id"]!=identifier]
        if pin:
            if len(entries)>=limit:
                raise ValueError("Maximum "+str(limit)+" pinned "+kind)
            label=str(name or ("Friend" if kind=="friends" else "Group")).strip()
            row={"id":identifier,"name":label[:maxname] or "Unknown"}
            if kind=="friends":
                entries.insert(0,row)
            else:
                entries.append(row)
        self._save(account,kind,entries)
        return entries

    def move_friend(self,account,identifier,direction):
        account=self._account(account)
        if type(direction) is not int or direction not in (-1,1):
            raise ValueError("Move a pin by one row")
        rows=self.list(account,"friends")
        ids=[row["id"] for row in rows]
        if identifier not in ids:
            raise ValueError("Select a pinned friend")
        i=ids.index(identifier)
        j=i+direction
        if 0<=j<len(rows):
            rows[i],rows[j]=rows[j],rows[i]
            self._save(account,"friends",rows)
        return rows
