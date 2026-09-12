# FrozenLib wind state probe

This directory contains an isolated client-only Fabric probe for the audited
`FrozenLib-2.5.3-mc26.2.jar`, `WilderWild-4.2.11-mc26.2.jar`, and
`fabric-api-0.160.0+26.2.jar` artifacts. It does not modify production sources or vendor jars.

Place those three jars in `build/wind-smoke/mods/`, then run:

```powershell
.\tools\wind-state-probe\run-probe.ps1 -Mode baseline
.\tools\wind-state-probe\run-probe.ps1 -Mode patched
```

The baseline run disables the dev `lampas2-overrides` mod through Fabric Loader's
`fabric.debug.disableModIds` property. The patched run uses the current dev classes. Both runs
compile against the current `compileClientJava` classpath plus the two audited FrozenLib/Wilder
Wild jars, launch an isolated Loom client, and stop it after the probe callback.
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

The probe is a deterministic lifecycle/thread ownership check. It does not claim multiplayer or
full-pack gameplay coverage, and it does not reproduce an actual connected-world attachment
lookup.
