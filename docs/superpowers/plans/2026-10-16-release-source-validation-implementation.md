# Trusted Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure candidate tag code cannot control the privileged workflow that validates source ancestry, accesses signing secrets, runs Gradle, or writes GitHub Releases.

**Architecture:** Split release into an unprivileged tag-triggered request workflow and a privileged `workflow_run` workflow loaded from the default branch. The privileged workflow validates `workflow_run.head_sha`, default-branch ancestry and the exact version tag before checking out or executing candidate code.

**Tech Stack:** GitHub Actions, Bash, Git, local shell regression tests.

**Spec:** `docs/superpowers/specs/2026-10-16-secure-ipc-and-release-design.md`

## Global Constraints

- The tag-triggered workflow has read-only permissions and no signing secrets.
- The privileged workflow uses `workflow_run`, so GitHub loads its definition from the default branch.
- Candidate code must not be checked out or executed before ancestry and tag validation succeeds.
- Candidate SHA must be a default-branch ancestor and have exactly one strict `v<major>.<minor>.<patch>` tag.
- Existing changelog concurrency protection remains.
- Tests do not use GitHub secrets or network access.
- Do not commit without explicit user authorization.

## Review Focus

- An attacker-modified tag workflow must not alter the privileged workflow; Task 2 verifies the trigger split.
- A feature-only candidate SHA must fail before candidate checkout; Task 1 tests this.
- A candidate with zero, multiple or malformed version tags must fail; Task 1 tests all three.
- An older valid default-branch ancestor with one version tag must pass; Task 1 tests this.
- Signing secrets must come from a documented protected environment and only appear after validation; Tasks 2–3 verify this.

---

### Task 1: Candidate-Provenance Verification Script

**Files:**
- Create: `.github/scripts/verify-release-candidate.sh`
- Create: `.github/scripts/test-verify-release-candidate.sh`

**Interfaces:**
- Produces: `verify-release-candidate.sh <candidate-sha> <remote-default-branch-ref>`.
- Output on success: shell-safe `RELEASE_TAG`, `RELEASE_VERSION` and `RELEASE_VERSION_CODE` lines for `$GITHUB_OUTPUT`.

- [ ] **Step 1: Write the failing shell regression test**

Create a temporary Git repository with a default branch, an older ancestor, an unmerged feature commit, legal/illegal tags and multiple tags. Assert: single valid tag on default head PASS; single valid tag on older ancestor PASS; feature-only commit FAIL; missing ref FAIL; zero tag FAIL; multiple matching tags FAIL; malformed version tag FAIL; empty arguments FAIL.

- [ ] **Step 2: Run the test and confirm RED**

Run: `bash .github/scripts/test-verify-release-candidate.sh`

Expected: FAIL because the verifier does not exist.

- [ ] **Step 3: Implement the fail-closed verifier**

Use `set -euo pipefail`, `git rev-parse --verify`, `git merge-base --is-ancestor` and `git tag --points-at`. Require exactly one tag matching the existing integer semantic-version grammar and preserve the current Android versionCode bounds.

- [ ] **Step 4: Run the regression test**

Run: `bash .github/scripts/test-verify-release-candidate.sh`

Expected: PASS.

- [ ] **Step 5: Review checkpoint**

Inspect quoting, cleanup traps, revision parsing and output encoding. The script must not fetch, checkout, push or execute repository files. Do not commit without explicit permission.

### Task 2: Split Unprivileged Request and Trusted Privileged Workflow

**Files:**
- Create: `.github/workflows/release-request.yml`
- Modify: `.github/workflows/release.yml`
- Create: `.github/scripts/test-release-workflow-boundary.sh`

**Interfaces:**
- Consumes: Task 1 verifier.
- Produces: `release-request.yml` on `push.tags: v*.*.*` with read-only permissions and no secrets.
- Produces: trusted `release.yml` on `workflow_run` completion of the exact request workflow name.

- [ ] **Step 1: Write the failing workflow-boundary test**

Assert the request workflow contains no `contents: write`, environment, secret reference, Gradle, release or repository script execution. Assert the privileged workflow uses `workflow_run`, reads `github.event.workflow_run.head_sha`, validates before candidate checkout, references `environment: release-signing`, and contains no `push.tags` trigger.

- [ ] **Step 2: Run the boundary test and confirm RED**

Run: `bash .github/scripts/test-release-workflow-boundary.sh`

Expected: FAIL because the split does not exist.

- [ ] **Step 3: Add the unprivileged request workflow**

Give it `contents: read`, a stable workflow name and one inert completion job. It must not checkout or execute candidate repository code; the event metadata supplies the candidate SHA to the trusted workflow.

- [ ] **Step 4: Convert the release workflow to `workflow_run`**

Trigger only when the request workflow completes successfully. Checkout the default branch version first, fetch default branch and tags, run Task 1 verifier against `workflow_run.head_sha`, then checkout the verified candidate SHA. Only after that may existing changelog, SDK, secret, Gradle and Release steps run.

- [ ] **Step 5: Attach the privileged job to `release-signing`**

Reference a GitHub Environment named `release-signing`. Keep repository code free of claims that required reviewers are enforced automatically; document that repository owners must configure reviewers, self-review prevention and tag/branch policies in GitHub settings.

- [ ] **Step 6: Preserve changelog concurrency behavior**

Continue comparing the default-branch changelog with the candidate-era file before committing the released version. Remove any assumption that this late check provides initial source authentication.

- [ ] **Step 7: Run workflow tests**

Run: `bash .github/scripts/test-verify-release-candidate.sh && bash .github/scripts/test-release-workflow-boundary.sh`

Expected: PASS.

- [ ] **Step 8: Review checkpoint**

Read both workflows in trigger order. Confirm no candidate-controlled workflow or code reaches secrets, write tokens, candidate checkout or Release operations before the trusted verifier. Do not commit without explicit permission.

### Task 3: Environment Setup Documentation and Final Verification

**Files:**
- Modify: `CONTRIBUTING.md`
- Modify: `CHANGELOG.md`
- Modify: `README.md` only if release instructions reference the old automatic workflow.

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: exact owner instructions for configuring `release-signing` environment secrets and protection rules.

- [ ] **Step 1: Document repository-owner setup**

List the four existing signing secrets, required reviewer setup, prevent-self-review setting, and selected tag/branch deployment policy. State which controls are enforced by code and which remain GitHub-hosted configuration.

- [ ] **Step 2: Add changelog entry**

Record that tag requests are unprivileged and the default-branch `workflow_run` validates candidate ancestry/tag identity before privileged work.

- [ ] **Step 3: Run all release checks**

Run: `bash .github/scripts/test-verify-release-candidate.sh`

Run: `bash .github/scripts/test-release-workflow-boundary.sh`

Run: `./gradlew testDebugUnitTest`

Run: `git diff --check`

Expected: all commands PASS.

- [ ] **Step 4: Launch one read-only bypass/regression review**

Give the reviewer only the original release finding and current diff. Ask it to test whether tag-controlled YAML, candidate artifacts, checkout order, workflow_run metadata, environment placement or changelog writes retain a bypass.

- [ ] **Step 5: Commit gate**

Stop and report code-enforced guarantees, required GitHub environment configuration, tests and remaining uncertainty. Commit/push only after explicit authorization.
