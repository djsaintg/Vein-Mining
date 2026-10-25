# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**Project by:** djsaintg (concept & direction) · Claude/Anthropic (development)

---

## [0.1.0] — 2026-10-25

### Added
- Breaking an ore block also breaks every connected ore block of the same metal, up to 64 extra
  blocks, nearest first.
- Vein blocks use the normal mining rules: the tool must be good enough, normal drops, normal
  durability per block, and their own cancellable break events.
- Nothing extra is mined if the original break is cancelled (e.g. by a protection plugin).
- Disabled in Creative mode.
- `config.json` with `maxBlocks`, `requireCrouch`, `includeDiagonals` and `blocks`.
