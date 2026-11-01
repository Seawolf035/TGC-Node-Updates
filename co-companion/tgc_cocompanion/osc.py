"""PC VRChat OSC packet creation and safe loopback transport.

No LAN listener, remote control, or unverified device pairing is implemented.
OSC packets have no delivery acknowledgment; successful send != VRChat action.
"""
from __future__ import annotations

import math
import re
import socket
import struct


class OscError(ValueError):
    pass


def _str(value: str) -> bytes:
    if not isinstance(value, str) or "\x00" in value:
        raise OscError("Invalid OSC string")
    encoded = value.encode("utf-8") + b"\x00"
    return encoded + b"\x00" * ((-len(encoded)) % 4)


def packet(address: str, tags: str, payload: bytes = b"") -> bytes:
    if not address.startswith("/") or not tags.startswith(","):
        raise OscError("Invalid OSC address or type tags")
    return _str(address) + _str(tags) + payload


def chatbox(text: str, *, send_immediately: bool = True, sound: bool = False) -> bytes:
    if not isinstance(text, str) or not text.strip() or "\x00" in text:
        raise OscError("Chatbox text is required")
    if len(text.encode("utf-8")) > 144:
        raise OscError("Chatbox text exceeds 144 UTF-8 bytes")
    tags = ",s" + ("T" if send_immediately else "F") + ("T" if sound else "F")
    return packet("/chatbox/input", tags, _str(text))


def eye_height(metres: float) -> bytes:
    try:
        value = float(metres)
    except (TypeError, ValueError) as exc:
        raise OscError("Invalid avatar eye height") from exc
    if not math.isfinite(value) or not 0.1 <= value <= 100.0:
        raise OscError("Avatar eye height must be 0.1–100 metres")
    return packet("/avatar/eyeheight", ",f", struct.pack(">f", value))


def avatar_parameter(name: str, value: bool | int | float) -> bytes:
    if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_]+", name):
        raise OscError("Invalid avatar parameter name")
    address = "/avatar/parameters/" + name
    if isinstance(value, bool):
        return packet(address, ",T" if value else ",F")
    if isinstance(value, int):
        if not -128 <= value <= 127:
            raise OscError("VRChat integer avatar parameter must be -128..127")
        return packet(address, ",i", struct.pack(">i", value))
    if isinstance(value, float) and math.isfinite(value) and -1 <= value <= 1:
        return packet(address, ",f", struct.pack(">f", value))
    raise OscError("Avatar parameter must be bool, int -128..127, or float -1..1")


def voice_input(pressed: bool) -> bytes:
    """Safe push-to-talk command; caller must guarantee eventual release."""
    if type(pressed) is not bool:
        raise OscError("Voice input state must be true or false")
    return packet("/input/Voice", ",T" if pressed else ",F")


def chatbox_typing(typing: bool) -> bytes:
    if type(typing) is not bool:
        raise OscError("Typing status must be true or false")
    return packet("/chatbox/typing", ",T" if typing else ",F")


class LocalOscSender:
    """Explicit PC client port required; always sends to loopback only."""

    def send(self, message: bytes, *, port: int) -> None:
        if not isinstance(port, int) or not 1 <= port <= 65535:
            raise OscError("Select a valid VRChat OSC port first")
        if not isinstance(message, bytes) or not message:
            raise OscError("No OSC command provided")
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.sendto(message, ("127.0.0.1", port))
