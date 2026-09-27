# FrozenLib wind state and Wilder Wild cloud crash

The client wind shim documented in the older sections below was retired for
the latest supported FrozenLib 3.0 and Wilder Wild 4.3 pair. Those sections
remain as evidence for the former 2.5.3/4.2.11 artifacts.

## Report and inspected artifacts

The Lampas client report `crash-2026-09-12_17.49.30-client.txt` records an
`IllegalStateException: WWWindManagerExtension was not registered for level ClientLevel`.
The render thread calls `WWWindManagerExtension.getCloudX(Level, float)` through
Wilder Wild's cloud-position hook. The client was in the Overworld on a multiplayer
server. Cloud movement was enabled.

Installed artifacts inspected with Java 25 `javap -p -c -s`:

| Artifact | Metadata version | SHA-256 |
| --- | --- | --- |
| FrozenLib | `2.5.3-mc26.2` | `510bce30b1d7fdc869eccab3cd2ffd49609ec81c29b40f30da5d9d984f73338e` |
| Wilder Wild | `4.2.11-mc26.2` | `dd1111aa5f9eecf95cef3e42c099df0726ef3089cb48064723e286612bd62957` |

These match the pipeline's locked artifacts, Modrinth versions `Ak4sHFuc` and
`TVkz6cZh`, respectively. The client uses Fabric API `0.160.0+26.2`.

## Lifecycle defect

FrozenLib uses a static client `WindManager.INSTANCE`. Its `reset()V` clears
`extensions` but retains `loadedExtensions=true`. The next `getOrCreate(Level)`
calls `tryCreateAndSortExtensions(Level)`, which immediately returns when that
flag is true. Client level-change and disconnect callbacks both call `reset()`.
This leaves registry reconstruction disabled after a reset.

Reset also clears the seed, and Wilder Wild skips wind-driven clouds while the
seed is absent. A normal full sync can repopulate the extension before restoring
the seed. The flag omission is therefore not, by itself, proof of this cloud
crash. During a periodic update the seed remains present while the extension is
removed and replaced, which exposes the failing cloud getter.

The client-only repair resets the flag at the end of the upstream reset method,
so the normal registry-driven initialization can run again.

## Incoming synchronization

`WindManager.applyFromStreamCodec` writes directly to the static client manager.
For extensions without `supportsApplicationFromSync`, it removes the old object
before adding the decoded replacement. Wilder Wild inherits that interface's
default `false` implementation. Concurrent rendering could observe the missing
entry during this replacement. The installed Minecraft `PacketDecoder` invokes
the stream codec from Netty decoding. Fabric's attachment receiver applies the
result later, on the client's packet-processing thread. The singleton mutation
therefore precedes that thread handoff.

The repair decodes into a detached manager and applies the original upstream
merge at Fabric attachment application, after the target resolves successfully.
It preserves the singleton attachment identity and the original merge policy.
It does not schedule work independently from packet application or change the
cloud getter's exception handling.

## Causality and validation boundaries

The supplied log shows a connection to `127.0.0.1:25576` at 17:28:38, normal
render activity afterward, and the crash at 17:49:30. The last resource reload
was several minutes earlier. This establishes the observed failure and the
installed code defects; it does not record the exact instruction interleaving
that caused this particular frame to fail. The mid-session timing favors the
packet replacement race as the immediate trigger; the reset flag defect is an
independently confirmed lifecycle problem.

Regression probe sources and instructions live in `tools/wind-state-probe`.
The isolated Fabric client runs use the exact installed FrozenLib/Wilder Wild
and Fabric API JARs. Baseline mode disables the overrides mod through Fabric
Loader's debug mod filter; it does not change the vendor JARs.

The first baseline and patched comparisons established:

| Check | Upstream baseline | Patched |
| --- | --- | --- |
| Initialized flag after reset | Remains true | False |
| Registry reconstruction after reset | Not exercised; stale flag observed | One WW extension recreated |
| Decoder returns live singleton | Yes | No |
| Worker decode leaves singleton unchanged | No | Yes |
| Client attachment stores singleton identity | Yes | Yes |

Both wind mixins applied without injection errors in the patched client.
The fixture allocates a synthetic ClientLevel with only the registry, dimension,
and client-side fields needed for these calls. It never installs that fixture in
Minecraft or ticks/renders it. This tests actual wind and attachment methods but
does not substitute for a connected world.

The first runs passed the wind assertions but hit a separate post-main shutdown
watchdog due to a cached executor left running. Their assertion results are not
claimed as clean process-exit evidence. The final harness captures the exact
executor created by `CapeUtil.registerCapesFromURL` and requests graceful shutdown
after its assertions. This instrumentation exists only in the probe mod.

Both final comparison runs passed and exited normally with code **0** (baseline
26 seconds, patched 29 seconds). Each captured one cape executor and confirmed
its termination. The final patched run also verified packet ordering, replay of
the live singleton, and null attachment deletion. Unknown-target handling was
reviewed in the upstream bytecode; it was not exercised with a connected world.
Results are in [baseline](evidence/wind-state-baseline.txt),
[patched](evidence/wind-state-patched.txt), and the
[artifact and verification record](evidence/wind-state-artifacts.json).

