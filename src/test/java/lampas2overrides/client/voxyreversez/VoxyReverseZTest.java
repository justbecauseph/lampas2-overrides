package lampas2overrides.client.voxyreversez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class VoxyReverseZTest {

	private static final float FOV = (float) Math.toRadians(70);
	private static final float ASPECT = 16f / 9f;
	private static final float NEAR = 16f;
	private static final float FAR = 16 * 3000;

	/** A pack's {@code voxy_opaque.glsl} shape: it is appended to Voxy's shader and has no {@code #version}. */
	private static final String PATCH = """
		layout(location = 0) out vec4 gcolor;
		void voxy_emitFragment(VoxyFragmentParameters parameters) {
		    float lodDepth = texelFetch(vxDepthTexOpaque, ivec2(gl_FragCoord.xy), 0).r;
		    float fragDepth = gl_FragCoord.z;
		    vec4 albedo = texture(gtexture, parameters.uv);
		    gcolor = albedo * lodDepth * fragDepth;
		    gl_FragDepth = fragDepth;
		}
		""";

	@Test
	void irisDepthTransformFlipsLodDepthReadsFragCoordAndFragDepth() {
		String transformed = VoxyReverseZ.transformFragmentDepth(PATCH, true).replaceAll("\\s+", "");

		assertTrue(transformed.contains("vec4(1.0f-texelFetch(vxDepthTexOpaque,"), transformed);
		assertTrue(transformed.contains("fragDepth=vec4(gl_FragCoord.xy,1.0f-gl_FragCoord.z,gl_FragCoord.w).z"), transformed);
		assertTrue(transformed.contains("gl_FragDepth=1.0f-(fragDepth)"), transformed);
		assertTrue(transformed.contains("texture(gtexture,parameters.uv)"), transformed);
		assertFalse(transformed.contains("1.0f-texture(gtexture"), transformed);
		assertFalse(transformed.contains("#version"), transformed);
	}

	@Test
	void zeroToOneReverseProjectionBecomesTheForwardOpenGlProjection() {
		// Voxy builds its reverse-Z projection by swapping near and far in the zero-to-one formula, as JOML does.
		Matrix4f reverse = new Matrix4f().perspective(FOV, ASPECT, FAR, NEAR, true);
		Matrix4f forward = new Matrix4f().perspective(FOV, ASPECT, NEAR, FAR, false);

		assertMatrixEquals(forward, VoxyReverseZ.toForwardZ(reverse, true));
	}

	@Test
	void minusOneToOneReverseProjectionBecomesTheForwardOpenGlProjection() {
		Matrix4f reverse = new Matrix4f().perspective(FOV, ASPECT, FAR, NEAR, false);
		Matrix4f forward = new Matrix4f().perspective(FOV, ASPECT, NEAR, FAR, false);

		assertMatrixEquals(forward, VoxyReverseZ.toForwardZ(reverse, false));
	}

	@Test
	void convertedViewProjectionMapsTheNearPlaneToMinusOneAndTheFarPlaneToOne() {
		Matrix4f view = new Matrix4f().rotateY(0.4f).translate(-3, -70, 12);
		Matrix4f reverse = new Matrix4f().perspective(FOV, ASPECT, FAR, NEAR, true).mul(view);
		Matrix4f forward = VoxyReverseZ.toForwardZ(new Matrix4f(reverse), true);
		Matrix4f inverseView = view.invert(new Matrix4f());

		assertEquals(-1f, ndcDepth(forward, inverseView.transform(new Vector4f(0, 0, -NEAR, 1))), 1e-4f);
		assertEquals(1f, ndcDepth(forward, inverseView.transform(new Vector4f(0, 0, -FAR, 1))), 1e-4f);
		assertEquals(1f, ndcDepth(reverse, inverseView.transform(new Vector4f(0, 0, -NEAR, 1))), 1e-4f);
	}

	private static float ndcDepth(Matrix4f projection, Vector4f worldPoint) {
		Vector4f clip = projection.transform(new Vector4f(worldPoint));
		return clip.z / clip.w;
	}

	private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
		for (int column = 0; column < 4; column++) {
			for (int row = 0; row < 4; row++) {
				float tolerance = Math.max(1e-5f, Math.abs(expected.get(column, row)) * 1e-5f);
				assertEquals(expected.get(column, row), actual.get(column, row), tolerance,
					"m" + column + row + " expected " + expected + " got " + actual);
			}
		}
	}
}
