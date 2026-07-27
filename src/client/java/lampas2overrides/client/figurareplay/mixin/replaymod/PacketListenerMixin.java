package lampas2overrides.client.figurareplay.mixin.replaymod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.netty.channel.ChannelHandlerContext;
import lampas2overrides.client.figurareplay.FiguraReplayBridge;

/**
 * Lands the bridge's queued avatar writes before ReplayMod zips the archive up.
 *
 * <p>Avatar data is serialised on a background thread to keep large avatars from stuttering the
 * game, which leaves a window where the recording ends with writes still in flight. This is the
 * last point at which they can still make it into the file: {@code channelInactive} spawns the
 * thread that saves and closes the replay.
 */
@Mixin(targets = "com.replaymod.recording.packet.PacketListener", remap = false)
public class PacketListenerMixin {

	@Inject(method = "channelInactive(Lio/netty/channel/ChannelHandlerContext;)V", at = @At("HEAD"))
	private void lampas2$flushFiguraAvatars(ChannelHandlerContext ctx, CallbackInfo ci) {
		FiguraReplayBridge.onRecordingStopping(this);
	}
}
