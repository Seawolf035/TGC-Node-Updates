"""Windows StaffDex — official TGC roster + complete personal VRChat friends.

Matches Mobile Companion's collection rule: actual VRChat friendship or own
personal account counts. No sighting logs, friend requests, usernames, rank
imitation, or TGC Node enrollment can unlock StaffDex rewards. No progress is
reported from a PARTIAL friend scan.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
from time import time
from urllib.request import Request, urlopen

from .pilot_enrollment import _encrypt_for_user, _decrypt_for_user, _atomic_bytes
from .vrchat_personal import USER_ID

# Same read-only roster endpoint as Mobile Companion StaffDirectoryRepository.
ROSTER_URL = (
    "https://script.google.com/macros/s/"
    "AKfycbx0MRdKsYmz1B82M3KYZ28_fcyTrdavMTsTxNkLm2-ijyXAaR-1hbpa2fhDB_zXZAmrCg/exec?format=json"
)
DISCORD_ID = re.compile(r"[0-9]{15,22}\Z")
ROLES = ("The Royalty", "Owner", "Head Admin", "Admin",
         "Head Moderator", "Moderator", "Trial Moderator")
RANKS = {"the royalty":"The Royalty","royalty":"The Royalty",
         "owner":"Owner","co-owner":"Owner","head admin":"Head Admin",
         "head administrator":"Head Admin","admin":"Admin","administrator":"Admin",
         "head moderator":"Head Moderator","head mod":"Head Moderator",
         "moderator":"Moderator","mod":"Moderator",
         "trial moderator":"Trial Moderator","trial mod":"Trial Moderator"}
GM = {
    "GM1": "usr_7e47ea74-6034-48a1-8e8d-32e6bf8b0060",
    "GM2": "usr_5a0bd6d8-25c6-4a7b-a1a6-9ea85125398f",
    "GM3": "usr_677c17a0-a615-4dfd-907a-86706f06cc98",
    "GM4": "usr_bc1178b2-e5ad-40d5-a632-31acd5165416",
}
# Mobile's hidden awards: do not reveal names/targets to players until earned.
_HIDDEN = {
    "Community Encounter": "usr_0f534ae6-c5c4-4025-9478-d5423da1f296",
    "Community Encounter II": "usr_d2698c92-1c49-4fde-bf3b-ea1809cceed1",
    "Community Encounter III": "usr_42d46072-71c6-4fb9-8391-d1bbd0e543a3",
    "Free VRC+? NICE!": "usr_a4ee31d2-0c71-4e12-a0ab-4c1c6693a315",
}
_WESMAN = "usr_7a5238d4-9017-49dd-a0df-df25800d119c"
_SELF_TITLE = "Your own account"
# Exact Mobile Companion leadership identities and award conditions.
_LEADERS = {
    "Gamewitch · The Mysterious One": "usr_ff419c82-c92b-4494-b821-035d33253ef0",
    "Seawolf · The Founder": "usr_2a833076-03ac-4212-aeff-0708d8eab968",
    "Lily · The Lil shit": "usr_b85631a8-5e23-4b88-a14b-4c8a2ae863c1",
    "Imposter · Sus": "usr_3f229b32-5059-4889-af22-a98203d7a2b3",
    "AJ · The Chairman": "usr_f430e93d-d7f4-4e23-a0e9-e332f2228dff",
    "Asriel · The Spawn of Lily and Seawolf": "usr_740ff8b0-6d49-4b38-b28b-a45f33ea8ef9",
    "Darkflames · ICE Breaker": "usr_cd566dbc-b21d-4a84-9548-4767128f0bec",
}
_HIDDEN_TITLES = {
    "Community Encounter": "Devinity • The Gothic Puppy",
    "Community Encounter II": "Soup • The Pregnant Man",
    "Community Encounter III": "Klashy • The Black Cat",
    "Free VRC+? NICE!": "Kaitlynslayer • Free VRC+? NICE!",
}



@dataclass(frozen=True)
class StaffEntry:
    discord_id: str
    vrchat_id: str
    name: str
    rank: str


@dataclass(frozen=True)
class DexSnapshot:
    discord_id: str
    account_id: str
    members: tuple[StaffEntry, ...]
    friends: frozenset[str]
    captured_at: float
    complete: bool = True

    def collected(self, user_id: str) -> bool:
        return self.complete and (user_id == self.account_id or user_id in self.friends)

    @property
    def total(self) -> int:
        return sum(bool(e.vrchat_id) and e.vrchat_id != self.account_id for e in self.members)

    @property
    def earned(self) -> int:
        return sum(e.vrchat_id != self.account_id and bool(e.vrchat_id)
                   and self.collected(e.vrchat_id) for e in self.members)

    def visible_rewards(self) -> dict[str, bool]:
        """Port Mobile's 5 Gamer Music + 11 leadership + earned hidden awards."""
        if not self.complete:
            return {}
        titles=(
            "GM1 · Original Broadcast","GM2 · Initiated into the Cult",
            "GM3 · Third Channel","GM4 · Fourth Frequency",
        )
        result={title:self.collected(uid) for title,uid in zip(titles,GM.values())}
        result["Gamer Music: Complete Discography"]=all(result.values())
        for title,uid in _LEADERS.items():
            result[title]=self.collected(uid)
        result["Seawolf & Lily · Two Hearts"]=(
            self.collected(_LEADERS["Seawolf · The Founder"]) and
            self.collected(_LEADERS["Lily · The Lil shit"]))
        royals={_LEADERS["Gamewitch · The Mysterious One"]}
        royals.update(e.vrchat_id for e in self.members if
                      e.rank=="The Royalty" and bool(e.vrchat_id))
        owners={
            _LEADERS["Imposter · Sus"],
            _LEADERS["AJ · The Chairman"],
            _LEADERS["Asriel · The Spawn of Lily and Seawolf"],
        }
        owners.update(e.vrchat_id for e in self.members if
                      e.rank=="Owner" and bool(e.vrchat_id))
        result["Royal Flush"]=all(self.collected(uid) for uid in royals)
        result["Owners' Meeting"]=all(self.collected(uid) for uid in owners)
        result["The Entire Upper Deck"]=all(self.collected(uid) for uid in (royals | owners))
        # Hidden awards do not appear before confirmed friendship.
        for key,uid in _HIDDEN.items():
            if uid in self.friends and uid!=self.account_id:
                result[_HIDDEN_TITLES[key]]=True
        # Wesman: dynamically community-only and requiring a FRESH roster.
        if (time()-self.captured_at<=600 and _WESMAN in self.friends
                and _WESMAN!=self.account_id
                and not any(e.vrchat_id==_WESMAN for e in self.members)):
            result["Wesman4real1 • That one dude"]=True
        return result


