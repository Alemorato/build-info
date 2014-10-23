package org.jfrog.build.extractor.maven.resolver;

import org.apache.maven.artifact.DefaultArtifact;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.jfrog.build.extractor.maven.BuildInfoRecorder;
import org.sonatype.aether.AbstractRepositoryListener;
import org.sonatype.aether.RepositoryEvent;
import org.sonatype.aether.RepositoryListener;
import org.sonatype.aether.resolution.ArtifactRequest;

/**
 * Repository listener when running in Maven 3.0.x.
 * The listener performs the following:
 * 1. Enforces artifacts resolution from Artifactory.
 * 2. Updates the BuildInfoRecorder with each resolved artifact.
 * @author Shay Yaakov
 */
@Component(role = RepositoryListener.class)
public class ArtifactorySonatypeRepositoryListener extends AbstractRepositoryListener implements Contextualizable {

    @Requirement
    private Logger logger;

    @Requirement
    private ResolutionHelper resolutionHelper;

    BuildInfoRecorder buildInfoRecorder = null;

    private PlexusContainer plexusContainer;

    private BuildInfoRecorder getBuildInfoRecorder() {
        if (buildInfoRecorder == null) {
            try {
                buildInfoRecorder = (BuildInfoRecorder)plexusContainer.lookup(BuildInfoRecorder.class.getName());
            } catch (ComponentLookupException e) {
                logger.error("Failed while trying to fetch BuildInfoRecorder from the container in " + this.getClass().getName(), e);
            }
            if (buildInfoRecorder == null) {
                logger.error("Could not fetch BuildInfoRecorder from the container in " + this.getClass().getName() + ". Artifacts resolution cannot be recorded.");
            }
        }
        return buildInfoRecorder;
    }

    /**
     * Intercepts resolved artifacts and updates the BuildInfoRecorder, so that build-info includes all resolved artifacts.
     */
    @Override
    public void artifactResolved(RepositoryEvent event) {
        String requestContext = ((ArtifactRequest)event.getTrace().getData()).getRequestContext();
        String scope = resolutionHelper.getScopeByRequestContext(requestContext);
        org.apache.maven.artifact.Artifact artifact = toMavenArtifact(event.getArtifact(), scope);
        if (logger.isDebugEnabled()) {
            logger.debug("[buildinfo] Resolved artifact: " + artifact + " from: " + event.getRepository() + " Context is: " + requestContext);
        }

        if (getBuildInfoRecorder() != null) {
            getBuildInfoRecorder().artifactResolved(artifact);
        }
        super.artifactResolved(event);
    }

    /**
     * Converts org.sonatype.aether.artifact.Artifact objects into org.apache.maven.artifact.Artifact objects.
     */
    private org.apache.maven.artifact.Artifact toMavenArtifact(final org.sonatype.aether.artifact.Artifact art, String scope) {
        if (art == null) {
            return null;
        }
        DefaultArtifact artifact = new DefaultArtifact(art.getGroupId(), art.getArtifactId(), art.getVersion(), scope, art.getExtension(), "", null) {
            public boolean equals( Object o ) {
                if (o == this) {
                    return true;
                }
                if (!(o instanceof org.apache.maven.artifact.Artifact)) {
                    return false;
                }
                return hashCode() == o.hashCode();
            }
        };
        artifact.setFile(art.getFile());
        return artifact;
    }

    @Override
    public void contextualize(Context context) throws ContextException {
        plexusContainer = (PlexusContainer)context.get(PlexusConstants.PLEXUS_KEY);
    }
}
