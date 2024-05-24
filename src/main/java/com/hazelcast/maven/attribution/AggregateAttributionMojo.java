package com.hazelcast.maven.attribution;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates an aggregated attribution file for a (possibly) multi-module project.
 */
@Mojo(name = "aggregate", defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        aggregator = true, requiresDependencyCollection = ResolutionScope.RUNTIME,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        threadSafe = true)
public class AggregateAttributionMojo extends AbstractAttributionMojo {

    @Override
    protected Map<String, File> resolveSourceJars() {
        Set<String> projectGaSet = new HashSet<>();

        Set<String> artifacts = new HashSet<>();
        Map<String, File> result = new HashMap<String, File>();
        if (reactorProjects != null) {
            for (final MavenProject p : reactorProjects) {
                String projectGaKey = gaKey(p.getGroupId(), p.getArtifactId());
                projectGaSet.add(projectGaKey);
            }
            for (final MavenProject p : reactorProjects) {
                List<Artifact> projectArtifacts = p.getRuntimeArtifacts();
                getLog().debug("Project " + gaKey(p.getGroupId(), p.getArtifactId()) + " artifacts: " + projectArtifacts);

                ProjectBuildingRequest pbr = getProjectBuildingRequest(p);
                for (Artifact artifact : projectArtifacts) {
                    String gaKey = gaKey(artifact);
                    String gavKey = gavKey(artifact);
                    if (artifacts.contains(gavKey)) {
                        getLog().debug("Artifact already resolved " + gavKey);
                        continue;
                    }
                    artifacts.add(gavKey);
                    if (projectGaSet.contains(gaKey)) {
                        // don't include (sub)project sources
                        getLog().debug("Skipping (sub)project artifact " + gaKey);
                        continue;
                    }
                    File sourceFile = resourceResolver.resolveSourceFromArtifact(pbr, p, artifact);
                    if (sourceFile == null) {
                        getLog().debug("No source file resolved for " + gavKey);
                        continue;
                    }
                    getLog().debug("Resolved " + sourceFile);
                    result.put(gavKey, sourceFile);
                }
            }
        } else {
            getLog().info("Null reactorProjects");
        }
        getLog().debug("Project GAs: " + projectGaSet);
        getLog().debug("Artifacts size: " + artifacts.size());
        return result;
    }

}
