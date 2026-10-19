package lampas2overrides.client.voxyreversez;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.systems.RenderSystem;

import org.antlr.v4.runtime.Token;

import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import lampas2overrides.Lampas2Overrides;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.transformer.DepthTransformer;

/**
 * Keeps Voxy's LOD depth reverse-Z under Iris 1.11.4 and presents it to shader packs as forward-Z, the way Iris does
 * for its own depth textures and matrices. Only the mixins call this, so it loads only when the gate has passed.
 */
public final class VoxyReverseZ {

	private static final Logger LOGGER = LoggerFactory.getLogger(Lampas2Overrides.MOD_ID + "/voxy-reverse-z");

	/** Voxy 0.2.19-beta's fixed far plane, from {@code VoxyRenderSystem.computeProjectionMat}. */
	private static final float FAR_PLANE = 16 * 3000;

	private VoxyReverseZ() {
	}

	/**
	 * Runs a shader pack's {@code voxy_opaque}/{@code voxy_translucent} fragment patch through Iris's own fragment
	 * depth transform: depth-sampler reads (Iris lists {@code vxDepthTexOpaque} and {@code vxDepthTexTrans} among
	 * them), {@code gl_FragCoord.z} and {@code gl_FragDepth} writes become forward-Z for the pack's code.
	 */
	public static String patchForReverseZ(String source) {
		if (source == null) {
			return null;
		}
		try {
			return transformFragmentDepth(source, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
		} catch (RuntimeException exception) {
			LOGGER.error("Could not apply Iris's reverse-Z depth transform to the shader pack's Voxy patch", exception);
			throw exception;
		}
	}

	/**
	 * Configured like Iris's {@code TransformPatcher}: the source provider has already run the preprocessor, so a
	 * leftover directive is an error instead of being dropped, and the output keeps one statement per line so driver
	 * compile errors in the pack's code stay locatable.
	 */
	static String transformFragmentDepth(String source, boolean zeroToOne) {
		SingleASTTransformer<JobParameters> transformer = new SingleASTTransformer<>();
		transformer.setPrintType(PrintType.SIMPLE);
		transformer.setTokenFilter(new ChannelFilter<>(TokenChannel.PREPROCESSOR) {
			@Override
			public boolean isTokenAllowed(Token token) {
				if (!super.isTokenAllowed(token)) {
					throw new IllegalArgumentException("Unparsed preprocessor directive in the Voxy shader patch: "
						+ token.getText());
				}
				return true;
			}
		});
		transformer.setTransformation((tree, root) ->
			DepthTransformer.transform(transformer, tree, root, PatchShaderType.FRAGMENT, false, zeroToOne));
		return transformer.transform(source);
	}

	/**
	 * Converts a reverse-Z projection to the forward-Z, -1..1 clip range that Iris gives packs: Iris's
	 * {@code iris_undoRevZ} sets row 2 to {@code row3 - 2 * row2} for zero-to-one depth and negates it otherwise.
	 */
	public static Matrix4f toForwardZ(Matrix4f matrix, boolean zeroToOne) {
		Vector4f row = matrix.getRow(2, new Vector4f());
		if (zeroToOne) {
			row.mul(-2.0f).add(matrix.getRow(3, new Vector4f()));
		} else {
			row.mul(-1.0f);
		}
		return matrix.setRow(2, row);
	}

	/** The near plane {@code VoxyRenderSystem.computeProjectionMat} uses this frame. */
	public static float nearPlane() {
		if (VoxyClient.disableSodiumChunkRender()) {
			return 0.1f;
		}
		return VoxyRenderSystem.getVanillaRenderDistance() <= 32.0f ? 8f : 16f;
	}

	public static float farPlane() {
		return FAR_PLANE;
	}
}
