# attribution-maven-plugin

The `attribution-maven-plugin` is a Maven Plugin that creates author's attribution for project dependencies.

## Usage

The plugin contains 2 goals
* `generate` - the attribution file is generated per project/module;
* `aggregate` - one attribution file is aggregated for all subprojects/submodules;

By default the resulting file is the `target/attribution.txt`.

### The pom.xml

```xml
<plugins>
    <plugin>
        <groupId>com.hazelcast.maven</groupId>
        <artifactId>attribution-maven-plugin</artifactId>
        <version>1.0-SNAPSHOT</version>
        <executions>
            <execution>
                <id>attribution</id>
                <goals>
                    <goal>generate</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
</plugins>
```

### The command-line

```bash
mvn com.hazelcast.maven:attribution-maven-plugin:1.0-SNAPSHOT:aggregate
```