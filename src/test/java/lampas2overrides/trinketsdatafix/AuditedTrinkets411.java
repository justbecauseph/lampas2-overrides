package lampas2overrides.trinketsdatafix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TypeTemplate;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarFile;

/** Reflective access to the pinned upstream mixin without compiling against Trinkets. */
final class AuditedTrinkets411 {
	private static final String CLASS_NAME = "eu.pb4.trinkets.mixin.datafixer.V1460Mixin";
	private static final String CLASS_ENTRY = "eu/pb4/trinkets/mixin/datafixer/V1460Mixin.class";

	private AuditedTrinkets411() {
	}

	static synchronized TypeTemplate attach(Schema schema, TypeTemplate vanillaTemplate) throws Exception {
		Class<?> mixin = Class.forName(CLASS_NAME);
		Field schemaField = mixin.getDeclaredField("schema");
		schemaField.setAccessible(true);
		schemaField.set(null, schema);
		Method attach = mixin.getDeclaredMethod("attachTrinketFixer", TypeTemplate.class);
		attach.setAccessible(true);
		return (TypeTemplate) attach.invoke(null, vanillaTemplate);
	}

	static String classSha256() throws Exception {
		Class<?> mixin = Class.forName(CLASS_NAME);
		Path jarPath = Path.of(mixin.getProtectionDomain().getCodeSource().getLocation().toURI());
		try (JarFile jar = new JarFile(jarPath.toFile());
			var in = jar.getInputStream(jar.getJarEntry(CLASS_ENTRY))) {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
		}
	}

	static JsonObject metadata() throws Exception {
		Class<?> mixin = Class.forName(CLASS_NAME);
		Path jarPath = Path.of(mixin.getProtectionDomain().getCodeSource().getLocation().toURI());
		try (JarFile jar = new JarFile(jarPath.toFile());
			var in = jar.getInputStream(jar.getJarEntry("fabric.mod.json"));
			var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
