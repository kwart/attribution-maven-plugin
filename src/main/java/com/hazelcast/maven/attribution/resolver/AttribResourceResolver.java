/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.hazelcast.maven.attribution.resolver;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;

/**
 *
 */
@Named
@Singleton
public final class AttribResourceResolver extends AbstractLogEnabled {

    /**
     * The classifier for sources.
     */
    public static final String SOURCES_CLASSIFIER = "sources";

    @Inject
    private RepositorySystem repoSystem;

    public File resolveSourceFromArtifact(ProjectBuildingRequest projectBuildingRequest, MavenProject project, Artifact a) {
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(a.getGroupId(),
                a.getArtifactId(), SOURCES_CLASSIFIER, "jar", a.getVersion());
        ArtifactRequest req = new ArtifactRequest(artifact, project.getRemoteProjectRepositories(), null);
        try {
            RepositorySystemSession repoSession = projectBuildingRequest.getRepositorySession();
            ArtifactResult resolutionResult = repoSystem.resolveArtifact(repoSession, req);
            Artifact resolvedArtifact = RepositoryUtils.toArtifact(resolutionResult.getArtifact());
            File file = resolvedArtifact.getFile();
            getLogger().debug("Resolved source jar: " + file);
            return file;
        } catch (Exception e) {
            getLogger().info("Resolving failed for " + artifact);
        }
        return null;
    }

}
