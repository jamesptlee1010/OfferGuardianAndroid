# Offer Guardian v0.9.6 — Mandatory Minimum Deliveries

**Base:** v0.9.5 Auto-Decline Recovery + Faster Waze + Advanced History Filters. All prior Android screens, history filtering, OCR hour/minute fixes, £/$5 whole-dollar fix, safe hour-bound declines, and Waze identity policies are retained.

## New user-facing rule

- Under **Offer Rules Builder → Mandatory Delivery Requirement**, enable the new delivery-count checkbox and choose a minimum of 1, 2, 3, or 4 deliveries.
- The setting is **off by default** on existing installs; opt in and press **Save rules** to activate it.
- The count gate is **mandatory**. Even if a payment or hourly rate satisfies ANY/OR, fewer deliveries still fails. A qualifying count must then meet the existing price/hourly/distance rules as configured.
- The badge `Delivery` or `Pack & Deliver` without parentheses is **one** when fully readable; `(2)`, `(3)`, `(4)` etc denote the indicated number. Missing or truncated header text gives **unknown**, not an assumed single.

## Runtime and safety

- New isolated `OfferDeliveryCount` parser examines only the green badge line near the verified Uber X; ignores item/unit counts, route information and addresses, and handles split OCR components.
- `OfferReadVerifier` / `OfferBoundVerifier` check that consecutive readings agree on count whenever this rule is active.
- New `OfferCountVerifier` independently confirms the delivery count, price, pickup merchant and X coordinates on two separate frames before a count-only rejection.
- A below-minimum delivery count can establish a safe decline even if the duration is unreadable. In that case, the existing SQLite store records a `COUNT_ONLY` extraction with **unknown** hourly rate. It never fabricates trip minutes.
- Waze's existing second-screen offer-identity, X-confidence and monitored-foreground restrictions remain mandatory; a gesture dispatch is not proof Uber completed a decline.
- Unknown count prevents automatic decline; TEST ONLY, auto-decline OFF and screenshot verification still block gestures.

## Compatibility

- `versionName '0.9.6'`, `versionCode 15`, same application ID and SQLite schema v2. Existing history migration unchanged; no new Android permissions or dependencies.
- Updated `.github/workflows/build-apk.yml` compiles and runs the new regression suite in addition to existing checks; ZIP contains fewer than 100 project files for GitHub browser upload.

## Important limitations

- A genuinely absent number in the badge means one delivery according to Uber's current UI. If OCR drops a displayed number *in every frame*, it could still be misclassified. Two-frame confirmation reduces but does not eliminate this risk.
- No Android APK was compiled or tested against live on-device OCR in this environment. Run the GitHub workflow, enable TEST ONLY and verify at least one each of single, double and triple offers, including Waze and dark/light variants, before allowing automatic gestures.
