# The GuardianshipTest API


## Getting started

### Modules

- `ns`: namespace definitions (ontologies and properties), Semantic schema repository
- `api`: rest-api's for guardianship testing
- `service`: services to execute remotely

Guardianshiptest modules are available for Scala 2.12.x. 
To include `xx` add the following to your `build.sbt`:
```
libraryDependencies += "nl.convenantgemeenten.guardianship" %% "{xx}" % "{version}"
```

## Examples
Run local:
```
sbt service/run
```
or with docker:
```
cd examples
docker-compose up
```
