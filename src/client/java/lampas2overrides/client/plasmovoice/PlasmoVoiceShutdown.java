package lampas2overrides.client.plasmovoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.capture.AudioCapture;
import su.plo.voice.api.client.connection.UdpClientManager;
import su.plo.voice.api.client.event.socket.UdpClientClosedEvent;

/** Uses upstream cleanup while its event listeners and add-ons still exist. */
public final class PlasmoVoiceShutdown {
    private static final Logger LOGGER = LoggerFactory.getLogger("lampas2-overrides/plasmo-shutdown");

    private PlasmoVoiceShutdown() {}

    public static void cleanup(PlasmoVoiceClient client) {
        // Capture stop interrupts the capture loop; that loop owns encoder cleanup.
        // Do not join on the render thread or close devices underneath capture.
        try {
            AudioCapture capture = client.getAudioCapture();
            if (capture != null && capture.isActive()) {
                capture.stop();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Could not stop Plasmo Voice capture during client shutdown", exception);
        }
        // Always attempt networking cleanup, even if a capture listener throws.
        // NettyUdpClient.close also calls workGroup.shutdownGracefully().
        try {
            UdpClientManager manager = client.getUdpClientManager();
            if (manager != null) {
                manager.removeClient(UdpClientClosedEvent.Reason.DISCONNECT);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Could not close Plasmo Voice UDP client during client shutdown", exception);
        }
    }
}
