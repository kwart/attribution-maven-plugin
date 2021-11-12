package com.hazelcast.maven.attribution;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Generates an aggregated attribution file for a (possibly) multi-module project.
 */
@Mojo(name = "aggregate", defaultPhase = LifecyclePhase.PACKAGE, aggregator = true, inheritByDefault = false,
    requiresDependencyCollection = ResolutionScope.RUNTIME, threadSafe = true)
public class AggregateAttributionMojo extends AbstractAttributionMojo {

    @Override
    protected Map<String, File> resolveSourceJars() {
        Set<String> projectGaSet = new HashSet<>();

        Set<Artifact> artifacts = new HashSet<>();
        final Map<String, MavenProject> projectMap = new HashMap<>();
        if (reactorProjects != null) {
            for (final MavenProject p : reactorProjects) {
                String projectGaKey = gaKey(p.getGroupId(), p.getArtifactId());
                projectGaSet.add(projectGaKey);
                List<Artifact> projectArtifacts = p.getRuntimeArtifacts();
                artifacts.addAll(projectArtifacts);
                getLog().debug("Project " + projectGaKey + " artifacts: " + projectArtifacts);
            }
        } else {
            getLog().info("Null reactorProjects");
        }
        getLog().debug("Project GAs: " + projectGaSet);
        getLog().debug("Artifacts size: " + artifacts.size());

        Map<String, File> result = new HashMap<String, File>();
        for (Artifact artifact : artifacts) {
            String gaKey = gaKey(artifact);
            if (projectGaSet.contains(gaKey)) {
                // don't include (sub)project sources
                getLog().debug("Skipping (sub)project artifact " + gaKey);
                continue;
            }
            String gavKey = gavKey(artifact);
            File sourceFile = resolve(createResourceArtifact(artifact, SOURCES_CLASSIFIER));
            if (sourceFile == null) {
                getLog().debug("No source file resolved for " + gavKey);
                continue;
            }
            getLog().debug("Resolved " + sourceFile);
            result.put(gavKey, sourceFile);
        }
        return result;
    }

}
