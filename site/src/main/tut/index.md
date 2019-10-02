---
layout: page
section: home
title: "Home"
---
**The Weddingplanner API**

Semantic schema repository

## Getting started

### Modules

- `weddingplanner-ns`: namespace definitions (ontologies and properties), Semantic schema repository
- `weddingplanner-api`: rest-api's for ...
- `weddingplanner-service`: services to coordinate a wedding planning process

Weddingplanner modules are available for Scala 2.12.x. 
To include `weddingplanner-xx` add the following to your `build.sbt`:
```
libraryDependencies += "nl.thijsbroersen" %% "weddingplanner-{xx}" % "{version}"
```

## Examples
Run local:
```
sbt weddingplannerService/run
```
or with docker:
```
cd examples
docker-compose up
```

## Demo
A demo-api runs at [http://api.convenantgemeenten.nl/](http://api.convenantgemeenten.nl/). 

The demo-data can be reset by doing a GET on [/reset](http://api.convenantgemeenten.nl/reset)

The demo-data can be cleared by doing a GET on [/clear](http://api.convenantgemeenten.nl/clear)

The service runs the following services:  

data:  
  * [/agenda/](http://api.convenantgemeenten.nl/agenda/)
  * [/appointment/](http://api.convenantgemeenten.nl/appointment/)
  * [/person/](http://api.convenantgemeenten.nl/person/)
  * [/place/](http://api.convenantgemeenten.nl/place/)

process:  
  * [/reportofmarriage/](http://api.convenantgemeenten.nl/reportofmarriage/)
  * [/weddingreservation/](http://api.convenantgemeenten.nl/weddingreservation/)
  
knowledge:
  * [/kinsman/](http://api.convenantgemeenten.nl/kinsman/)
    * [?person]
    * example: [/kinsman/?iri=person1&iri=person2&degree=3](http://api.convenantgemeenten.nl/kinsman/?iri=person1&iri=person2&degree=3)
  * [/canmarry/](http://api.convenantgemeenten.nl/canmarry/)