def _valid_rank(raw) -> str:
    if not isinstance(raw, str):
        return ""
    return RANKS.get(raw.strip().lower(), "")


def _from_url(raw: object) -> str:
    if not isinstance(raw, str):
        return ""
    match = re.search(r"usr_[0-9a-fA-F-]{36}", raw)
    return match.group(0) if match else ""


def parse_roster(response: dict) -> tuple[StaffEntry, ...]:
    if not isinstance(response, dict) or response.get("ok", True) is False:
        raise ValueError("Invalid TGC roster")
    rows = response.get("members", response.get("staff"))
    if not isinstance(rows, list) or len(rows) > 1000:
        raise ValueError("TGC roster has no recognized staff array")
    entries: dict[str, StaffEntry] = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        discord = str(row.get("discordId") or "").strip()
        if not DISCORD_ID.fullmatch(discord):
            continue
        rank = _valid_rank(row.get("role"))
        if row.get("isRoyalty") is True or str(row.get("displayRole") or "").strip().lower()=="the royalty":
            rank = "The Royalty"
        if not rank:
            rank = _valid_rank(row.get("displayRole"))
        if not rank:
            continue
        identifier = str(row.get("vrchatUserId") or "")
        if not USER_ID.fullmatch(identifier):
            identifier = _from_url(row.get("vrchatUrl"))
        name = str(row.get("username") or "").strip()[:80] or ("Staff "+discord)
        entries[discord] = StaffEntry(discord,identifier,name,rank)
    return tuple(sorted(entries.values(),key=lambda e:(ROLES.index(e.rank),e.name.casefold())))


def fetch_roster() -> tuple[StaffEntry, ...]:
    req = Request(ROSTER_URL,headers={"User-Agent":"TGC-CoCompanion/Roster","Accept":"application/json"})
    with urlopen(req,timeout=12) as response:
        data = response.read(1_000_001)
    if len(data)>1_000_000:
        raise ValueError("Roster response too large")
    members = parse_roster(json.loads(data.decode("utf-8")))
    if not members:
        raise ValueError("TGC staff roster is empty")
    return members


class StaffDex:
    def __init__(self, directory: Path, roster_fetch=fetch_roster):
        self.directory = directory
        self._fetch = roster_fetch
        self.path = directory / "staffdex_completed_snapshot.dpapi"

    def roster_directory(self) -> tuple[StaffEntry, ...]:
        """Read authoritative PUBLIC roster without scanning anyone's friendships.

        This is a cosmetic read-only directory, NOT staff authentication.
        Loading it must work when the owner's short-lived Relay lease expires.
        """
        return self._fetch()

    def refresh(self, personal_vrchat, verified_discord: str) -> DexSnapshot:
        if not DISCORD_ID.fullmatch(str(verified_discord)):
            raise PermissionError("Verified TGC staff account required")
        if not personal_vrchat.signed_in or not USER_ID.fullmatch(personal_vrchat.user_id):
            raise PermissionError("Personal VRChat sign-in required")
        account = personal_vrchat.user_id
        members = self._fetch()
        friends = personal_vrchat.friend_snapshot()   # raises if ANY page fails
        if account != personal_vrchat.user_id:
            raise PermissionError("Personal VRChat account switched during scan")
        result = DexSnapshot(verified_discord,account,members,frozenset(friends),time())
        # Owner-preview local collection, NOT an authorization source.\n        # Only completed data is persisted, under DPAPI, scoped to BOTH
        # immutable Discord and personal VRChat identity.
        payload={"discord":verified_discord,"account":account,"captured_at":result.captured_at,
                 "members":[e.__dict__ for e in members],"friend_ids":sorted(friends)}
        _atomic_bytes(self.path,_encrypt_for_user(json.dumps(payload)))
        return result

    def cached(self, verified_discord: str, personal_user: str) -> DexSnapshot | None:
        if not DISCORD_ID.fullmatch(str(verified_discord)) or not USER_ID.fullmatch(str(personal_user)):
            return None
        try:
            raw=json.loads(_decrypt_for_user(self.path.read_bytes()))
            if raw.get("discord")!=verified_discord or raw.get("account")!=personal_user:
                return None
            if not 0<float(raw.get("captured_at") or 0)<=time()+60:
                return None
            entries=tuple(StaffEntry(**item) for item in raw["members"])
            friends=frozenset(str(x) for x in raw["friend_ids"])
            if len(entries)>500 or len(friends)>10000 or any(
                    not USER_ID.fullmatch(x) for x in friends):
                return None
            return DexSnapshot(verified_discord,personal_user,entries,friends,float(raw["captured_at"]))
        except (OSError,ValueError,TypeError,KeyError,Exception):
            return None
