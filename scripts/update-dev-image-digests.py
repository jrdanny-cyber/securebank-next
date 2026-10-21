#!/usr/bin/env python3
"""Update development values using exact published-image references."""

import argparse
import re
from pathlib import Path

REGISTRY = "535079144516.dkr.ecr.us-east-1.amazonaws.com"
REPOSITORIES = {
    "web": "securebank-next/web",
    "api": "securebank-next/banking-api",
}
ARTIFACT_FILES = {
    "web": "web.txt",
    "api": "banking-api.txt",
}


def update_values(values, references):
    # Validate both references before changing either value.
    digests = {}
    for component, repository in REPOSITORIES.items():
        reference = references[component].strip()
        match = re.fullmatch(
            re.escape(f"{REGISTRY}/{repository}@")
            + r"(sha256:[0-9a-f]{64})",
            reference,
        )
        if not match:
            raise ValueError(f"Invalid published image reference for {component}")
        digests[component] = match.group(1)

    updated = values
    for component, digest in digests.items():
        # Match only the expected top-level component's image.digest.
        pattern = re.compile(
            rf"(^{component}:\r?\n"
            rf"  image:\r?\n"
            rf"    digest: )sha256:[0-9a-f]{{64}}(?=\r?$)",
            re.MULTILINE,
        )
        updated, count = pattern.subn(
            lambda match: match.group(1) + digest,
            updated,
        )
        if count != 1:
            raise ValueError(
                f"Expected exactly one {component}.image.digest; found {count}"
            )

    return updated


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--references-dir", type=Path, required=True)
    parser.add_argument(
        "--values",
        type=Path,
        default=Path("deployment/environments/dev-localhost.yaml"),
    )
    args = parser.parse_args()

    references = {
        component: (args.references_dir / filename).read_text(encoding="utf-8")
        for component, filename in ARTIFACT_FILES.items()
    }
    original = args.values.read_bytes().decode("utf-8")
    updated = update_values(original, references)

    if updated == original:
        print("Development image digests already match the published references.")
        return

    args.values.write_bytes(updated.encode("utf-8"))
    print(f"Updated API and web image digests in {args.values}")


if __name__ == "__main__":
    main()
