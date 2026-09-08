# Plasmo Voice shutdown verification — 2026-09-09

Upstream report: https://github.com/plasmoapp/plasmo-voice/issues/539

## Audited artifact and hook

Plasmo Voice 2.1.16, Modrinth version `I0T9OQcy`, file
`plasmovoice-fabric-26.2-2.1.16.jar`, SHA-256:
`ecdc653794303cde801562953cc0b9bbd8c7ecd124b398d6a8c3cee79c904259`.
The resolved Maven artifact and installed Lampas client artifact match exactly.

`javap -p -c` confirmed `BaseVoiceClient.onShutdown()V`, capture `stop()` interrupting
its thread, `VoiceUdpClientManager.removeClient(Reason)` delegating to UDP `close`,
and `NettyUdpClient.close(Reason)` invoking `workGroup.shutdownGracefully()`.
The hook runs at HEAD, before upstream event-bus unregister and addon unload.
It requests active capture stop, then UDP removal with DISCONNECT. Each operation logs
RuntimeException independently so upstream shutdown can continue. No thread-name scan,
forced thread termination, device close from the render thread, or watchdog override.
Upstream capture-stop cancellation is preserved; an addon deliberately cancelling stop
can still prevent cleanup and must be investigated separately.

## Automated checks

`gradlew.bat test build --no-daemon`: passed, 82 tests, zero failures/errors.
Six new tests cover version gating, cleanup order, independent failure handling,
partial initialization, actual upstream capture-thread termination, and the pinned
upstream bytecode cleanup contract. The capture fixture has no microphone/server,
exercising the 1000 ms idle wait path seen in the crash. It checks a real non-daemon
thread and joins it only in the test, never in production cleanup.

## Isolated client probe

Minecraft 26.2, Fabric Loader 0.19.3 (project baseline), Fabric API 0.160.0+26.2,
Plasmo Voice 2.1.16, overrides development classes, and a temporary probe mod.
The probe starts real VoiceAudioCapture and NettyUdpClient workers, registers the
UDP client in the actual manager, and requests normal Minecraft stop. It verifies
termination after Plasmo's lifecycle callback. No multiplayer connection is opened.

Observed on the successful run:

```text
[06:15:00] Mixing BaseVoiceClientMixin from lampas2-overrides.plasmoshutdown.mixins.json into su.plo.voice.client.BaseVoiceClient
[06:15:02] PLASMO_PROBE_UDP_THREAD=plasmo-voice-udp-1-1 daemon=false
[06:15:02] PLASMO_PROBE_CAPTURE_ALIVE=true
[06:15:02] [PlasmoVoice] Disconnecting before connecting with reason DISCONNECT
[06:15:02] [PlasmoVoice] Shutting down
[06:15:04] PLASMO_PROBE_RESULT captureTerminated=true
udpTerminated=true
managerEmpty=true
BUILD SUCCESSFUL in 14s
```

Successful probe exit code: 0. No watchdog crash or injection failure.
Local full log: `build/plasmo-smoke-run.log`; result: `build/plasmo-smoke/probe-result.txt`.
Earlier probe attempts were not product failures: the first used Loom's default run
directory, and the next registered its assertion before Plasmo's stopping callback.
The final probe sets Loom's run directory and registers verification at CLIENT_STARTED,
after all mod initializers have registered shutdown callbacks.

### Reproduce in PowerShell from this repository

Use an isolated directory under build; the probe automatically closes the dev client.

```powershell
New-Item -ItemType Directory -Force build/plasmo-smoke/mods,build/plasmo-smoke-probe/classes | Out-Null
# Place the audited Plasmo Voice JAR and Fabric API 0.160.0+26.2 in build/plasmo-smoke/mods.
.\gradlew.bat -I tools/plasmo-shutdown-probe/smoke.init.gradle plasmoProbeClasspath --no-daemon
$probeClasspath = Get-Content build/plasmo-smoke-probe/classpath.txt -Raw
javac -cp $probeClasspath -d build/plasmo-smoke-probe/classes tools/plasmo-shutdown-probe/PlasmoShutdownProbe.java
'{"schemaVersion":1,"id":"plasmo-shutdown-probe","version":"1.0.0","environment":"client","entrypoints":{"client":["PlasmoShutdownProbe"]},"depends":{"plasmovoice":"2.1.16"}}' | Set-Content build/plasmo-smoke-probe/classes/fabric.mod.json
jar cf build/plasmo-smoke/mods/plasmo-shutdown-probe.jar -C build/plasmo-smoke-probe/classes .
.\gradlew.bat -I tools/plasmo-shutdown-probe/smoke.init.gradle runClient --no-daemon
Get-Content build/plasmo-smoke/probe-result.txt
```

## Remaining live validation

The probe does not exercise a connected voice server, real microphone input, ReplayMod,
Sound Physics addons, or the full pack under Loader 0.19.5. Verify normal voice operation,
disconnect/reconnect, and exit while connected in that environment. Unknown Plasmo versions
remain disabled until audited. Nothing has been staged to the pipeline or deployed.
