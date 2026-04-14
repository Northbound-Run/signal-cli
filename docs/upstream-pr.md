# Upstream PR — Add per-account proxy configuration

Draft for submission to [AsamK/signal-cli](https://github.com/AsamK/signal-cli).

---

## Title

Add per-account proxy configuration (HTTP + SOCKS5)

## Summary

signal-cli currently relies on process-global proxy settings (JVM
`ProxySelector` via `HTTPS_PROXY` / `socksProxyHost` env vars). When
hosting many accounts in a single daemon (common for bridges and
multi-tenant deployments), every account shares the same exit route.

This PR adds per-account proxy configuration:

- `setProxy` / `removeProxy` CLI + JSON-RPC commands persist a proxy
  on the account.
- Optional `--proxy` flag on `send`, `register`, `verify`, `link`,
  `startChangeNumber`, `finishChangeNumber`, `updateProfile` overrides
  the stored proxy for a single call.
- HTTP, HTTPS (transport), and SOCKS5 proxies supported. Authentication
  is now forwarded to the libsignal Rust Network (`setProxy(type, host,
  port, user, pass)` previously passed `null, null`).
- Template placeholders `{COUNTRY}` (random pick from a configured
  list) and `{SESSION}` (random per call) are resolved at connection
  time — useful for residential-proxy rotation patterns.

Accounts with no proxy configured behave exactly as before — this is a
pure additive change.

## Design

### Storage

A new `org.asamk.signal.manager.api.ProxyConfig` record carries `type`
(HTTP | SOCKS5), `host`, `port`, optional `username` / `password`, and
optional `templateVars`. It is persisted as a nullable field at the end
of `SignalAccount.Storage` — existing account files load cleanly
without migration.

### Per-account wiring

`SignalAccountFiles` previously constructed a single
`ServiceEnvironmentConfig` in its constructor. It now retains the
service-environment metadata and exposes
`buildServiceEnvironmentConfig(ProxyConfig)` so `ManagerImpl`,
`RegistrationManagerImpl`, and `ProvisioningManagerImpl` can build an
account-scoped config after loading the account.

`LiveConfig.createDefaultServiceConfiguration` and `StagingConfig`
equivalent now accept `Optional<SignalProxy>` and `Optional<HttpProxy>`
parameters instead of hard-coding `Optional.empty()`. For SOCKS5
accounts only the libsignal Rust Network is plumbed (libsignal's
`HttpProxy` type is HTTP-only); OkHttp continues to respect the JVM
ProxySelector, which typically means no proxy. HTTP accounts get full
coverage on both stacks.

### Hot reconfiguration

`SignalDependencies.reconfigureProxy(ServiceEnvironmentConfig,
ProxyConfig)` closes cached websockets and API clients so they rebuild
against the new proxy. `Manager.setProxy` / `removeProxy` and
`Manager.withProxyOverride` use this path.

### Per-call override

`Manager.withProxyOverride(ProxyConfig, Callable)` synchronizes on the
Manager instance, swaps the effective proxy for the duration of the
callable, then restores. A ThreadLocal slot was considered but rejected
because `SignalDependencies` caches websocket clients across threads —
a ThreadLocal would be invisible to them.

## What's not in this PR

- DBus surface is stubbed (`UnsupportedOperationException`). Happy to
  add in a follow-up once this shape is reviewed.
- `startLink` / `finishLink` don't accept `--proxy` because they span
  two RPC calls through `MultiAccountManager`; tracking as a follow-up.
- Proxy-rotation policy (e.g. "rotate session after N sends") is out
  of scope — `{SESSION}` is resolved per connection today.

## Testing

- ProxyConfig round-trip, validation, `toString()` redaction
- ProxyResolver template expansion (country/session), java.net.Proxy
  mapping, deterministic selection under seeded SecureRandom
- LiveConfigProxyParityTest: no-proxy path is byte-identical to the
  old `Optional.empty()` path
- SetProxyCommand argparse for HTTP and SOCKS5
- ProxyArgumentHelper URL parsing (happy paths, malformed, unsupported
  scheme)
- ProxyOverrideConcurrencyTest: normal/exception restore, LIFO nesting,
  cross-Manager isolation, intra-Manager serialization
- 21 new tests; all existing tests continue to pass

Build: `./gradlew build` ✅
Native-image: `./gradlew nativeCompile` ✅ (reachability metadata
unchanged — new code is reflection-free)

## Backwards compatibility

Zero-change for accounts without a proxy configured. New storage field
is nullable and absent in existing account JSON — Jackson reads
missing keys as null.

## Size

~1100 LOC across 22 files, 6 commits:

1. Add ProxyConfig model and per-account proxy storage field
2. Add ProxyResolver for template placeholder expansion
3. Thread per-account proxy into SignalServiceConfiguration
4. Add setProxy / removeProxy CLI + JSON-RPC commands
5. Add per-call --proxy override to network-touching commands
6. Bump version + docs
