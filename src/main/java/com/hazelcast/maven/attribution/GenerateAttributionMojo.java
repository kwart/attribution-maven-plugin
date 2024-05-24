package com.hazelcast.maven.attribution;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * Generates the attribution file for a single project.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
    requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class GenerateAttributionMojo extends AbstractAttributionMojo {

    @Override
    protected Map<String, File> resolveSourceJars() {
        Map<String, File> result = new HashMap<>();
        List<Artifact> artifacts = getRuntimeAndCompileScopedArtifacts(project);
        ProjectBuildingRequest pbr = getProjectBuildingRequest(project);
        Set<String> projectGaSet = getReactorProjectGaSet();
        for (Artifact artifact : artifacts) {
            String gaKey = gaKey(artifact);
            if (projectGaSet.contains(gaKey)) {
                getLog().debug("Skipping reactor project artifact " + gaKey);
                continue;
            }
            String gavKey = gavKey(artifact);
            File sourceFile = resourceResolver.resolveSourceFromArtifact(pbr, project, artifact);
            if (sourceFile == null) {
                getLog().debug("No source file resolved for " + gavKey);
                continue;
            }
            getLog().debug("Resolved " + sourceFile);
            result.put(gavKey, sourceFile);
        }
        return result ;
    }
}
