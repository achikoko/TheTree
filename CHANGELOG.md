# Changelog

---

## [0.2.1] - 2026-10-27
### Fix
- Actions on nodes inside nested folders are now resolved by their full path from the content root, so `a/b/c` keys apply to `c` and root-level keys no longer leak onto nested nodes with the same name.

## [0.2.0] - 2026-10-16
### Feat
- Action names `/compact`, `/order` and `/hide`; `/compacted`, `/weight` and `/hidden` remain as discouraged aliases.
- `/rename` action: a plain string, or a list of strings and `{attrs, text}` objects (`R`, `RB`, `RI`, `E`, `G`, `GB`, `GI`, `GSM`).
- The config file is now `thetree.yaml`; the dotted `.thetree.yaml` remains as a discouraged fallback.

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
