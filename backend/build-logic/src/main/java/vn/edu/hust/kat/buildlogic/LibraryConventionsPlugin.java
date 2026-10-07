package vn.edu.hust.kat.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;

public class LibraryConventionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        project.getPluginManager()
                .apply("java-library");

        project.getExtensions()
                .configure(JavaPluginExtension.class, java -> {
                    java.getToolchain()
                            .getLanguageVersion()
                            .set(
                                    org.gradle.jvm.toolchain.JavaLanguageVersion.of(21)
                            );
                });

        project.getRepositories()
                .mavenCentral();
    }
}