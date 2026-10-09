# Source

Vendored from https://github.com/SimoneAvogadro/android-reverse-engineering-skill
(`plugins/android-reverse-engineering`, commit 04fe39c, 2026-09-08). Apache License 2.0, see LICENSE.
The `/decompile` command lives in `.claude/commands/decompile.md`.

Local changes: `${CLAUDE_PLUGIN_ROOT}` paths point at this repo (bash commands are anchored with
`git rev-parse --show-toplevel`). Tools it needs (jadx; optionally Vineflower, dex2jar) are not
installed yet; `scripts/install-dep.sh` installs them per user under ~/.local/share from their
GitHub releases. Java 17+ is already available (JDK 21 at ~/.local/jdks).
