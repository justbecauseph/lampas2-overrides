package lampas2overrides.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandshakeS2CPayload(
		boolean accepted,
		String serverPackVersion,
		int serverProtocolVersion,
		String message
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<HandshakeS2CPayload> TYPE = new CustomPacketPayload.Type<>(LampasProtocol.HANDSHAKE_S2C_ID);

	public static final StreamCodec<ByteBuf, HandshakeS2CPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, HandshakeS2CPayload::accepted,
			ByteBufCodecs.STRING_UTF8, HandshakeS2CPayload::serverPackVersion,
			ByteBufCodecs.VAR_INT, HandshakeS2CPayload::serverProtocolVersion,
			ByteBufCodecs.STRING_UTF8, HandshakeS2CPayload::message,
			HandshakeS2CPayload::new
	);

	@Override
	public CustomPacketPayload.Type<HandshakeS2CPayload> type() {
		return TYPE;
	}
}
