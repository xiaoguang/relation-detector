#!/usr/bin/env python3
"""Compare relation-detector facts and semantic observations between result trees."""

import argparse
from collections import defaultdict
import hashlib
import json
from pathlib import Path


IMPLEMENTATION_KEYS = {
    "backend",
    "detail",
    "fullGrammarContextSource",
    "fullGrammarNative",
    "fullGrammarProfile",
    "parser",
    "parserBackend",
    "parserClass",
    "parserMode",
    "parserName",
    "parserProfile",
    "profile",
    "profileId",
    "resultName",
    "tokenEventNative",
    "count",
    "occurrenceCount",
}
IMPLEMENTATION_KEYS.add("fullGram" + "merNative")
IMPLEMENTATION_KEYS.add("fullGram" + "merProfile")
IMPLEMENTATION_KEYS.add("fullGram" + "merContextSource")

PROVENANCE_NORMALIZATION_ATTRIBUTES = {
    "sourceObjectType",
}

RECOVERY_AND_REGRESSION_CLASSIFICATIONS = {
    "ROUTINE_RELATIONSHIP_OBSERVATION_RECOVERY",
    "ROUTINE_NAMING_OBSERVATION_RECOVERY",
    "ROUTINE_LINEAGE_RECOVERY",
    "ROUTINE_DERIVED_RECOVERY",
    "NAMING_OBSERVATION_SELECTION_REGRESSION",
    "CONDITIONAL_RELATIONSHIP_RECOVERY",
    "CONDITIONAL_DERIVED_REGRESSION",
    "UNION_LINEAGE_RECOVERY",
    "SQL_ASSET_CORRECTION",
}

ALLOWED_CLASSIFICATIONS = {
    "A_TO_B": {
        "IMPLEMENTATION_NAME_ONLY",
        "PROVENANCE_NORMALIZATION_ONLY",
        "STRUCTURE_MIGRATION_REGRESSION",
        "PREVIOUS_GOLDEN_CORRECTION",
        "REVIEW_NEEDED",
    } | RECOVERY_AND_REGRESSION_CLASSIFICATIONS,
    "B_TO_C": {
        "DDL_UNIQUENESS_CORRECTION",
        "TRIGGER_FACT_RECOVERY",
        "SELF_UPDATE_LINEAGE_RECOVERY",
        "SQL_ASSET_CORRECTION",
        "PROVENANCE_CORRECTION",
        "FALSE_POSITIVE_REMOVAL",
        "REVIEW_NEEDED",
    },
    "C_TO_D": {
        "IMPLEMENTATION_NAME_ONLY",
        "PROVENANCE_NORMALIZATION_ONLY",
        "PROVENANCE_CORRECTION",
        "DDL_UNIQUENESS_CORRECTION",
        "TRIGGER_FACT_RECOVERY",
        "SELF_UPDATE_LINEAGE_RECOVERY",
        "FALSE_POSITIVE_REMOVAL",
        "REVIEW_NEEDED",
    } | RECOVERY_AND_REGRESSION_CLASSIFICATIONS,
}


def normalized_string(value):
    marker = "relation-detector/"
    if marker in value:
        value = value[value.index(marker):]
    legacy = "gram" + "mer"
    replacements = (
        ("FULL_" + legacy.upper(), "FULL_GRAMMAR"),
        ("Full" + legacy.title(), "FullGrammar"),
        ("full-" + legacy, "full-grammar"),
        ("full" + legacy, "fullgrammar"),
    )
    for old, new in replacements:
        value = value.replace(old, new)
    return value


def canonical(value):
    if isinstance(value, dict):
        return {
            key: canonical(child)
            for key, child in sorted(value.items())
            if key not in IMPLEMENTATION_KEYS
        }
    if isinstance(value, list):
        return [canonical(child) for child in value]
    if isinstance(value, str):
        return normalized_string(value)
    return value


