#!/usr/bin/env python3
"""Validate and expose the repository's single release version contract."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


SEMVER_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
CHANGELOG_HEADING_PATTERN = re.compile(r"^##\s+(?:v)?(?P<version>[0-9]+\.[0-9]+\.[0-9]+)\s+-\s+(?P<date>[0-9]{4}-[0-9]{2}-[0-9]{2})\s*$")
MAX_COMPONENT = 999
MAX_ANDROID_VERSION_CODE = 2_100_000_000
VERSION_PATH = "VERSION"
CHANGELOG_PATH = "CHANGELOG.md"
APPLICATION_ID = "com.blackbag.minibox"


class VersionContractError(ValueError):
    """Raised when repository release metadata violates the version contract."""


@dataclass(frozen=True, order=True)
class ReleaseVersion:
    major: int
    minor: int
    patch: int

    @classmethod
    def parse(cls, raw: str, source: str = VERSION_PATH) -> "ReleaseVersion":
        value = raw.strip()
        match = SEMVER_PATTERN.fullmatch(value)
        if match is None:
            raise VersionContractError(
                f"{source} must contain exactly MAJOR.MINOR.PATCH without prefixes, suffixes, or leading zeroes: {value!r}"
            )
        version = cls(*(int(component) for component in match.groups()))
        if version.minor > MAX_COMPONENT or version.patch > MAX_COMPONENT:
            raise VersionContractError(
                f"{source} minor and patch components must be <= {MAX_COMPONENT}: {version}"
            )
        if version.version_code > MAX_ANDROID_VERSION_CODE:
            raise VersionContractError(
                f"{source} maps to Android versionCode {version.version_code}, above {MAX_ANDROID_VERSION_CODE}"
            )
        return version

    @property
    def version_code(self) -> int:
        return self.major * 1_000_000 + self.minor * 1_000 + self.patch + 1

    @property
    def tag(self) -> str:
        return f"v{self}"

    @property
    def apk_name(self) -> str:
        return f"minibox-v{self}.apk"

    @property
    def checksum_name(self) -> str:
        return f"{self.apk_name}.sha256"

    def __str__(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"


def run_git(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        check=check,
        capture_output=True,
        text=True,
    )


def read_worktree_version(root: Path) -> ReleaseVersion:
    path = root / VERSION_PATH
    if not path.is_file():
        raise VersionContractError(f"missing repository version file: {VERSION_PATH}")
    return ReleaseVersion.parse(path.read_text(encoding="utf-8"), VERSION_PATH)


def read_blob(commit: str, path: str) -> str | None:
    result = run_git("show", f"{commit}:{path}", check=False)
    if result.returncode == 0:
        return result.stdout
    missing_markers = ("does not exist", "exists on disk, but not in", "Path '")
    if any(marker in result.stderr for marker in missing_markers):
        return None
    raise VersionContractError(
        f"unable to read {path} from {commit}: {result.stderr.strip()}"
    )


def resolve_commit(value: str) -> str:
    result = run_git("rev-parse", f"{value}^{{commit}}", check=False)
    if result.returncode != 0:
        raise VersionContractError(f"unable to resolve Git commit {value!r}: {result.stderr.strip()}")
    return result.stdout.strip()


def changelog_section(text: str, version: ReleaseVersion, source: str = CHANGELOG_PATH) -> str:
    lines = text.splitlines()
    matches: list[int] = []
    for index, line in enumerate(lines):
        heading = CHANGELOG_HEADING_PATTERN.fullmatch(line)
        if heading and heading.group("version") == str(version):
            matches.append(index)
    if len(matches) != 1:
        raise VersionContractError(
            f"{source} must contain exactly one '## {version} - YYYY-MM-DD' section, found {len(matches)}"
        )
    start = matches[0]
    end = len(lines)
    for index in range(start + 1, len(lines)):
        if lines[index].startswith("## "):
            end = index
            break
    section_lines = lines[start:end]
    body_lines = [
        line.strip()
        for line in section_lines[1:]
        if line.strip() and not line.lstrip().startswith("###")
    ]
    if not body_lines:
        raise VersionContractError(f"{source} section for {version} has no release notes")
    return "\n".join(section_lines).rstrip() + "\n"


def tagged_versions() -> dict[ReleaseVersion, str]:
    result = run_git("tag", "--list", "v*")
    versions: dict[ReleaseVersion, str] = {}
    for raw_tag in result.stdout.splitlines():
        match = SEMVER_PATTERN.fullmatch(raw_tag.removeprefix("v"))
        if match is None or not raw_tag.startswith("v"):
            continue
        version = ReleaseVersion.parse(raw_tag[1:], f"Git tag {raw_tag}")
        if version in versions:
            raise VersionContractError(
                f"multiple Git tags encode version {version}: {versions[version]} and {raw_tag}"
            )
        versions[version] = raw_tag
    return versions


def validate_changelog(text: str | None, version: ReleaseVersion, source: str) -> str:
    if text is None:
        raise VersionContractError(f"missing {source}")
    return changelog_section(text, version, source)


def validate_bootstrap_history(
    base_sha: str,
    candidate_sha: str,
    candidate_version: ReleaseVersion,
) -> None:
    ancestor = run_git(
        "merge-base", "--is-ancestor", base_sha, candidate_sha, check=False
    )
    if ancestor.returncode != 0:
        raise VersionContractError(
            f"bootstrap base {base_sha} is not an ancestor of candidate {candidate_sha}"
        )

    result = run_git(
        "rev-list",
        "--reverse",
        "--topo-order",
        f"{base_sha}..{candidate_sha}",
        "--",
        VERSION_PATH,
    )
    commits = [line for line in result.stdout.splitlines() if line]
    if not commits:
        raise VersionContractError(
            f"bootstrap history from {base_sha} to {candidate_sha} does not introduce {VERSION_PATH}"
        )

    versions: list[ReleaseVersion] = []
    for commit in commits:
        version_text = read_blob(commit, VERSION_PATH)
        if version_text is None:
            raise VersionContractError(f"bootstrap history deletes {VERSION_PATH} at {commit}")
        version = ReleaseVersion.parse(version_text, f"{commit}:{VERSION_PATH}")
        validate_changelog(
            read_blob(commit, CHANGELOG_PATH),
            version,
            f"{commit}:{CHANGELOG_PATH}",
        )
        versions.append(version)

    if versions[0] != ReleaseVersion(0, 0, 0):
        raise VersionContractError(
            f"bootstrap history must start at 0.0.0; found {versions[0]}"
        )
    for previous, current in zip(versions, versions[1:]):
        if current <= previous:
            raise VersionContractError(
                f"bootstrap version {current} must be greater than {previous}"
            )
    if versions[-1] != candidate_version:
        raise VersionContractError(
            f"bootstrap history ends at {versions[-1]}, candidate is {candidate_version}"
        )


def validate_pull_request(base: str, candidate: str) -> ReleaseVersion:
    base_sha = resolve_commit(base)
    candidate_sha = resolve_commit(candidate)
    candidate_version_text = read_blob(candidate_sha, VERSION_PATH)
    if candidate_version_text is None:
        raise VersionContractError(f"candidate {candidate_sha} does not contain {VERSION_PATH}")
    candidate_version = ReleaseVersion.parse(
        candidate_version_text,
        f"{candidate_sha}:{VERSION_PATH}",
    )
    validate_changelog(
        read_blob(candidate_sha, CHANGELOG_PATH),
        candidate_version,
        f"{candidate_sha}:{CHANGELOG_PATH}",
    )

    base_version_text = read_blob(base_sha, VERSION_PATH)
    if base_version_text is None:
        validate_bootstrap_history(base_sha, candidate_sha, candidate_version)
    else:
        base_version = ReleaseVersion.parse(base_version_text, f"{base_sha}:{VERSION_PATH}")
        if candidate_version <= base_version:
            raise VersionContractError(
                f"candidate version {candidate_version} must be greater than base version {base_version}"
            )

    tags = tagged_versions()
    if candidate_version in tags:
        raise VersionContractError(
            f"candidate version {candidate_version} is already reserved by Git tag {tags[candidate_version]}"
        )
    if tags and candidate_version <= max(tags):
        raise VersionContractError(
            f"candidate version {candidate_version} must be greater than latest tagged version {max(tags)}"
        )
    print(
        f"Version contract OK: base={base_sha} candidate={candidate_sha} version={candidate_version} versionCode={candidate_version.version_code}"
    )
    return candidate_version


def validate_current(
    root: Path,
    allow_existing_tag_at_head: bool,
    base: str | None = None,
) -> ReleaseVersion:
    version = read_worktree_version(root)
    changelog_path = root / CHANGELOG_PATH
    if not changelog_path.is_file():
        raise VersionContractError(f"missing repository changelog: {CHANGELOG_PATH}")
    changelog_section(changelog_path.read_text(encoding="utf-8"), version)

    if base is not None:
        base_sha = resolve_commit(base)
        base_version_text = read_blob(base_sha, VERSION_PATH)
        if base_version_text is None:
            validate_bootstrap_history(base_sha, resolve_commit("HEAD"), version)
        else:
            base_version = ReleaseVersion.parse(base_version_text, f"{base_sha}:{VERSION_PATH}")
            if version <= base_version:
                raise VersionContractError(
                    f"current version {version} must be greater than pushed base version {base_version}"
                )

    head = resolve_commit("HEAD")
    tags = tagged_versions()
    existing_tag = tags.get(version)
    if existing_tag is not None:
        tagged_commit = resolve_commit(existing_tag)
        if not allow_existing_tag_at_head or tagged_commit != head:
            raise VersionContractError(
                f"version {version} is already bound to {existing_tag} at {tagged_commit}; current HEAD is {head}"
            )
    previous_versions = [candidate for candidate in tags if candidate != version]
    if previous_versions and version <= max(previous_versions):
        raise VersionContractError(
            f"current version {version} must be greater than previous tagged version {max(previous_versions)}"
        )
    print(
        f"Version contract OK: head={head} version={version} versionCode={version.version_code} tag={version.tag}"
    )
    return version


def write_github_output(path: Path, version: ReleaseVersion) -> None:
    values = {
        "version": str(version),
        "version_code": str(version.version_code),
        "tag": version.tag,
        "application_id": APPLICATION_ID,
        "apk_name": version.apk_name,
        "checksum_name": version.checksum_name,
    }
    with path.open("a", encoding="utf-8") as stream:
        for name, value in values.items():
            stream.write(f"{name}={value}\n")


def write_step_summary(path: Path, version: ReleaseVersion) -> None:
    with path.open("a", encoding="utf-8") as stream:
        stream.write("## Release version\n\n")
        stream.write(f"- Version: `{version}`\n")
        stream.write(f"- Android versionCode: `{version.version_code}`\n")
        stream.write(f"- Application ID: `{APPLICATION_ID}`\n")
        stream.write(f"- Tag: `{version.tag}`\n")
        stream.write(f"- APK: `{version.apk_name}`\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    pull_request = subparsers.add_parser("check-pr")
    pull_request.add_argument("--base", required=True)
    pull_request.add_argument("--candidate", required=True)

    current = subparsers.add_parser("check-current")
    current.add_argument("--root", type=Path, default=Path.cwd())
    current.add_argument("--base")
    current.add_argument("--allow-existing-tag-at-head", action="store_true")
    current.add_argument("--github-output", type=Path)
    current.add_argument("--step-summary", type=Path)

    notes = subparsers.add_parser("release-notes")
    notes.add_argument("--root", type=Path, default=Path.cwd())
    notes.add_argument("--output", type=Path, required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        if args.command == "check-pr":
            validate_pull_request(args.base, args.candidate)
            return 0
        if args.command == "check-current":
            version = validate_current(
                args.root,
                args.allow_existing_tag_at_head,
                base=args.base,
            )
            if args.github_output:
                write_github_output(args.github_output, version)
            if args.step_summary:
                write_step_summary(args.step_summary, version)
            return 0
        if args.command == "release-notes":
            version = read_worktree_version(args.root)
            changelog_path = args.root / CHANGELOG_PATH
            notes = changelog_section(changelog_path.read_text(encoding="utf-8"), version)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(notes, encoding="utf-8")
            print(f"Wrote release notes for {version} to {args.output}")
            return 0
    except (OSError, subprocess.CalledProcessError, VersionContractError) as error:
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print(f"::error title=Release version contract::{error}", file=sys.stderr)
        else:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1
    raise AssertionError(f"unsupported command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
