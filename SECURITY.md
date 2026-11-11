# Security

## Reporting

Do not open a public issue for a working crash or exploit. Use GitHub's private vulnerability
reporting (Security → Report a vulnerability) on this repository.

Please include:
- server software and version, Java version, TCGuard plugin and gate versions,
- what happens (crash, freeze, memory, duplication, bypass),
- the smallest reproduction you have: packets, a script, or the client mod and steps.

## What happens next

- Acknowledged within 3 days.
- A crash or duplication with a reproduction is fixed with priority; the target is a release
  within 7 days.
- The fix ships together with a test that reproduces the attack (see `docs/EXPLOITS.md`).
- Details are published once a fixed release is out. Reporters are credited unless they ask not
  to be.

## Supported versions

Only the latest release gets fixes.