def stable_json(value):
    return json.dumps(canonical(value), ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def endpoint(endpoint):
    if not isinstance(endpoint, dict):
        return str(endpoint)
    return ".".join(
        str(endpoint[name])
        for name in ("catalog", "schema", "table", "column")
        if endpoint.get(name) not in (None, "")
    )


def evidence_types(fact):
    return sorted({item.get("type") for item in fact.get("evidence") or [] if item.get("type")})


def fact_identity(section, fact):
    if section == "relationships":
        value = {
            "source": endpoint(fact.get("source")),
            "target": endpoint(fact.get("target")),
            "relationType": fact.get("relationType"),
            "relationSubType": fact.get("relationSubType"),
            "evidenceTypes": evidence_types(fact),
        }
    elif section == "dataLineages":
        sources = fact.get("sources")
        if sources is None and fact.get("source") is not None:
            sources = [fact.get("source")]
        value = {
            "sources": sorted(endpoint(item) for item in (sources or [])),
            "target": endpoint(fact.get("target")),
            "flowKind": fact.get("flowKind"),
            "transformType": fact.get("transformType"),
        }
    elif section == "namingEvidence":
        value = {
            "source": endpoint(fact.get("source")),
            "target": endpoint(fact.get("target")),
            "rule": fact.get("rule"),
            "directionHint": fact.get("directionHint"),
        }
    elif section in ("derivedRelationships", "derivedDataLineages"):
        value = {
            "kind": fact.get("kind"),
            "source": endpoint(fact.get("source")),
            "target": endpoint(fact.get("target")),
            "path": [endpoint(item) for item in fact.get("path") or []],
            "pathLength": fact.get("pathLength"),
        }
    elif section == "warnings":
        attributes = fact.get("attributes") or {}
        value = {
            "code": fact.get("code") or fact.get("warningCode"),
            "source": normalized_string(str(fact.get("source") or "")),
            "sourceFile": normalized_string(str(attributes.get("sourceFile") or "")),
            "sourceStatementId": attributes.get("sourceStatementId"),
            "sourceLine": attributes.get("sourceLine"),
        }
    else:
        value = fact
    return stable_json(value)


def observation_fact_identity(section, fact):
    """Identify the supported fact without aggregate evidence added by other observations."""
    if section != "relationships":
        return fact_identity(section, fact)
    return stable_json({
        "source": endpoint(fact.get("source")),
        "target": endpoint(fact.get("target")),
        "relationType": fact.get("relationType"),
        "relationSubType": fact.get("relationSubType"),
    })


def parser_name(path):
    name = path.stem
    return name.replace("gram" + "mer", "grammar")


def fact_contexts(fact):
    contexts = set()
    for observation in fact.get("rawEvidence") or []:
        attributes = observation.get("attributes") or {}
        source_file = attributes.get("sourceFile")
        source_object = attributes.get("sourceObjectName") or attributes.get("sourceBlockId")
        statement = attributes.get("sourceStatementId")
        line = attributes.get("sourceLine")
        source = source_file or source_object or observation.get("source")
        if source:
            context = normalized_string(str(source))
            if statement:
                context += "#" + str(statement)
            if line is not None:
                context += ":" + str(line)
            contexts.add(context)
    return sorted(contexts)


def load_tree(root):
    facts = {}
    observations = {}
    sections = (
        "relationships",
        "dataLineages",
        "namingEvidence",
        "derivedRelationships",
        "derivedDataLineages",
        "warnings",
    )
    for path in sorted(root.rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        parser = parser_name(path)
        for section in sections:
            for fact in data.get(section) or []:
                identity = fact_identity(section, fact)
                key = (parser, section, identity)
                facts[key] = {
                    "fact": json.loads(identity),
                    "sqlContexts": fact_contexts(fact),
                }
                observation_fact = json.loads(observation_fact_identity(section, fact))
                for observation in fact.get("rawEvidence") or []:
                    observation_identity = stable_json({
                        "fact": observation_fact,
                        "observation": observation,
                    })
                    observation_key = (parser, section, observation_identity)
                    observations[observation_key] = json.loads(observation_identity)
    return facts, observations


def change_id(transition, scope, change_type, parser, section, value):
    encoded = json.dumps({
        "transition": transition,
        "scope": scope,
        "changeType": change_type,
        "parserCategory": normalized_string(parser),
        "section": section,
        "value": canonical(value),
    }, ensure_ascii=False, separators=(",", ":"), sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def diff_scope(before, after, transition, scope):
    changes = []
    for change_type, keys, values in (
        ("REMOVED", sorted(set(before) - set(after)), before),
        ("ADDED", sorted(set(after) - set(before)), after),
    ):
        for parser, section, identity in keys:
            value = values[(parser, section, identity)]
            changes.append({
                "id": change_id(transition, scope, change_type, parser, section, value),
                "transition": transition,
                "scope": scope,
                "changeType": change_type,
                "parser": parser,
                "section": section,
                "value": value,
                "classification": "REVIEW_NEEDED",
                "rationale": "",
                "sqlContext": "",
            })
    return changes


def provenance_normalization_key(value):
    """Return a key only when an observation differs in permitted provenance."""
    observation = value.get("observation")
    if not isinstance(observation, dict):
        return None
    attributes = observation.get("attributes")
    if not isinstance(attributes, dict):
        return None
    normalized = json.loads(stable_json(value))
    normalized_attributes = normalized["observation"].get("attributes")
    if not isinstance(normalized_attributes, dict):
        return None
    for attribute in PROVENANCE_NORMALIZATION_ATTRIBUTES:
        normalized_attributes.pop(attribute, None)
    return stable_json(normalized)


def strict_provenance_pair_ids(before, after, transition):
    """Pair changed observations only when each semantic identity occurs once per side."""
    before_only = set(before) - set(after)
    after_only = set(after) - set(before)
    before_groups = defaultdict(list)
    after_groups = defaultdict(list)
    for parser, section, identity in before_only:
        value = before[(parser, section, identity)]
        key = provenance_normalization_key(value)
        if key is not None:
            before_groups[(parser, section, key)].append(value)
    for parser, section, identity in after_only:
        value = after[(parser, section, identity)]
        key = provenance_normalization_key(value)
        if key is not None:
            after_groups[(parser, section, key)].append(value)

    pair_ids = set()
    for parser, section, key in sorted(set(before_groups) & set(after_groups)):
        removed = before_groups[(parser, section, key)]
        added = after_groups[(parser, section, key)]
        if len(removed) != 1 or len(added) != 1:
            continue
        pair_ids.add(change_id(transition, "OBSERVATION", "REMOVED", parser, section, removed[0]))
        pair_ids.add(change_id(transition, "OBSERVATION", "ADDED", parser, section, added[0]))
    return pair_ids


def observation_context(value):
    observation = value.get("observation") or {}
    attributes = observation.get("attributes") or {}
    source = attributes.get("sourceFile") or observation.get("source")
    statement = attributes.get("sourceStatementId")
    line = attributes.get("sourceLine")
    if not source:
        return ""
    context = normalized_string(str(source))
    if statement:
        context += "#" + str(statement)
    if line is not None:
        context += ":" + str(line)
    return context


def apply_strict_provenance_pairs(changes, pair_ids):
    for change in changes:
        if change["id"] not in pair_ids:
            continue
        change["classification"] = "PROVENANCE_NORMALIZATION_ONLY"
        change["rationale"] = (
            "Strict 1:1 observation pair: the semantic observation and location are unchanged; "
            "only permitted provenance attributes differ."
        )
        change["sqlContext"] = observation_context(change["value"])


def apply_classifications(changes, path, transition, provenance_pair_ids):
    if path is None:
        return
    data = json.loads(path.read_text(encoding="utf-8"))
    classifications = data.get("changes") or {}
    known_ids = {change["id"] for change in changes}
    unknown = sorted(set(classifications) - known_ids)
    if unknown:
        raise SystemExit("Classification file contains unknown change ids: " + ", ".join(unknown))
    allowed = ALLOWED_CLASSIFICATIONS[transition]
    for change in changes:
        entry = classifications.get(change["id"])
        if not entry:
            continue
        classification = entry.get("classification")
        if classification not in allowed:
            raise SystemExit("Classification {} is not allowed for {}".format(classification, transition))
        if classification == "PROVENANCE_NORMALIZATION_ONLY" and change["id"] not in provenance_pair_ids:
            raise SystemExit(
                "PROVENANCE_NORMALIZATION_ONLY requires a strict 1:1 observation pair: {}".format(
                    change["id"]
                )
            )
        if change["id"] in provenance_pair_ids and classification != "PROVENANCE_NORMALIZATION_ONLY":
            raise SystemExit(
                "Strict provenance pair must remain PROVENANCE_NORMALIZATION_ONLY: {}".format(change["id"])
            )
        rationale = str(entry.get("rationale") or "").strip()
        sql_context = str(entry.get("sqlContext") or "").strip()
        if not rationale or not sql_context:
            raise SystemExit("Classification {} requires rationale and sqlContext".format(change["id"]))
        change["classification"] = classification
        change["rationale"] = rationale
        change["sqlContext"] = sql_context


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--before", type=Path)
    parser.add_argument("--after", type=Path)
    parser.add_argument("--transition", choices=sorted(ALLOWED_CLASSIFICATIONS))
    parser.add_argument("--inventory-root", type=Path)
    parser.add_argument("--classifications", type=Path)
    parser.add_argument("--require-no-review", action="store_true")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    if args.inventory_root is not None:
        if args.before is not None or args.after is not None or args.transition is not None:
            parser.error("--inventory-root cannot be combined with comparison arguments")
        facts, observations = load_tree(args.inventory_root)
        fact_values = [
            {"parser": key[0], "section": key[1], "value": facts[key]}
            for key in sorted(facts)
        ]
        observation_values = [
            {"parser": key[0], "section": key[1], "value": observations[key]}
            for key in sorted(observations)
        ]
        report = {
            "root": str(args.inventory_root),
            "summary": {
                "factCount": len(fact_values),
                "observationCount": len(observation_values),
            },
            "factFingerprint": hashlib.sha256(stable_json(fact_values).encode("utf-8")).hexdigest(),
            "observationFingerprint": hashlib.sha256(stable_json(observation_values).encode("utf-8")).hexdigest(),
        }
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return

    if args.before is None or args.after is None or args.transition is None:
        parser.error("--before, --after and --transition are required for comparison")

    before_facts, before_observations = load_tree(args.before)
    after_facts, after_observations = load_tree(args.after)
    changes = diff_scope(before_facts, after_facts, args.transition, "FACT")
    changes.extend(diff_scope(before_observations, after_observations, args.transition, "OBSERVATION"))
    changes.sort(key=lambda item: (item["parser"], item["section"], item["scope"], item["changeType"], item["id"]))
    provenance_pair_ids = set()
    if "PROVENANCE_NORMALIZATION_ONLY" in ALLOWED_CLASSIFICATIONS[args.transition]:
        provenance_pair_ids = strict_provenance_pair_ids(before_observations, after_observations, args.transition)
        apply_strict_provenance_pairs(changes, provenance_pair_ids)
    apply_classifications(changes, args.classifications, args.transition, provenance_pair_ids)
    change_ids = [change["id"] for change in changes]
    if len(change_ids) != len(set(change_ids)):
        raise SystemExit("Semantic change ids must be unique across parser categories")
    classified_provenance_ids = {
        change["id"]
        for change in changes
        if change["classification"] == "PROVENANCE_NORMALIZATION_ONLY"
    }
    if classified_provenance_ids != provenance_pair_ids:
        raise SystemExit("Provenance-only classifications must exactly equal strict provenance pairs")

    review_needed = sum(1 for change in changes if change["classification"] == "REVIEW_NEEDED")
    fact_changes = sum(1 for change in changes if change["scope"] == "FACT")
    observation_changes = sum(1 for change in changes if change["scope"] == "OBSERVATION")
    report = {
        "transition": args.transition,
        "before": str(args.before),
        "after": str(args.after),
        "summary": {
            "factChanges": fact_changes,
            "observationChanges": observation_changes,
            "classifiedChanges": len(changes) - review_needed,
            "reviewNeeded": review_needed,
            "provenanceNormalizationPairs": len(provenance_pair_ids) // 2,
            "provenanceNormalizationObservationChanges": len(provenance_pair_ids),
        },
        "changes": changes,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.require_no_review and review_needed:
        raise SystemExit("{} semantic changes still require review".format(review_needed))


if __name__ == "__main__":
    main()
