package dev.fabricamper.intellij;

import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.service.project.GradleAutoReloadSettingsCollector;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;

public final class CollectorTest {
    private static int passed;
    private static final String SETTINGS = """
            pluginManagement {
                repositories { gradlePluginPortal() }
            }
            plugins {
                id("dev.fabric-amper.settings") version "0.3.1"
            }
            """;

    public static void main(String[] args) throws Exception {
        Path base = Files.createTempDirectory("fabric-amper-collector-test-");
        try {
            Path root = Files.createDirectories(base.resolve("Fabric Amper Project"));
            Path settings = root.resolve("settings.gradle.kts");
            Path yaml = root.resolve("mod.yaml");
            Path yml = root.resolve("mod.yml");
            List<Path> expected = List.of(yaml, yml);
            FabricAmperSettingsCollector collector = new FabricAmperSettingsCollector();

            check("missing settings", collect(root).isEmpty());
            Files.writeString(settings, "plugins { id(\"java\") }");
            Files.writeString(yaml, "schema: \"1.1\"");
            check("ordinary Gradle root is ignored", collect(root).isEmpty());
            Files.writeString(settings, "// id(\"dev.fabric-amper.settings\")\n");
            check("comment-only marker is ignored", collect(root).isEmpty());
            Files.writeString(settings, "/* id(\"dev.fabric-amper.settings\") */");
            check("block comment marker is ignored", collect(root).isEmpty());
            Files.writeString(settings, SETTINGS);
            check("root mod.yaml and missing alias registered", collect(root).equals(expected));
            Files.move(yaml, yml);
            check("alias mod.yml registered", collect(root).equals(expected));
            Files.delete(yml);
            check("creation/deletion paths remain registered", collect(root).equals(expected));
            Path child = Files.createDirectories(root.resolve("nested"));
            Files.writeString(child.resolve("mod.yaml"), "ignored: true");
            check("nested file excluded", !collect(root).contains(child.resolve("mod.yaml")));
            check("linked nested root without Fabric Amper ignored", collect(child).isEmpty());
            check("invalid path ignored", FabricAmperSettingsCollector.collectRootFiles("\u0000").isEmpty());
            check("null path ignored", FabricAmperSettingsCollector.collectRootFiles(null).isEmpty());
            // Real SDK types, not a replacement Gradle API.
            GradleProjectSettings linked = new GradleProjectSettings();
            linked.setExternalProjectPath(root.toString());
            Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(),
                    new Class<?>[]{Project.class}, (proxy, method, values) -> {
                        throw new AssertionError("Unexpected Project call: " + method.getName());
                    });
            GradleAutoReloadSettingsCollector extension = collector;
            check("real 263 collector interface invocation", extension.collectSettingsFiles(project, linked).equals(expected));
            check("real SDK extension-point name", GradleAutoReloadSettingsCollector.EP_NAME.getName()
                    .equals("org.jetbrains.plugins.gradle.autoReloadSettingsCollector"));
            verifyDescriptors(Path.of(args[0]));
            System.out.println("PASS: " + passed + " assertions against installed IntelliJ 263 SDK");
        } finally {
            try (var paths = Files.walk(base)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static List<Path> collect(Path path) {
        return FabricAmperSettingsCollector.collectRootFiles(path.toString());
    }

    private static void verifyDescriptors(Path sdk) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream stream = FabricAmperSettingsCollector.class.getResourceAsStream("/META-INF/plugin.xml")) {
            // Use the project's descriptor directly to avoid resource collisions with IDE jars.
            var doc = factory.newDocumentBuilder().parse(Path.of("src/main/resources/META-INF/plugin.xml").toFile());
            var extensions = doc.getElementsByTagName("autoReloadSettingsCollector");
            check("plugin descriptor binds collector", extensions.getLength() == 1
                    && extensions.item(0).getAttributes().getNamedItem("implementation").getNodeValue()
                    .equals(FabricAmperSettingsCollector.class.getName()));
            var deps = doc.getElementsByTagName("depends");
            boolean gradle = false;
            for (int i = 0; i < deps.getLength(); i++) {
                gradle |= deps.item(i).getTextContent().trim().equals("com.intellij.gradle");
            }
            check("plugin requires 263 Gradle plugin", gradle);
        }
        Path gradleJar = sdk.resolve("plugins/gradle-plugin/lib/gradle-plugin.jar");
        try (ZipFile jar = new ZipFile(gradleJar.toFile())) {
            var doc = factory.newDocumentBuilder().parse(jar.getInputStream(jar.getEntry("META-INF/plugin.xml")));
            check("installed Gradle plugin ID", doc.getElementsByTagName("id").item(0)
                    .getTextContent().equals("com.intellij.gradle"));
        }
        try (ZipFile jar = new ZipFile(sdk.resolve("plugins/gradle-plugin/lib/intellij.gradle.jar").toFile())) {
            var doc = factory.newDocumentBuilder().parse(jar.getInputStream(jar.getEntry("intellij.gradle.xml")));
            var eps = doc.getElementsByTagName("extensionPoint");
            boolean found = false;
            for (int i = 0; i < eps.getLength(); i++) {
                var attrs = eps.item(i).getAttributes();
                var name = attrs.getNamedItem("qualifiedName");
                if (name != null && name.getNodeValue().equals("org.jetbrains.plugins.gradle.autoReloadSettingsCollector")) {
                    var api = attrs.getNamedItem("interface");
                    found = api != null && api.getNodeValue().equals(GradleAutoReloadSettingsCollector.class.getName());
                }
            }
            check("installed Gradle descriptor declares matching extension", found);
        }
    }

    private static void check(String name, boolean success) {
        if (!success) throw new AssertionError(name);
        passed++;
        System.out.println("PASS: " + name);
    }
}
