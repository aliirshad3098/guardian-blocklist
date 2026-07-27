# Guardian Blocklist

An adult-content domain blocklist for **Guardian** — a privacy-first, on-device
content filter that helps people overcome compulsive access to adult content.

The list is provided in **hosts format** (`0.0.0.0 domain`), gzip-compressed for
fast mobile downloads.

## Contents

- `guardian_blocklist.txt.gz` — ~4.6 million adult domains (hosts format, gzipped)

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

## Attribution

This blocklist is derived from the **Blacklists UT1** project, maintained by
Fabrice Prigent, Université Toulouse 1 Capitole
(https://dsi.ut-capitole.fr/blacklists/index_en.php).

## License

This work is licensed under **Creative Commons Attribution-ShareAlike (CC BY-SA)**,
in accordance with the license of the source UT1 blacklists. You are free to use,
share, and adapt it, provided you give attribution and license derivative works
under the same terms.
