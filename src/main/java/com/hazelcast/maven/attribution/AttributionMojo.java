package com.hazelcast.maven.attribution;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Generates the attribution file for a single project.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class AttributionMojo extends AbstractAttributionMojo {

    @Override
    protected Map<String, File> resolveSourceJars() {
        Map<String, File> result = new HashMap<>();
        List<Artifact> artifacts = project.getRuntimeArtifacts();
        for (Artifact artifact : artifacts) {
            String gavKey = gavKey(artifact);
            File sourceFile = resolve(createResourceArtifact(artifact, SOURCES_CLASSIFIER));
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
