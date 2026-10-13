# TGC Staff Companion v0.19.7 — nested avatar OSC parameter paths

## Bug fix
Previously, the parameter control form, saved-control database, and UDP sender all
accepted only `[A-Za-z_][A-Za-z0-9_]{0,63}`. They rejected the slash in
`/avatar/parameters/Go/IsFeral` and `Go/IsFeral`, with a misleading error
suggesting underscores.

Now both forms normalize to the stored name `Go/IsFeral`, and the outgoing OSC
address is exactly `/avatar/parameters/Go/IsFeral`.

## Validation
- Accept `Go/IsFeral`, `/avatar/parameters/Go/IsFeral`, existing `IsFeral`,
  and permitted multi-segment names (ASCII letters, digits, underscores and
  forward slashes between nonempty segments).
- Reject `/avatar/parameters//Go`, `Go//IsFeral`, `Go/`, spaces, unrelated
  endpoints (`/chatbox/input`), and relative names longer than 192 chars.
- User typing full OSC prefix never produces a duplicate
  `/avatar/parameters//avatar/parameters/` path.
- Existing saved simple names continue to display and work; new saved controls
  use a canonical relative path and keep account isolation and 16-slot cap.
- Bool emits OSC `,T`/`,F`; int emits `,i` (0-255); float emits `,f`
  (-1..1). No unrequested writes or background control.
- Check the Quest System `Avatar parameters` screen and Android phone
  floating-tools `Avatar parameters` screen both show a full-address example.
- Test on a VRChat avatar with the actual `Go/IsFeral` Bool parameter:
  enable OSC in VRChat's Action Menu; save the control and press TRUE/FALSE.
  The app can confirm local UDP transmission, **not** VRChat execution or
  that the currently worn avatar exposes this input.
- All Quest and Android phone tracking behavior, local OSC chatbox, avatars,
  profile groups and Relay remain unchanged.

v0.19.7 / versionCode 59. Permanent signing identity must pass before publishing.
