# 2.1.0 — Quick controls development source
- Optional top-centre D | DF | U accessibility overlay, shown while monitoring is enabled.
- D launches DoorDash; U tries a positively identified accessible Uber bubble, then direct launch.
- DF pauses a verified idle DoorDash session, checks Dash Paused / Resume dash, and returns to Uber. Does not end a Dash or sign out. Pauses may expire after 35 minutes.
- Navigation interlock stops offer scans while quick actions are in progress; no automatic acceptance. Unknown or inaccessible screens time out without further taps. Accessibility labels and window ownership must match; no guessed coordinates.
- Existing rules and history retained; new preference defaults off. Live Android and APK validation outstanding.
