---
description: git branching and push policy for Encapso
---

# Git Workflow Policy

## Branching Strategy
- `main` is production. It is always clean and deployable.
- `develop` is the integration branch for the next release.
- Feature work happens on `develop` (or feature branches off it).

## Commit & Push Policy — CRITICAL
**NEVER run `git commit` or `git push` without explicit user authorization.**

After finishing a feature or fix:
1. Run `mvn clean test` and confirm BUILD SUCCESS.
2. Show the user a summary of what was changed (files added/modified).
3. Inform the user: _"Tests pass. Ready to commit — shall I?"_
4. Wait for explicit commit approval before running `git commit`.
5. After committing, inform the user: _"Committed locally. Ready to push to `develop` — shall I?"_
6. Wait for explicit push approval before running `git push`.
7. Before pushing to `develop`, all related not pushed commits must be squased together into one meaningful commit which describes the feature that is delivered with that commit.
8. Always use the `Feature:` prefix for new feature commits (do NOT use `feat:`). For example: `Feature: implement sub-component isolation`.
9. Always use the `Fix:` prefix for bug fixes (do NOT use `fix:`).
10. Always use the `Refactor:` prefix for refactoring tasks.

## Merging to Main
- Only merge `develop` → `main` via a Pull Request on GitHub.
- Never force-push to `main`.