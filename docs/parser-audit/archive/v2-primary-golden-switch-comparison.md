# Token/Event v2 Primary Golden Switch Comparison

This report compares the pre-switch correctness golden snapshot with the current Token/Event v2 primary golden files.

## Inputs

- Snapshot: `docs/parser-audit/archive/v2-primary-before-switch-golden-snapshot.json`
- Detailed item audit: `docs/parser-audit/archive/v2-primary-golden-diff-item-audit.md`

## Summary

| Metric | Count |
|---|---:|
| Compared golden files | 258 |
| Changed files | 132 |
| expected-diagnostics.json:added_items | 1 |
| expected-lineage.json:added_items | 50 |
| expected-relations.json:added_items | 161 |
| expected-relations.json:removed_items | 103 |
| Items requiring user review | 0 |

## Category Counts

| Count | Category |
|---:|---|
| 101 | `REMOVED_LEGACY_BARE_FIELD_CO_OCCURRENCE` |
| 87 | `ADDED_V2_COLUMN_CO_OCCURRENCE` |
| 72 | `ADDED_V2_QUALIFIED_FK_LIKE` |
| 50 | `ADDED_V2_INTERNAL_FIELD_LINEAGE` |
| 2 | `ADDED_CANONICAL_DIRECTION_REPLACES_OLD` |
| 2 | `REMOVED_OLD_DIRECTION_REPLACED_BY_CANONICAL` |
| 1 | `DIAGNOSTIC_ADDED` |

## Audit Conclusion

- No remaining ambiguous relation or lineage item requires user review.
- Removed relation items are either legacy bare-field/table co-occurrence cleanup or old reversed direction replaced by canonical direction.
- Added relation items are v2 column-level co-occurrence or fully-qualified FK-like relationships.
- Added lineage items are v2 internal database field lineage goldens.
- The detailed per-item list is intentionally kept in `v2-primary-golden-diff-item-audit.md`.
