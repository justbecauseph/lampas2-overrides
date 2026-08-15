package lampas2overrides.protocol;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HandshakeC2SPayload(
		String packVersion,
		int protocolVersion,
		String launcherVersion
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<HandshakeC2SPayload> TYPE = new CustomPacketPayload.Type<>(LampasProtocol.HANDSHAKE_C2S_ID);

	public static final StreamCodec<ByteBuf, HandshakeC2SPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, HandshakeC2SPayload::packVersion,
			ByteBufCodecs.VAR_INT, HandshakeC2SPayload::protocolVersion,
			ByteBufCodecs.STRING_UTF8, HandshakeC2SPayload::launcherVersion,
			HandshakeC2SPayload::new
	);

	@Override
	public CustomPacketPayload.Type<HandshakeC2SPayload> type() {
		return TYPE;
	}
}
