# Agent-Channels Fork of signal-cli

This fork adds **per-account proxy configuration** on top of upstream
[AsamK/signal-cli](https://github.com/AsamK/signal-cli).

## Why this fork exists

Upstream signal-cli relies on process-global proxy settings (JVM
`ProxySelector` / `HTTPS_PROXY`). When hosting many Signal accounts in a
single daemon, all accounts share the same exit route. This fork adds:

- A persisted `ProxyConfig` field on each account's stored config
  (type `http`/`socks5`, host, port, optional user/pass, template vars)
- New CLI + JSON-RPC commands `setProxy` / `removeProxy`
- Optional `--proxy` flag on `send`, `register`, `verify`, `link`,
  `updateProfile` etc. that overrides the stored proxy for that call
- Per-account HTTP client wiring — each `ManagerImpl` builds its own
  `SignalServiceConfiguration` using its account's stored proxy

The feature is intended to be contributed back to upstream. See the
[open PR](https://github.com/AsamK/signal-cli/pulls) for status.

## Branches

| Branch         | Purpose                                             |
|----------------|-----------------------------------------------------|
| `main`         | Tracks `upstream/main` exactly. Never diverges.     |
| `agentchannels`| Long-lived branch carrying our per-account proxy patches. This is the branch we build releases from. |

## Tags

Our fork releases follow upstream tags with an `-ac.N` suffix:

| Tag                 | Meaning                             |
|---------------------|-------------------------------------|
| `v0.14.2-ac.1`      | First fork release, based on upstream v0.14.2 |
| `v0.14.2-ac.2`      | Second fork release with the same upstream base |
| `v0.14.3-ac.1`      | First release rebased onto upstream v0.14.3 |

## Syncing from upstream

Run `./scripts/sync-upstream.sh`. This:

1. Fetches `upstream`
2. Fast-forwards our `main` to match `upstream/main`
3. Merges `upstream/main` into `agentchannels` — resolve conflicts here
4. Pushes both branches to `origin`

### Expected conflict locations

Based on upstream's velocity, these files are most likely to conflict:

- `lib/src/main/java/org/asamk/signal/manager/storage/SignalAccount.java`
  — our `proxy` field on the `Storage` record. Keep it at the end of
  the field list to minimize conflict-diff size.
- `lib/src/main/java/org/asamk/signal/manager/config/LiveConfig.java`
  and `StagingConfig.java` — our proxy argument to
  `createDefaultServiceConfiguration()`.
- `lib/src/main/java/org/asamk/signal/manager/internal/SignalDependencies.java`
  — our auth credentials on the libsignal `setSignalNetworkProxy` call.
- `src/main/java/org/asamk/signal/commands/Commands.java` — our
  `setProxy` / `removeProxy` registrations.

If the conflict volume ever exceeds ~50 lines, consider re-architecting
our patches to touch fewer upstream files (e.g. pull the proxy logic
into a single new class and inject it via constructor).

## Building

```
./gradlew build                 # runs unit tests
./gradlew nativeCompile         # produces build/native/nativeCompile/signal-cli
```

GraalVM is required for `nativeCompile`. See upstream `README.md` for
toolchain setup.

## Releasing

1. Ensure `agentchannels` is clean and up-to-date with upstream
2. Bump version in `build.gradle.kts` to `0.14.2-ac.N`
3. `git tag v0.14.2-ac.N && git push --tags`
4. CI builds `signal-cli-0.14.2-ac.N-Linux-native.tar.gz` and publishes
   a GitHub Release on this fork
5. Update `docker/signal-cli/Dockerfile` in the AgentChannels repo to
   reference the new version

## Upstream PR

When the upstream PR is open, link it here:

- Status: (not yet opened)
- PR: (pending)

If/when upstream merges the PR, retire the `agentchannels` branch and
point AgentChannels' Dockerfile back at `AsamK/signal-cli` releases.
