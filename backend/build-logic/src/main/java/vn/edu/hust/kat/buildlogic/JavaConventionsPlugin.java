package vn.edu.hust.kat.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.testing.Test;

public class JavaConventionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {

        project.getPluginManager().apply("java");

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

        project.getTasks()
                .withType(Test.class)
                .configureEach(test ->
                        test.useJUnitPlatform()
                );
    }
}