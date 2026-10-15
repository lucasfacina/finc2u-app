#!/usr/bin/env python3
"""Consulta a base OSV (api.osv.dev) por vulnerabilidades conhecidas nas dependências Maven listadas.

Entrada: arquivo com a saída de `mvn dependency:list` (linhas `grupo:artefato:tipo[:classificador]:versão:escopo`).
Saída: vulnerabilidades por dependência; código de saída 1 se houver alguma, 2 se a consulta falhar.

Uso: python3 -I scripts/osv-audit.py <arquivo>
"""
import json
import re
import sys
import urllib.error
import urllib.request

OSV = "https://api.osv.dev/v1"
COORD = re.compile(r"^\s*([\w.\-]+):([\w.\-]+):[\w.\-]+:(?:[\w.\-]+:)?([\w.\-]+):(compile|runtime|provided|test|system)\b")


def post(path, body):
    request = urllib.request.Request(
        f"{OSV}{path}", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def get(path):
    with urllib.request.urlopen(f"{OSV}{path}", timeout=60) as response:
        return json.load(response)


def parse(path):
    deps = {}
    with open(path, encoding="utf-8") as file:
        for line in file:
            match = COORD.match(line)
            if match:
                group, artifact, version, scope = match.groups()
                deps[(f"{group}:{artifact}", version)] = scope
    return deps


def severity(vuln):
    return (vuln.get("database_specific", {}).get("severity") or "SEM CLASSIFICAÇÃO").upper()


def fixed_versions(vuln, name):
    fixed = []
    for affected in vuln.get("affected", []):
        if affected.get("package", {}).get("name") != name:
            continue
        for rng in affected.get("ranges", []):
            fixed += [event["fixed"] for event in rng.get("events", []) if "fixed" in event]
    return sorted(set(fixed))


def main():
    deps = parse(sys.argv[1])
    if not deps:
        print("Nenhuma dependência encontrada na entrada.", file=sys.stderr)
        return 2
    keys = sorted(deps)
    try:
        results = post(
            "/querybatch",
            {"queries": [{"package": {"ecosystem": "Maven", "name": n}, "version": v} for n, v in keys]},
        )["results"]
        found = {}
        for key, result in zip(keys, results):
            ids = [vuln["id"] for vuln in result.get("vulns", [])]
            if ids:
                found[key] = ids
        details = {vid: get(f"/vulns/{vid}") for ids in found.values() for vid in ids}
    except (urllib.error.URLError, TimeoutError, KeyError) as error:
        print(f"Falha ao consultar a OSV: {error}", file=sys.stderr)
        return 2

    print(f"{len(deps)} dependências verificadas na OSV (Maven).")
    if not found:
        print("Nenhuma vulnerabilidade conhecida.")
        return 0

    print(f"\n{len(found)} dependência(s) com vulnerabilidades:\n")
    for (name, version), ids in sorted(found.items()):
        print(f"{name}:{version} [{deps[(name, version)]}]")
        for vid in sorted(ids):
            vuln = details[vid]
            aliases = ", ".join(a for a in vuln.get("aliases", []) if a.startswith("CVE-")) or "-"
            fixes = ", ".join(fixed_versions(vuln, name)) or "sem correção informada"
            print(f"  - {vid} ({aliases}) {severity(vuln)}: {vuln.get('summary', '').strip()}")
            print(f"    corrigido em: {fixes}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
