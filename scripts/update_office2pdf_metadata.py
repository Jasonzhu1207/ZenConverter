#!/usr/bin/env python3
"""Updates office2pdf metadata (SHA-256, file size, build date, commit, tag)
across project documentation and attribution files.
"""

import argparse
import re
import sys
from pathlib import Path


def update_file(path: Path, patterns_replacements: list[tuple[str, str]], dry_run: bool = False) -> bool:
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    content = path.read_text(encoding="utf-8")
    original = content
    all_matched = True

    for pattern, replacement in patterns_replacements:
        match = re.search(pattern, content)
        if not match:
            print(f"[WARN] Pattern not found in {path}:\n  Pattern: {pattern}", file=sys.stderr)
            all_matched = False
        content = re.sub(pattern, replacement, content, count=1)

    if content != original:
        if not dry_run:
            path.write_text(content, encoding="utf-8")
            print(f"[OK] Updated {path}")
        else:
            print(f"[DRY-RUN] Changes would be written to {path}")
    else:
        print(f"[INFO] No changes in {path}")
    return all_matched



def main() -> None:
    parser = argparse.ArgumentParser(description="Update Office2PDF metadata in documentation")
    parser.add_argument("--sha", required=True, help="New SHA-256 hash of libzen_office2pdf.so")
    parser.add_argument("--size", required=True, type=int, help="File size in bytes")
    parser.add_argument("--date", required=True, help="Build date string, e.g. 'September 12, 2026'")
    parser.add_argument("--tag", required=True, help="Upstream tag, e.g. 'v0.6.8' or 'current'")
    parser.add_argument("--commit", required=True, help="Upstream commit SHA")
    parser.add_argument("--repo-root", default=".", help="Path to repository root")
    parser.add_argument("--dry-run", action="store_true", help="Do not write changes to disk")
    args = parser.parse_args()

    root = Path(args.repo_root).resolve()
    sha = args.sha.strip()
    size = args.size
    formatted_size = f"{size:,}"
    date_str = args.date.strip()
    tag = args.tag.strip()
    commit = args.commit.strip()
    dry_run = args.dry_run
    success = True

    # 1. docs/development-setup.md
    dev_patterns = [
        (
            r"The checked-in [A-Za-z]+ \d{1,2}, \d{4} rebuild is `[^`]+` bytes with SHA-256\s*\r?\n\s*`[a-fA-F0-9]{64}`",
            f"The checked-in {date_str} rebuild is `{formatted_size}` bytes with SHA-256\n`{sha}`",
        )
    ]
    if tag != "current":
        dev_patterns.append(
            (
                r"release commit\s*\r?\n\s*`[a-fA-F0-9]{40}` \(`[^`]+`\)",
                f"release commit\n`{commit}` (`{tag}`)",
            )
        )
    if not update_file(root / "docs" / "development-setup.md", dev_patterns, dry_run=dry_run):
        success = False

    # 2. native/office2pdf-jni/README.md
    readme_patterns = [
        (
            r"checked-in [A-Za-z]+ \d{1,2}, \d{4} arm64\s*\r?\n\s*build is `[^`]+` bytes with SHA-256\s*\r?\n\s*`[a-fA-F0-9]{64}`",
            f"checked-in {date_str} arm64\nbuild is `{formatted_size}` bytes with SHA-256\n`{sha}`",
        )
    ]
    if tag != "current":
        readme_patterns.append(
            (
                r"release `[^`]+` at commit\s*\r?\n\s*`[a-fA-F0-9]{40}`",
                f"release `{tag}` at commit\n`{commit}`",
            )
        )
    if not update_file(root / "native" / "office2pdf-jni" / "README.md", readme_patterns, dry_run=dry_run):
        success = False

    # 3. third_party/THANKS.md
    thanks_patterns = [
        (
            r"Rebuilt on [A-Za-z]+ \d{1,2}, \d{4} from `native/office2pdf-jni`; size `[^`]+` bytes, SHA-256 `[a-fA-F0-9]{64}`",
            f"Rebuilt on {date_str} from `native/office2pdf-jni`; size `{formatted_size}` bytes, SHA-256 `{sha}`",
        )
    ]
    if tag != "current":
        thanks_patterns.append(
            (
                r"at `developer0hye/office2pdf` commit `[a-fA-F0-9]{40}`, released as `[^`]+`",
                f"at `developer0hye/office2pdf` commit `{commit}`, released as `{tag}`",
            )
        )
    if not update_file(root / "third_party" / "THANKS.md", thanks_patterns, dry_run=dry_run):
        success = False

    # 4. docs/license-and-attribution.md
    lic_patterns = [
        (
            r"Current binary: rebuilt on [A-Za-z]+ \d{1,2}, \d{4}, `[^`]+` bytes with SHA-256\s*\r?\n\s*`[a-fA-F0-9]{64}`",
            f"Current binary: rebuilt on {date_str}, `{formatted_size}` bytes with SHA-256\n  `{sha}`",
        )
    ]
    if tag != "current":
        lic_patterns.append(
            (
                r"Upstream source dependency: `developer0hye/office2pdf` commit\s*\r?\n\s*`[a-fA-F0-9]{40}`, release `[^`]+`",
                f"Upstream source dependency: `developer0hye/office2pdf` commit\n  `{commit}`, release `{tag}`",
            )
        )
    if not update_file(root / "docs" / "license-and-attribution.md", lic_patterns, dry_run=dry_run):
        success = False

    if not success:
        sys.exit(1)


if __name__ == "__main__":
    main()
