# FrozenLib wind state probes

The historical 2.5.3 probe uses an isolated client-only Fabric run for the audited
`FrozenLib-2.5.3-mc26.2.jar`, `WilderWild-4.2.11-mc26.2.jar`, and
`fabric-api-0.160.0+26.2.jar` artifacts. It does not modify production sources or vendor jars.

Place those three jars in `build/wind-smoke/mods/`, then run:

```powershell
.\tools\wind-state-probe\run-probe.ps1 -Mode baseline
```

The baseline run disables the dev `lampas2-overrides` mod through Fabric Loader's
`fabric.debug.disableModIds` property. The historical patched mode is unsupported because the
production wind override has been retired after the latest FrozenLib and Wilder Wild pair fixed the
observed reset and extension-replacement behavior. The baseline compiles against the current
`compileClientJava` classpath plus the audited FrozenLib and Wilder Wild jars, launches an isolated
Loom client, and stops it after the probe callback.
The temporary probe mixin captures the exact cached executor created by FrozenLib's
`CapeUtil.registerCapesFromURL` and gracefully shuts it down with a bounded await before client
shutdown, so the run does not leave the vendor's discarded worker alive.

The first check loads the real Wilder Wild extension type and verifies that it is present in
FrozenLib's extension registry. It seeds `WindManager.INSTANCE` with an actual
`WWWindManagerExtension`, sets the private `loadedExtensions` flag, calls `reset()`, and records
the extension list and flag contract, including rehydration through the real extension registry.
The second check invokes the private upstream `applyFromStreamCodec` on a worker thread with real
extension instances. It then applies the returned object through the real Fabric
`AttachmentChange.tryApply` on the client thread. Because Fabric's target-info interface is sealed,
the probe allocates an isolated transformed `ClientLevel` fixture with `Unsafe` and initializes
only its client-side flag, dimension, and registry access. The fixture is never installed in
Minecraft or ticked, and no world is opened. It applies two decoded packets in sequence, checks
the latest state wins, verifies an `INSTANCE` identity attachment, and verifies a null deletion
clears the attachment. The result is written to
`build/wind-smoke/wind-probe-result.txt`.

This historical probe is a deterministic lifecycle/thread ownership check. It does not claim multiplayer or
full-pack gameplay coverage, and it does not reproduce an actual connected-world attachment
lookup.

## FrozenLib 3.x unpatched observation run

`run-observation.ps1` is a separate observational harness for FrozenLib 3.0. It requires
explicit source paths and SHA-256 values for FrozenLib, Wilder Wild, and the pack's Fabric API.
It checks every hash before copying the artifacts into a new, run-specific `mods/` directory.
That directory contains the three attested runtime artifacts and the temporary probe mod. The
dev `lampas2-overrides` mod is disabled, and this harness does not load the old
`CapeUtilProbeMixin` because FrozenLib 3.0 uses a different cape executor path.

Example command for an unpatched FrozenLib 3.0 + Wilder Wild 4.3 pair with pack Fabric API
0.161.0+26.2:

```powershell
.\tools\wind-state-probe\run-observation.ps1 `
  -RunId frozen3-wilder43 `
  -FrozenLibPath .\build\frozenlib-3.0-candidate.jar `
  -FrozenLibSha256 508093763f014ee25430045153a619383eea697d9b7853b72b097f700ec0a51e `
  -WilderWildPath .\build\wilderwild-4.3-candidate.jar `
  -WilderWildSha256 9569288654cee9becfb075a3379d9c8eb5f4a9b06180d7f1936d5bc530f5368c `
  -FabricApiPath ..\lampas-server-fabric\mods\fabric-api-0.161.0+26.2.jar `
  -FabricApiSha256 e5b858ceb13290c274e31cb888ff5a1a40cb91067e7ffab0b5b775aec51e216a
```

Use a distinct `RunId` for each pair so results remain separate. The observation result captures
reset state, extension rehydration attempts, worker-thread codec behavior, two sequential extension
updates, errors, and the client exit status. An upstream compatibility error such as
`AbstractMethodError` is recorded as an observation; it does not make the probe itself fail. The
runtime result, Gradle log, client logs, and input attestation are retained under
`build/wind-state-probe/<RunId>/`. Synthetic-level rehydration is isolated and does not open or tick
a world.
