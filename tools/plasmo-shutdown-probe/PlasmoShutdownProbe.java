import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import su.plo.voice.client.ModVoiceClient;
import su.plo.voice.client.socket.NettyUdpClient;
import io.netty.channel.EventLoopGroup;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
public class PlasmoShutdownProbe implements ClientModInitializer {
 private static Thread captureThread;
 private static EventLoopGroup group;
 public void onInitializeClient() {
  ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
   try {
    var voice = ModVoiceClient.INSTANCE;
    voice.getAudioCapture().start();
    var captureField = voice.getAudioCapture().getClass().getDeclaredField("thread");
    captureField.setAccessible(true);
    captureThread = (Thread) captureField.get(voice.getAudioCapture());
    var udp = new NettyUdpClient(voice, voice.getConfig(), UUID.randomUUID());
    voice.getUdpClientManager().setClient(udp);
    var groupField = NettyUdpClient.class.getDeclaredField("workGroup");
    groupField.setAccessible(true);
    group = (EventLoopGroup) groupField.get(udp);
    group.submit(() -> System.out.println("PLASMO_PROBE_UDP_THREAD=" + Thread.currentThread().getName() + " daemon=" + Thread.currentThread().isDaemon())).sync();
    System.out.println("PLASMO_PROBE_CAPTURE_ALIVE=" + captureThread.isAlive());
    ClientLifecycleEvents.CLIENT_STOPPING.register(this::verify);
    mc.stop();
   } catch (Exception e) { throw new RuntimeException(e); }
  });
 }
 private void verify(net.minecraft.client.Minecraft mc) {
   if (captureThread == null) return;
   try {
    captureThread.join(3000);
    boolean terminated = group.terminationFuture().await(5, TimeUnit.SECONDS);
    String result = "captureTerminated=" + !captureThread.isAlive() + "\nudpTerminated=" + terminated + "\nmanagerEmpty=" + ModVoiceClient.INSTANCE.getUdpClientManager().getClient().isEmpty() + "\n";
    Files.writeString(Path.of("probe-result.txt"), result);
    System.out.println("PLASMO_PROBE_RESULT " + result);
    if (captureThread.isAlive() || !terminated) throw new AssertionError(result);
   } catch (Exception e) { throw new RuntimeException(e); }
 }
}
