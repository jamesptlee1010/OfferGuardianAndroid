# 3.3.1
- Correct focus-query selection for Android 16, retaining strict focus, caller, target and freshness checks.
- Log the focus query and verification result.
- Blocked requests do not consume dispatch budget; ambiguous input retains fail-closed behavior.
- Bump both Shizuku user-service versions so stale services are replaced.
- No financial, OCR, X detector or history changes.
