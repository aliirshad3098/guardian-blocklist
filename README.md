# Guardian Blocklist

An adult-content domain blocklist for **Guardian** — a privacy-first, on-device
content filter that helps people overcome compulsive access to adult content.

The list is provided in **hosts format** (`0.0.0.0 domain`), gzip-compressed for
fast mobile downloads. A prebuilt, ready-to-load Bloom filter binary is also
published alongside it, so the app doesn't have to parse ~4.6 million lines on
the device itself (see [Prebuilt Bloom filter binary](#prebuilt-bloom-filter-binary) below).

## Contents

- `guardian_blocklist.txt.gz` — ~4.6 million adult domains (hosts format, gzipped)
- `guardian_blocklist.bin` — prebuilt Bloom filter, built from the list above
  (see below)
- `manifest.json` — version metadata for `guardian_blocklist.bin`

## Format

Each line follows the standard hosts-file convention:

    0.0.0.0 example-adult-site.com

An app or DNS filter reads each line and blocks the listed domain.

## How it's built

The list is derived from the **UT1 "adult" category** blacklist, then processed:

- De-duplicated at the full-domain level
- Cross-checked against a popularity list to catch mislabeled entries
- Contradictory entries manually reviewed and re-labeled
- A small allowlist of critical mainstream domains (search engines, banks,
  government, telecom) is excluded so they are never blocked

## Usage

Download the compressed list and decompress it in your app or filter:

    https://github.com/aliirshad3098/guardian-blocklist/releases/download/v1/guardian_blocklist.txt.gz

## Prebuilt Bloom filter binary

Parsing ~4.6 million lines on a mobile device on every first run is slow
(tens of seconds on lower-end hardware). `guardian_blocklist.bin` is a
Bloom filter built from `guardian_blocklist.txt.gz` ahead of time, so a
consuming app can download and load it directly instead of parsing the raw
text itself.

- `guardian_blocklist.bin` — the compiled Bloom filter (binary format:
  magic number + format version + a version string + Bloom filter
  parameters + bit array)
- `manifest.json` — `{"version": "...", "formatVersion": 1, "domainCount": N,
  "builtAt": "..."}`. Consumers should compare `version` against whatever
  they last downloaded and only re-fetch `guardian_blocklist.bin` when it
  changes.

Built with the CLI tool in [`tools/blocklist-builder/`](tools/blocklist-builder/),
and republished automatically via the
[Build Prebuilt Bloom Filter](.github/workflows/build-bloom-filter.yml)
GitHub Actions workflow (run manually from the Actions tab after updating
`guardian_blocklist.txt.gz` on a release).

**Consuming this binary directly is optional.** It's a matched pair with the
exact Bloom filter format Guardian's Android app uses internally
(`DomainBloomFilter.kt`) — other consumers can safely ignore `.bin`/
`manifest.json` and just use `guardian_blocklist.txt.gz` as before.

## Attribution

This blocklist is derived from the **Blacklists UT1** project, maintained by
Fabrice Prigent, Université Toulouse 1 Capitole
(https://dsi.ut-capitole.fr/blacklists/index_en.php).

## License

This work is licensed under **Creative Commons Attribution-ShareAlike (CC BY-SA)**,
in accordance with the license of the source UT1 blacklists. You are free to use,
share, and adapt it, provided you give attribution and license derivative works
under the same terms.
