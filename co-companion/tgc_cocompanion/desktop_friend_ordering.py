"""Faithful nonprivileged ordering from Android FriendOrdering.java.

VRChat Favorites -> shared instances -> solo known location -> hidden online
-> offline. Pinned friends are displayed separately without changing VRChat.
"""
from __future__ import annotations
from collections import Counter

FAVORITES="★ VRChat Favorites"
UNKNOWN="Private / hidden location"
OFFLINE="Offline"

def user_id(person):
    return str(person.get("id") or person.get("userId") or "")

def display_name(person):
    return str(person.get("displayName") or person.get("name") or user_id(person))

def online(person):
    # Android prioritizes isOnline, falling back to online.
    return bool(person.get("isOnline",False)) if "isOnline" in person else bool(person.get("online",False))

def known(location):
    return isinstance(location,str) and location.startswith("wrld_") and ":" in location

def section(person, favorites=frozenset()):
    if user_id(person) in favorites:return FAVORITES
    if not online(person):return OFFLINE
    location=person.get("location","")
    return location if known(location) else UNKNOWN

def group_friends(friends, favorites=frozenset()):
    """Return named sections and people, in the same order as Mobile."""
    items=[p for p in friends if isinstance(p,dict)]
    counts=Counter(section(p,favorites) for p in items)
    def group_sort(key):
        if key==FAVORITES: return (0,0,key.casefold())
        if key==OFFLINE: return (4,0,key.casefold())
        if key==UNKNOWN or not known(key): return (3,0,key.casefold())
        if counts[key]>=2: return (1,-counts[key],key.casefold())
        return (2,0,key.casefold())
    groups=[]
    for key in sorted(counts,key=group_sort):
        members=sorted((x for x in items if section(x,favorites)==key),
                       key=lambda x:display_name(x).casefold())
        if key in (FAVORITES,UNKNOWN,OFFLINE):label=key
        elif counts[key]>=2:label=f"Shared instance · {counts[key]} friends"
        else:label="Single known location"
        groups.append((key,label,members))
    return groups
