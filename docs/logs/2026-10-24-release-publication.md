# 2026-10-24

## 0.1.0 publication

The owner explicitly approved pushing the existing v0.1.0 tag and publishing
its GitHub source release after reviewing the preparation result.

Rechecked a clean tree, original tag target, absence of remote tags, and
absence of GitHub releases. Executed:

```
git push --no-follow-tags origin refs/tags/v0.1.0:refs/tags/v0.1.0
gh release create v0.1.0 --repo ascasso/setaccio-lab --verify-tag \
  --title 'Setaccio Lab 0.1.0' --notes-file docs/releases/0.1.0-notes.md
```

Published: https://github.com/ascasso/setaccio-lab/releases/tag/v0.1.0

GitHub timestamp: 2026-10-25T01:19:51Z (2026-10-24 in America/New_York).
Remote annotated tag object a88ec2fec5377b5cb7a41d79947fbcd26c367745 and peeled
commit 016eef300764af4fa69898eba0eefb677a07b96f match the existing local tag.
API checks confirmed exact release-body equality, non-draft/non-prerelease
state, ZIP and tar source archive URLs, and no uploaded assets.

Updated README, next-work plan, deferred-work index, publication record, and
changelog. Capability, environment, and test-plan contracts are unchanged.
The immediately preceding preparation verified the exact release on Java 25:
all three builds and all eight isolated provider-free test suites passed.
This publication did not change source, so those checks were not repeated.
The standing current-branch build and `git diff --check` were run for closeout.

No tag was recreated or moved, no branch was promoted or merged, and no binary,
package, private data, or raw evidence was uploaded. No live model run or
historical evidence revalidation occurred. The source release retains its
original 2026-09-04 changelog date and excludes subsequent maintenance commits.
