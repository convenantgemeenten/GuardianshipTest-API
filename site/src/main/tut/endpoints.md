---
layout: docs
title: Endpoint guide
position: 2
---

# Endpoint Guide
* [Overview](#overview)
* [Knowledge services](#knowledge-services)
  * [GuardianshipTest](#guardianshiptest-service)
  
## Overview
There are multiple endpoints, each has its own purpose. 

## Knowledge services
Knowlegde services can be queried or asked for information. They are a hotspot or interchange for information. 
A knowledge service can execute federated queries, this means that the query contains parts to be executed on other services. 
This can be the result of a query or some sort of assertion.

### GuardianshipTest service
This service can test if a subject is under legal restraint.
It returns a boolean (```Content-Type: text/boolean```)
Request structure:
POST ```/```  
body ```{"subject":"$bsnOrOtherRef","validOn":"2016-12-01"}```