`gradlew.bat test build --no-daemon` passed with **89 tests, zero failures and
zero errors** in `build/wind-verify`, an isolated checkout of `11240f4` with only
the wind change copied in. This excludes unrelated boat-rendering work being
edited concurrently in the main checkout. The wind source and gate test hashes
were compared with the main checkout after verification and matched. The tests
exercise absent attachment modules, module-version mismatches, target hash
mismatches, and version near-misses. The gate requires the inspected attachment
module `2.2.19+515ac5339e` as well as its target class fingerprint.

The isolated output JAR is `build/wind-verify/build/libs/lampas2-overrides-1.0.0.jar`,
SHA-256 `c58baa8f064fbbf5953ec11df875edb329ceb87eefb710af0c15e8d2135c9291`.
It includes the wind compatibility classes and no bundled FrozenLib/Wilder Wild
classes, probe instrumentation, or unrelated boat feature. Independent production
review passed after the module metadata gate was added.

Full-pack reconnects, dimension changes, resource reloads, and rendered cloud
behavior require live gameplay validation. This change has not been staged to
the pipeline or deployed.

## FrozenLib 3.0 and Wilder Wild 4.3 candidate (2026-09-27)

The next supported pair has different extension and reset behavior. The exact
Modrinth candidates are FrozenLib `dAbWrFf4` (metadata `3.0`, JAR SHA-256
`508093763f014ee25430045153a619383eea697d9b7853b72b097f700ec0a51e`)
and Wilder Wild `jO1Bwxn3` (metadata `4.3`, JAR SHA-256
`9569288654cee9becfb075a3379d9c8eb5f4a9b06180d7f1936d5bc530f5368c`).
Their inspected `WindManager.class` and `WWWindManagerExtension.class` SHA-256
values are `8d5f13c0e9600017495beacd1e0103dd5bda65eafcbc2fcf57a99bcecd208cf5`
and `319392b0baa832a638f9e4cbbf6fa9f421fc7bd6c23b88b546f61bac096a37eb`.
The pack's Fabric API `0.161.0+26.2` JAR SHA-256 is
`e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a`;
its Data Attachment API module is `2.2.19+515ac5339e` with the same inspected
`AttachmentChange.class` SHA-256 as the older profile.

The unpatched isolated client probe ran with exactly these three input JARs
and exited normally. `reset()` cleared both the extension list and
`loadedExtensions`; `getOrCreate` then rehydrated one Wilder Wild extension.
Two worker-thread stream decodes each returned `WindManager.INSTANCE` and
changed its wind values and Wilder Wild cloud coordinates before attachment
application. Wilder Wild 4.3 retained the extension object and list identity
while applying each cloud update in place. The 2.5.3 reset repair and the
missing-extension replacement repair are no longer indicated for this pair.
Worker decode still mutates the singleton; this isolated probe does not show
whether that residual race matters during connected gameplay. The initial
result and logs are under the ignored `build/wind-state-probe/frozen3-wilder43/`
fixture. A fresh baseline after retiring the shim, using the baseline-only
probe source and a new fixture, repeated these observations and exited 0. Its
result SHA-256 is `c14da59bbaf9540afade2930a1cc0b553cebce6fa0d903af4b1ffa1ed4edc45a`;
the input attestation SHA-256 is
`3afb39cc92c9e13932c41771ba4d330c87b7211f82de5ee41e796c8adbff1d7b`.
The retained fixture is `build/wind-state-probe/frozen3-wilder43-retired-baseline/`.

FrozenLib 3.0 with Wilder Wild 4.2.11 is an incompatible pair. An isolated
three-JAR fixture exited 1 during startup: `WilderWildMixinPlugin.onLoad`
raised `NoClassDefFoundError: net/frozenblock/lib/config/api/instance/Config`.
The probe entrypoint did not run. Its console log and attestation are under
`build/wind-state-probe/frozen3-wilder4211-negative/`. This startup failure
does not establish how the pair would behave after additional dependencies.
The mixed pair is not a supported deployment target.

The production `frozenwind` mixin and its old exact gate were removed rather
than extended to 3.0/4.3. This decision is limited to the original cloud
extension failure: an isolated synthetic client probe is not a multiplayer,
reconnect, dimension-change, or rendered-cloud test. If a new wind failure is
reported, collect the exact installed artifacts and a runtime trace before
adding another override.

The fresh first-boot fixture logged FrozenLib errors while looking up several
Wilder Wild and FrozenLib config entries; JSON5 config files were present by
the end of the run. The client reached the menu and exited normally. The
effect of those startup errors on gameplay has not been checked and is separate
from the retired wind repair.

After removal, `gradlew.bat test --no-daemon` passed 101 tests with zero
failures, errors, or skips; `gradlew.bat build --no-daemon` and `git diff --check`
passed. The built override JAR SHA-256 is
`4962f4f2c29554c91849feb209a0399e073c4c850ae73bc881c2a9e9e0698672`;
it contains no `frozenwind` class or mixin config. Independent deletion review
found no dangling production references.
