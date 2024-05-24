package com.hazelcast.maven.attribution;

import com.hazelcast.maven.attribution.resolver.AttribResourceResolver;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class AbstractAttributionMojo extends AbstractMojo {

    protected static final String DEFAULT_COPYRIGHT_PATTERN = "(?i)^([\\s/*]*)(((\\(c\\))|(copyright))\\s+\\S[^;{}]*)$";
    protected static final int DEFAULT_COPYRIGHT_PATTERN_GRPIDX = 2;

    /**
     * The classifier for sources.
     */
    protected static final String SOURCES_CLASSIFIER = "sources";

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "reactorProjects", readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
     * Specifies whether the attribution file generation should be skipped.
     */
    @Parameter(property = "attribution.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * Configures the parallelism level. By default the number of available processors is used. Parallelism says (1) how many
     * new threads is used for reading source archives and also (2) how many new threads is used for search the copyright
     * notices.
     */
    @Parameter(property = "attribution.parallelism", defaultValue = "0")
    protected int parallelism;

    /**
     * Customizes the pattern for finding the "attribution lines".
     *
     * @see #copyrightPatternGroupIndex
     * @see #DEFAULT_COPYRIGHT_PATTERN
     */
    @Parameter(property = "attribution.copyrightPattern", defaultValue = "")
    protected volatile String copyrightPattern;

    /**
     * When the {@link #copyrightPattern} is configured, then this parameter allows to specify which capture group is used. By
     * default the whole pattern is used (group==0) when custom pattern is configured.
     *
     * @see #copyrightPattern
     * @see #DEFAULT_COPYRIGHT_PATTERN_GRPIDX
     */
    @Parameter(property = "attribution.copyrightPatternGroupIndex", defaultValue = "0")
    protected volatile int copyrightPatternGroupIndex;

    /**
     * Maximal wait time for finishing reading source JARs and searching for patterns in the found source files.
     */
    @Parameter(property = "attribution.serviceTimeoutMinutes", defaultValue = "60")
    protected int serviceTimeoutMinutes;

    /**
     * Specifies the destination attribution file.
     */
    @Parameter(property = "attribution.outputFile", defaultValue = "${project.build.directory}/attribution.txt", required = true)
    protected File outputFile;

    /**
     * Parameter which can specify a file in which exclusion patterns are listed. File should be in UTF-8 with a one pattern per
     * line.
     *
     * @see #exclusionPatterns
     */
    @Parameter(property = "attribution.exclusionPatternsFile")
    protected File exclusionPatternsFile;

    /**
     * Specifies copyright exclusion patterns.
     *
     * @see #exclusionPatternsFile
     */
    @Parameter
    protected List<String> exclusionPatterns;

    @Component
    protected AttribResourceResolver resourceResolver;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping the plugin execution");
            // try to create the output file even when the plugin execution is skipped
            if (outputFile != null && !outputFile.exists()) {
                try {
                    getLog().debug("Create the output attribution file even when the execution is skipped");
                    ensureOutputFileParentDirCreated();
                    Files.createFile(outputFile.toPath());
                } catch (IOException e) {
                    getLog().warn("Outputfile creation failed", e);
                }
            }
            return;
        }

        if (outputFile == null) {
            throw new MojoFailureException("The outputFile has to be configured");
        }

        if (outputFile.exists()) {
            try {
                FileUtils.forceDelete(outputFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to remove the outputFile " + outputFile, e);
            }
        }

        final AttributionContext context = new AttributionContext();
        prepareExclusionPatterns(context);

        Map<String, File> sourceJars = resolveSourceJars();

        int threads = parallelism > 0 ? parallelism : Runtime.getRuntime().availableProcessors();
        ExecutorService jarReaderService = Executors.newFixedThreadPool(threads);
        for (Map.Entry<String, File> entry : sourceJars.entrySet()) {
            jarReaderService.submit(() -> readJar(entry.getKey(), entry.getValue(), context));
        }
        ExecutorService consumerExecutorService = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            consumerExecutorService.submit(() -> consumeSrc(context));
        }

        consumerExecutorService.shutdown();
        jarReaderService.shutdown();
        try {
            jarReaderService.awaitTermination(serviceTimeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            getLog().error(e);
            throw new MojoFailureException("JAR files processing has timed out", e);
        }
        context.producersRunning.set(false);
        try {
            consumerExecutorService.awaitTermination(serviceTimeoutMinutes, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            getLog().error(e);
            throw new MojoFailureException("Source files processing has timed out", e);
        }

        if (context.foundAttribution.isEmpty()) {
            getLog().info("No attribution found in the dependencies. The output file will not be generated.");
        } else {
            generateResults(context);
        }
    }

    private void prepareExclusionPatterns(final AttributionContext context) throws MojoExecutionException {
        if (exclusionPatterns != null && !exclusionPatterns.isEmpty()) {
            context.exclusionPatterns.addAll(exclusionPatterns);
        }

        if (exclusionPatternsFile != null && exclusionPatternsFile.isFile()) {
            getLog().debug("Reading exclusionPatternsFile " + exclusionPatternsFile);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(exclusionPatternsFile), StandardCharsets.UTF_8))) {
                String line;
                while (null != (line = reader.readLine())) {
                    if (!line.isEmpty()) {
                        context.exclusionPatterns.add(line);
                        getLog().debug("Added exclusionPattern '" + line + "'");
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException(
                        "Failed to read patterns from the exlucsionPatternFile " + exclusionPatternsFile.getAbsolutePath(), e);
            }
        }
    }

    private void generateResults(final AttributionContext context) throws MojoExecutionException {
        ensureOutputFileParentDirCreated();
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(outputFile)), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Set<String>> gavEntry : context.foundAttribution.entrySet()) {
                Set<String> attributionSet = gavEntry.getValue();
                String gav = gavEntry.getKey();
                if (!attributionSet.isEmpty()) {
                    pw.println(gav);
                    getLog().debug("Adding " + attributionSet.size() + " attribution(s) for " + gav);
                    for (String attribution : attributionSet) {
                        String attributionLine = "\t" + attribution;
                        pw.println(attributionLine);
                        getLog().debug(attributionLine);
                    }
                    pw.println();
                } else {
                    getLog().debug("Skipping " + gav + " as no attribution was found there.");
                }
            }
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Unable to write to the outputFile " + outputFile, e);
        }
        getLog().info("Attribution file was generated: " + outputFile.getAbsolutePath());
    }

    private void ensureOutputFileParentDirCreated() {
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    protected abstract Map<String, File> resolveSourceJars();

    private void readJar(String gav, File jar, AttributionContext context) {
        if (!jar.isFile()) {
            getLog().info("Skipping the resolved source path as it's not a file: " + jar);
            return;
        }
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(jar)))) {
            ZipEntry zipEntry;
            while (null != (zipEntry = zip.getNextEntry())) {
                String srcName = zipEntry.getName();
                if (!zipEntry.isDirectory() && acceptFile(srcName)) {
                    try {
                        context.srcQueue.put(new SrcFile(gav, srcName, toByteArray(zip)));
                    } catch (InterruptedException e) {
                        getLog().warn("Putting source file to queue was interrupted", e);
                    } catch (IOException e) {
                        getLog().warn("Reading source file failed", e);
                    }
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            getLog().error("Reading archive failed: " + jar, e);
        }
    }

    private boolean acceptFile(String srcName) {
        String nameLowerCase = srcName.toLowerCase(Locale.ROOT);
        return nameLowerCase.endsWith(".java") || nameLowerCase.endsWith(".xml");
    }

    private void consumeSrc(AttributionContext context) {
        String patternStr = DEFAULT_COPYRIGHT_PATTERN;
        int group = DEFAULT_COPYRIGHT_PATTERN_GRPIDX;
        if (copyrightPattern != null && !copyrightPattern.isEmpty()) {
            patternStr = copyrightPattern;
            group = copyrightPatternGroupIndex >= 0 ? copyrightPatternGroupIndex : 0;
        }
        Pattern pattern = Pattern.compile(patternStr);
        while (context.producersRunning.get() || !context.srcQueue.isEmpty()) {
            try {
                SrcFile srcFile = context.srcQueue.poll(1, TimeUnit.SECONDS);
                getLog().debug("Processing " + srcFile.getSourceName() + " from " + srcFile.getGav());
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(srcFile.getBytes()), StandardCharsets.UTF_8))) {
                    String line;
                    while (null != (line = reader.readLine())) {
                        Matcher m = pattern.matcher(line);
                        if (m.find()) {
                            String copyrightStr = m.group(group);
                            if (isExcluded(context, copyrightStr)) {
                                getLog().debug("Excluded: " + copyrightStr);
                                continue;
                            }
                            Set<String> hitSet = context.foundAttribution.computeIfAbsent(srcFile.getGav(),
                                    s -> Collections.newSetFromMap(new ConcurrentSkipListMap<>()));
                            if (hitSet.add(copyrightStr)) {
                                getLog().debug("Found: " + copyrightStr);
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                getLog().debug(e);
            } catch (IOException e) {
                getLog().error(e);
            }
        }
    }

    private boolean isExcluded(AttributionContext context, String copyrightStr) {
        for (String patternStr : context.exclusionPatterns) {
            Pattern p = Pattern.compile(patternStr);
            if (p.matcher(copyrightStr).find()) {
                return true;
            }
        }
        return false;
    }

    private static byte[] toByteArray(InputStream in) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    protected Set<String> getReactorProjectGaSet() {
        Set<String> gaSet = new HashSet<>();
        if (reactorProjects != null) {
            for (final MavenProject p : reactorProjects) {
                String projectGaKey = gaKey(p.getGroupId(), p.getArtifactId());
                gaSet.add(projectGaKey);
            }
        }
        getLog().debug("Reactor GAs: " + gaSet);
        return gaSet;
    }

    protected ProjectBuildingRequest getProjectBuildingRequest(MavenProject currentProject) {
        return new DefaultProjectBuildingRequest(session.getProjectBuildingRequest())
                .setRemoteRepositories(currentProject.getRemoteArtifactRepositories());
    }

    protected List<Artifact> getRuntimeAndCompileScopedArtifacts(MavenProject p) {
        Set<Artifact> artifacts = p.getArtifacts();
        List<Artifact> list = new ArrayList<>(artifacts.size());
        for (Artifact a : artifacts) {
            if (Artifact.SCOPE_COMPILE.equals(a.getScope()) || Artifact.SCOPE_RUNTIME.equals(a.getScope())) {
                list.add(a);
            }
        }
        getLog().debug("Project " + gavKey(p.getArtifact()) + " artifacts: " + list);
        return list;
    }

    protected static String gaKey(Artifact artifact) {
        if (artifact == null) {
            return "[null GA]";
        }
        return gaKey(artifact.getGroupId(), artifact.getArtifactId());
    }

    protected static String gavKey(Artifact artifact) {
        if (artifact == null) {
            return "[null GAV]";
        }
        return gavKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
    }

    protected static String gaKey(String gid, String aid) {
        return gid + ":" + aid;
    }

    protected static String gavKey(String gid, String aid, String version) {
        return gaKey(gid, aid) + ":" + version;
    }

}
