package lampas2overrides.walljumpelytra;

import io.netty.buffer.ByteBuf;
import lampas2overrides.Lampas2Overrides;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server notification sent when a player presses or holds the Wall-Jump TXF cling key against an adjacent
 * wall while elytra-gliding, so the server exits fall-flying in sync with the client.
 */
public record StopFallFlyingC2SPayload() implements CustomPacketPayload {

	public static final StopFallFlyingC2SPayload INSTANCE = new StopFallFlyingC2SPayload();

	public static final Identifier ID = Identifier.fromNamespaceAndPath(
		Lampas2Overrides.MOD_ID,
		"wall_jump_stop_fall_flying"
	);

	public static final CustomPacketPayload.Type<StopFallFlyingC2SPayload> TYPE = new CustomPacketPayload.Type<>(ID);

	public static final StreamCodec<ByteBuf, StopFallFlyingC2SPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public CustomPacketPayload.Type<StopFallFlyingC2SPayload> type() {
		return TYPE;
	}
}
