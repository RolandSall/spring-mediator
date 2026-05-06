# Publishing — operational notes

This file is the operational cheat-sheet for releasing new versions of Spring
Mediator to Maven Central. For the high-level "how does the pipeline fit
together" overview, see the README.

## The release-please tag-trigger gotcha (read this first)

**Symptom:** You merge the release-please PR (`chore(main): release X.Y.Z`).
release-please tags `vX.Y.Z` automatically. But `release.yml` *doesn't fire on
that tag*, so nothing gets uploaded to Sonatype Central. The Sonatype
Deployments page stays empty for the new version.

**Cause:** A deliberate GitHub Actions security feature.

> "Tags pushed by the auto-provided `GITHUB_TOKEN` from inside one workflow
> cannot trigger other workflows." — *prevents infinite-loop chains where one
> workflow keeps creating tags that fire another workflow*.

`release-please-action` uses the auto `GITHUB_TOKEN` to push the tag, so
`release.yml` (which listens on `v*` tags) is silently skipped.

### Manual workaround (works every time)

After merging the release-please PR, from your local machine:

```bash
# Replace v1.0.3 with whatever version release-please tagged.
TAG=v1.0.3

git fetch --tags origin
git pull --rebase origin main

# Identify the merge commit the release-please tag points at:
git log -1 --oneline $TAG

# Delete the tag both remotely and locally:
git push origin :refs/tags/$TAG
git tag -d $TAG

# Re-create from local — this push counts as a USER push, so release.yml fires:
git tag $TAG <merge-commit-sha-from-step-above>
git push origin $TAG
```

Now `release.yml` runs, vanniktech publishes to Sonatype Central, and the
Deployments page shows the new entry within a minute.

### Permanent fixes (deferred — pick one when redesigning the pipeline)

Documented here so we don't re-discover the options each time.

**Option A — Use a fine-grained Personal Access Token for `release-please-action`.**

PAT-pushes count as "user pushes" → downstream workflows fire normally.

1. Create at <https://github.com/settings/personal-access-tokens/new> (fine-grained):
   - Repository access: only `RolandSall/spring-mediator`
   - Permissions: Contents (R/W), Pull requests (R/W), Workflows (R/W)
2. Store as repo secret `RELEASE_PLEASE_TOKEN`.
3. In `.github/workflows/release-please.yml`, change:
   ```yaml
   - uses: googleapis/release-please-action@v4
     with:
       config-file: .github/release-please-config.json
       manifest-file: .github/.release-please-manifest.json
       token: ${{ secrets.RELEASE_PLEASE_TOKEN }}   # ← add this line
   ```

**Option B — Fold publishing into the release-please workflow itself.**

No PAT required. Trade-off: less modular, but zero extra setup. Drop the
`v* tag → release.yml` indirection entirely; do the upload inside
release-please.yml right after the action reports a successful release.

```yaml
# .github/workflows/release-please.yml — sketch
jobs:
  release-please:
    runs-on: ubuntu-latest
    steps:
      - uses: googleapis/release-please-action@v4
        id: release
        with: { … }

      # Only run the publish steps when release-please actually cut a release:
      - if: steps.release.outputs.release_created == 'true'
        name: Checkout the release tag
        uses: actions/checkout@v4
        with: { ref: ${{ steps.release.outputs.tag_name }} }

      - if: steps.release.outputs.release_created == 'true'
        name: Publish to Maven Central
        env: { … same env vars as release.yml … }
        run: |
          ./gradlew publishAndReleaseToMavenCentral \
            -Pversion=${{ steps.release.outputs.version }} \
            --no-configuration-cache
```

Then delete `release.yml`. Simpler workflow inventory, but couples the two
concerns.

---

## Quick-status commands

When you're not sure where a release is in the pipeline:

```bash
# Most recent workflow runs on the repo:
gh run list --repo RolandSall/spring-mediator --limit 8

# Was the v* tag created? Did release.yml fire?
gh run list --workflow=release.yml --repo RolandSall/spring-mediator --limit 5

# Sonatype deployment state for a known deployment id:
curl -s -u "$OSSRH_USERNAME:$OSSRH_PASSWORD" \
  -X POST "https://central.sonatype.com/api/v1/publisher/status?id=<deployment-id>"

# Is the artifact public on Maven Central yet?
curl -sI https://repo1.maven.org/maven2/io/github/springmediator/spring-mediator-starter/<version>/spring-mediator-starter-<version>.pom
```

---

## What v1.0.2 / v1.0.3 history teaches us

| Tag | How tagged | release.yml fired? | Outcome |
| --- | --- | --- | --- |
| `v1.0.1` | release-please-action (auto-`GITHUB_TOKEN`) | ✗ silently skipped | manually re-tagged → fired but ran with workflow bugs (per-module publish + GHP URL) → never reached Sonatype |
| `v1.0.2` | manual local push | ✓ | published successfully to Maven Central |
| `v1.0.3` | release-please-action then manual re-push | ✓ (after re-push) | published with corrected POM URLs |

The `release.yml` itself works once it fires. The brittleness is purely the
trigger path. Hence the two permanent-fix options above.

---

## Secrets we depend on

Stored as **repository secrets** at
<https://github.com/RolandSall/spring-mediator/settings/secrets/actions>:

| Secret | Source | Used by |
| --- | --- | --- |
| `OSSRH_USERNAME` | central.sonatype.com → Account → User Token (id) | release.yml — Maven Central upload |
| `OSSRH_PASSWORD` | central.sonatype.com → Account → User Token (password) | release.yml — Maven Central upload |
| `SIGNING_KEY` | `gpg --armor --export-secret-keys <fingerprint>` (full ASCII-armored block) | release.yml — signing |
| `SIGNING_PASSWORD` | passphrase set when generating the GPG key | release.yml — signing |

The local `.gitignore` blocks `*.asc`, `*.pem`, `*.key`, `.env`,
`credentials.*`, and similar so accidentally-exported keys can't be committed.
**Never paste these values in chat or in committed files.**

If a secret leaks: regenerate it (Sonatype: revoke User Token + generate new;
GPG: `gpg --gen-revoke <id>`, publish revocation, generate new key) and rerun
`gh secret set` for the affected name.

---

## Cutting a normal release

Once everything is set up (and assuming we haven't yet adopted Option A or B):

```
1. push commits to main with conventional-commit prefixes:
     feat: …  →  minor bump
     fix:  …  →  patch bump
     feat!:/BREAKING CHANGE:  →  major bump

2. release-please opens (or updates) "chore(main): release X.Y.Z" PR.

3. Review + merge that PR.

4. ⚠ Apply the manual workaround above to re-tag from local
   (until Option A or B is in place).

5. Watch:
     gh run list --workflow=release.yml --repo RolandSall/spring-mediator --limit 1
   — should turn green within ~1 min.

6. Wait 5–60 min for Sonatype to flip the deployment to PUBLISHED.

7. Verify on https://central.sonatype.com/artifact/io.github.springmediator/spring-mediator-starter
```
