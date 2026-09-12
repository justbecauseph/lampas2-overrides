# FrozenLib wind state and Wilder Wild cloud crash

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
