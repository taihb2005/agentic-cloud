package vn.edu.hust.kat.buildlogic;

import io.spring.gradle.dependencymanagement.DependencyManagementPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.springframework.boot.gradle.plugin.SpringBootPlugin;

public class SpringBootConventionsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaConventionsPlugin.class);

        project.getPluginManager().apply(SpringBootPlugin.class);
        project.getPluginManager().apply(DependencyManagementPlugin.class);

        project.getDependencies().add(
                "testImplementation",
                "org.springframework.boot:spring-boot-starter-test"
        );

        project.getDependencies().add(
                "testImplementation",
                "org.springframework.security:spring-security-test"
        );
    }
}