# Changelog

---

## [0.1.3] - 2026-10-16
### Feat
- The same key may be repeated in `.thetree.yaml`; every occurrence is applied in order.
- `.` path segments are ignored (`./src/main` is the same as `src/main`).
### Fix
- Hiding a folder now also hides the modules and source roots nested in it.

## [0.1.2] - 2026-07-03
### Fix
- Issues regarding mixing compacted and hidden

## [0.1.1] - 2026-07-03
### Feat
- Enabled pattern matching with `?`, `*` and `**`.

## [0.1.0] - 2026-07-02
- Initial version
