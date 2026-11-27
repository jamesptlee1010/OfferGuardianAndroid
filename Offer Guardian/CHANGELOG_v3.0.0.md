# 3.0.0
- Restored v0.9.15 single-platform service, manifest and accessibility package filter, with current history schema and selected v2.6.1 OCR and history modules.
- Removed dual-platform source, notification listener, switching and floating quick controls entirely from the live app. Historical data remains.
- Fixed UberDeclineRetry.observe to reset attempts when the verified financial key changes. Spaced bounded retries and native accessibility click when possible. Suppress gestures outside Uber or when target is not owned by Uber.
- Moved visual recognition to a single background worker with one reusable pixel array; added targeted repeat search, bounded OCR tasks and contrast/scale variants. Poll 900 ms, minimum regular capture 650 ms, fast confirmation 430 ms.
- Qualifying financial overlay and alert before strict history verification; repeat alerts suppressed for same financial key for 60 seconds. Actual delivery app response remains unconfirmed by gesture callbacks alone.
- Retained duration OCR fixes, history deduplication, cleanup, filters, import/export and $30/hour three-tier defaults. Existing preferences preserved; explicit reset button.
