# v2.6.0 — Visual overlay fallback

- Restores a guarded screenshot-based decline path specifically for the DoorDash-hosted Uber offer window pattern reported in v2.5.1 diagnostics. Requires a separately visible Uber window, an unexposed top covering window above DoorDash, two distinct agreeing fresh frames, Uber offer identity, X score >=94%, matching financial details and a current DECLINE decision. Known conflicting windows, moved controls, changed targets, stale frames, competing actions and unknown financial data block taps. Normal positive ownership remains preferred. No automatic acceptance.
- Repeat X recognition searches near the last verified card border first; full search on failure. All screenshots are processed locally.
- Adds a quick accessibility confirmation check after DoorDash stage 1 and logs successful qualifying-rate overlay attachment. Keeps the existing two-stage safeguards and threshold protections.
- Adds notification listener permission/connection status, a reconnect button and recognition of fresh active Dasher notifications on listener connection. Notification access and switching must be enabled; Android may still block launch.
- New preferences default to enabled A$30/hr across single/double/triple tiers in both rule stores; other price filters are off on clean installs. Existing explicit saved preferences are preserved. The new Apply $30/hr to all six delivery tiers button enables and updates all tiers while preserving other conditions.
- Retains vertical D / DF / DR dock, rule settings, history and existing app identity. versionCode 31 / versionName 2.6.0.
- Host-side validation only. Android SDK/Gradle unavailable here; actual compilation and live overlay/notification interactions must be tested.
