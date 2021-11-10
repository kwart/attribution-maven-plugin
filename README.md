# attribution-maven-plugin

The `attribution-maven-plugin` is a Maven Plugin that creates author's attribution for project dependencies.

## Usage

The plugin contains 2 goals
* `generate` - the attribution file is generated per project/module;
* `aggregate` - one attribution file is aggregated for all subprojects/submodules;

By default the resulting file is the `target/attribution.txt`.

Both goals are thread safe and they share the following configuration.

### Configuration properties

| Property name | User property | Default value | Description |
|:-:|:-:|:-:|---|
| `copyrightPattern` | `attribution.copyrightPattern` | `(?i)^([\s/*]*)(((\(c\))\|(copyright))\s+\S[^;{}]*)$` | Customizes the pattern for finding the "attribution lines". |
| `copyrightPatternGroupIndex` | `attribution.copyrightPatternGroupIndex` |  | When a custom `copyrightPattern` is configured, then this parameter allows to specify which capture group is used. By default, the whole pattern is used (group==0) when the custom pattern is configured. Capture group 2 is used otherwise (i.e. for the default pattern). |
| `exclusionPatterns` |   |   | Specifies list of copyright exclusion patterns. |
| `exclusionPatternsFile` | `attribution.exclusionPatternsFile` |   | Parameter which can specify a file in which exclusion patterns are listed. File should be in `UTF-8` with a pattern per line. |
| `outputFile` | `attribution.outputFile` | `${project.build.directory}/attribution.txt` | Specifies the destination attribution file. |
| `parallelism` | `attribution.parallelism` | *(#available-processors)* | Configures the parallelism level. By default the number of available processors is used. Parallelism says (1) how many new threads is used for reading source archives and also (2) how many new threads is used for search the copyright notices. |
| `serviceTimeoutMinutes` | `attribution.serviceTimeoutMinutes` | `60` | Maximal wait time for finishing reading source JARs and searching for patterns in the found source files. |
| `skip` | `attribution.skip` | `false` | Specifies whether the attribution file generation should be skipped. |

### The pom.xml

```xml
<plugin>
    <groupId>com.hazelcast.maven</groupId>
    <artifactId>attribution-maven-plugin</artifactId>
    <version>${maven.attribution-maven-plugin}</version>
    <executions>
        <execution>
            <id>attribution</id>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### The command-line

```bash
mvn com.hazelcast.maven:attribution-maven-plugin:1.0:aggregate
```