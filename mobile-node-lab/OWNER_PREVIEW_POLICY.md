# Standing requirement: owner must be able to preview exclusive Companion features

**Effective with Android Companion v0.21.20 (code 94).** The verified app owner needs to see and test account-exclusive UI features even when they are designed for other approved user/delegate accounts. This is an ongoing development/release requirement—not a one-off for Devinity's palette.

## Technical implementation

For **every future account-exclusive theme or app UI feature**, eligibility should be granted to either:

1. The specifically intended verified account, meeting that feature's existing exact identity/Relay/session checks; OR
2. `OwnerFeaturePreviewAccess.eligible(context)`, a central exact-owner verification helper that requires a currently approved Relay grant for the owner's Discord identity and an authenticated matching personal VRChat account (on Android phone or Quest).

The owner should **see the same selectable UI option** the intended user sees, including any exclusive preset in both the quick picker and Advanced Theme Studio, but there should be **no automatic activation**. Preserve existing user themes, individual opt-in settings, original ordering of previously shipped palettes and ability to switch back. Users besides the target and owner must not see or select the item; guard the underlying setter, not just menu visibility.

Use the current pattern in `ThemePreferences.exclusivePresetEligible`, `ThemePreferences.canSelect`, and `ThemePreferences.availablePresetIndices`. When a *new* exclusive theme is introduced, add the target-specific predicate to the eligible cases AND include the same verified owner test override. Do not infer a role from an owner's access to somebody else's preview or grant rankless users any staff permissions.

## Security boundaries

This rule is about **app feature testing and interface previews**. It does **not** bypass Relay node authorization, impersonate someone else's personal account, send messages on their behalf, grant invitation permissions, copy their private data, or override VRChat API authorization. Features requiring backend authorization must use real backend permissions or a separately approved test environment. `OwnerFeaturePreviewAccess` is not a universal authority to perform network actions.

The owner account is verified through trusted `StaffGate` and `VRChatSession` state, not editable inputs, display names, plain saved settings, rank-only flags, or a switch called 'I'm the owner.' It must not be publicly toggleable. Enforce this both in UI and feature-level methods.

## QA for future releases

- Owner sees and can opt in to every intended UI-only exclusive feature on Android and Quest.
- Intended target user/delegate retains their exact existing eligibility.
- Ordinary staff, other Royalty accounts and unrelated delegates cannot see or enable it.
- No exclusive preset is selected automatically when updating; other preferences do not change.
- Removing a valid Relay grant, changing the signed-in VRChat account or revoking a delegate blocks unauthorized access.
- Build/sign the complete app with the permanent signing key, check upgrade compatibility, and document results in the canonical Main Launcher handoff.
- **Do not use generated concept mockups as instructions to change the real UI** unless the owner separately requests that redesign.
