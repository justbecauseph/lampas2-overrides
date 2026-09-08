package lampas2overrides.client.plasmovoice;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.audio.device.DeviceManager;
import su.plo.voice.api.client.connection.UdpClientManager;
import su.plo.voice.api.client.event.socket.UdpClientClosedEvent;
import su.plo.voice.api.event.EventBus;
import su.plo.voice.client.audio.capture.VoiceAudioCapture;

class PlasmoVoiceShutdownTest {
    @Test void pinnedUpstreamStillProvidesTheAuditedCleanupContract() throws Exception {
        var base = readClass("su/plo/voice/client/BaseVoiceClient");
        assertTrue(base.methods.stream().anyMatch(m -> m.name.equals("onShutdown") && m.desc.equals("()V")));
        assertCalls("su/plo/voice/client/audio/capture/VoiceAudioCapture", "stop",
            "java/lang/Thread", "interrupt");
        assertCalls("su/plo/voice/client/connection/VoiceUdpClientManager", "removeClient",
            "su/plo/voice/api/client/socket/UdpClient", "close");
        assertCalls("su/plo/voice/client/socket/NettyUdpClient", "close",
            "io/netty/channel/EventLoopGroup", "shutdownGracefully");
    }

    private static org.objectweb.asm.tree.ClassNode readClass(String name) throws Exception {
        try (var input = PlasmoVoiceShutdownTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input);
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static void assertCalls(String type, String method, String owner, String target) throws Exception {
        var node = readClass(type);
        boolean found = false;
        for (var m : node.methods) {
            if (!m.name.equals(method)) continue;
            for (var instruction : m.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(owner) && call.name.equals(target)) found = true;
            }
        }
        assertTrue(found, type + "." + method + " must call " + owner + "." + target);
    }

    @Test void gateExcludesAbsentAndOtherVersions() {
        assertTrue(PlasmoVoiceShutdownMixinPlugin.supports("2.1.16"));
        for (String version : new String[] {null, "2.1.15", "2.1.17", "2.1.16-SNAPSHOT"}) {
            assertFalse(PlasmoVoiceShutdownMixinPlugin.supports(version));
        }
    }

    @Test void requestsCaptureStopBeforeDisconnect() {
        List<String> calls = new ArrayList<>();
        AudioCapture capture = proxy(AudioCapture.class, (name, args) -> {
            if (name.equals("isActive")) return true;
            if (name.equals("stop")) calls.add("stop");
            return null;
        });
        PlasmoVoiceShutdown.cleanup(client(capture, manager(calls)));
        assertEquals(List.of("stop", "disconnect"), calls);
    }

    @Test void captureFailureDoesNotSkipUdpOrEscapeIntoUpstreamShutdown() {
        List<String> calls = new ArrayList<>();
        AudioCapture capture = proxy(AudioCapture.class, (name, args) -> {
            if (name.equals("isActive")) return true;
            throw new IllegalStateException("simulated capture listener failure");
        });
        assertDoesNotThrow(() -> PlasmoVoiceShutdown.cleanup(client(capture, manager(calls))));
        assertEquals(List.of("disconnect"), calls);
    }

    @Test void handlesPartialInitializationAndUdpFailure() {
        assertDoesNotThrow(() -> PlasmoVoiceShutdown.cleanup(client(null, null)));
        UdpClientManager manager = proxy(UdpClientManager.class, (name, args) -> {
            throw new IllegalStateException("simulated UDP listener failure");
        });
        assertDoesNotThrow(() -> PlasmoVoiceShutdown.cleanup(client(null, manager)));
    }

    @Test void stopsRealUpstreamCaptureThreadWaitingWithoutDevice() throws Exception {
        AtomicReference<AudioCapture> captureRef = new AtomicReference<>();
        DeviceManager devices = proxy(DeviceManager.class, (name, args) -> Optional.empty());
        EventBus events = proxy(EventBus.class, (name, args) -> true);
        PlasmoVoiceClient client = proxy(PlasmoVoiceClient.class, (name, args) -> switch (name) {
            case "getAudioCapture" -> captureRef.get();
            case "getDeviceManager" -> devices;
            case "getEventBus" -> events;
            case "getServerInfo" -> Optional.empty();
            default -> null;
        });
        // No device/server: exercises the same 1000 ms sleep path seen in the crash.
        VoiceAudioCapture capture = new VoiceAudioCapture(client, null);
        captureRef.set(capture);
        capture.start();
        var field = VoiceAudioCapture.class.getDeclaredField("thread");
        field.setAccessible(true);
        Thread thread = (Thread) field.get(capture);
        try {
            assertTrue(thread.isAlive());
            assertFalse(thread.isDaemon());
            PlasmoVoiceShutdown.cleanup(client);
            thread.join(2000);
            assertFalse(thread.isAlive(), "real upstream capture thread must exit");
            assertFalse(capture.isActive());
        } finally {
            thread.interrupt();
            thread.join(2000);
        }
    }

    private static UdpClientManager manager(List<String> calls) {
        return proxy(UdpClientManager.class, (name, args) -> {
            assertEquals("removeClient", name);
            assertEquals(UdpClientClosedEvent.Reason.DISCONNECT, args[0]);
            calls.add("disconnect");
            return null;
        });
    }

    private static PlasmoVoiceClient client(AudioCapture capture, UdpClientManager manager) {
        return proxy(PlasmoVoiceClient.class, (name, args) -> switch (name) {
            case "getAudioCapture" -> capture;
            case "getUdpClientManager" -> manager;
            default -> throw new AssertionError(name);
        });
    }

    @FunctionalInterface private interface Handler { Object call(String name, Object[] args); }
    private static <T> T proxy(Class<T> type, Handler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (instance, method, args) -> handler.call(method.getName(), args)));
    }
}
