package vn.edu.hust.kat.buildlogic;

import com.google.protobuf.gradle.ProtobufExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;

/**
 * Convention plugin that configures the Protobuf / gRPC code-generation
 * pipeline for modules that compile {@code .proto} files.
 *
 * <p>Applies:
 * <ul>
 *   <li>{@code java} plugin</li>
 *   <li>{@link JavaConventionsPlugin} (toolchain, repos, JUnit)</li>
 *   <li>{@code com.google.protobuf} plugin</li>
 * </ul>
 *
 * <p>Adds compile-time dependencies: grpc-protobuf, grpc-stub,
 * protobuf-java, jakarta.annotation-api.
 *
 * <p>All versions are read from the {@code libs} version catalog
 * ({@code gradle/libs.versions.toml}).
 */
public class ProtobufConventionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        // ── prerequisite plugins ────────────────────────────────────────
        project.getPluginManager().apply(JavaConventionsPlugin.class);
        project.getPluginManager().apply("com.google.protobuf");

        // ── resolve version catalog ────────────────────────────────────
        VersionCatalogsExtension catalogs =
                project.getExtensions().getByType(VersionCatalogsExtension.class);
        VersionCatalog libs = catalogs.named("libs");

        String protobufVersion = libs.findVersion("protobuf")
                .orElseThrow(() -> new IllegalStateException("Version 'protobuf' not found in catalog"))
                .toString();

        String grpcVersion = libs.findVersion("grpc")
                .orElseThrow(() -> new IllegalStateException("Version 'grpc' not found in catalog"))
                .toString();

        // ── dependencies ───────────────────────────────────────────────
        libs.findLibrary("grpc-protobuf").ifPresent(lib ->
                project.getDependencies().add("implementation", lib));

        libs.findLibrary("grpc-stub").ifPresent(lib ->
                project.getDependencies().add("implementation", lib));

        libs.findLibrary("protobuf-java").ifPresent(lib ->
                project.getDependencies().add("implementation", lib));

        libs.findLibrary("jakarta-annotation").ifPresent(lib ->
                project.getDependencies().add("compileOnly", lib));

        // ── protobuf extension ─────────────────────────────────────────
        ProtobufExtension protobuf =
                project.getExtensions().getByType(ProtobufExtension.class);

        // protoc compiler
        protobuf.protoc(protoc ->
                protoc.setArtifact("com.google.protobuf:protoc:" + protobufVersion)
        );

        // gRPC codegen plugin
        protobuf.plugins(plugins ->
                plugins.create("grpc", grpc ->
                        grpc.setArtifact("io.grpc:protoc-gen-grpc-java:" + grpcVersion)
                )
        );

        // wire the grpc plugin into every proto-compilation task
        protobuf.generateProtoTasks(taskCollection ->
                taskCollection.all().forEach(task ->
                        task.plugins(plugins ->
                                plugins.create("grpc", grpc -> {
                                    grpc.option("@generated=omit");
                                    grpc.option("jakarta_omit");
                                })
                        )
                )
        );
    }
}
