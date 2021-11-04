package com.hazelcast.maven.attribution;

import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;

/**
 * Component accessing the artifact resolver to Mojo.
 */
@Component(role = ResolverComponent.class)
public final class ResolverComponent extends AbstractLogEnabled {

    @Requirement
    private ArtifactFactory artifactFactory;

    @Requirement
    private ArtifactResolver resolver;

    public ArtifactResolver getResolver() {
        return resolver;
    }

    public ArtifactFactory getArtifactFactory() {
        return artifactFactory;
    }
}
