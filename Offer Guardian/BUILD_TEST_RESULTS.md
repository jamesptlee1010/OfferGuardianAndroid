# v3.3.0 validation

- Existing host-side regression run reached and passed selectable touch methods. The final diagnostic guard step was run separately.
- 27 touch-method, 13 live guard and 18 existing own-screen guard assertions passed.
- Source inventory, Java syntax, workflow YAML and manifest validated.
- No changes to rules, detector, OCR or history classes.
- Full Android compilation, Shizuku binder runtime and real Uber dismissal remain unverified. Live input is experimental, opt-in and will skip if guards or permission fail. A successful command is not evidence of dismissal.
