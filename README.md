# Jenkins AccuRev Plugin

[![Build Status](https://ci.jenkins.io/job/Plugins/job/accurev-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/accurev-plugin/job/master/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/accurev.svg)](https://plugins.jenkins.io/accurev)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/accurev-plugin.svg?label=changelog)](https://github.com/jenkinsci/accurev-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/accurev.svg?color=blue)](https://plugins.jenkins.io/accurev)

This plugin allows you to use [AccuRev](https://www.microfocus.com/en-us/products/accurev/overview) as a SCM.

## Adopt this plugin

This plugin is up for adoption, it needs an activate maintainer who actively uses AccuRev.

## Bug Reports

File bug reports [here](http://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+%22In+Progress%22%2C+Reopened%29+AND+component+%3D+%27accurev-plugin%27)

## Development

Start the local Jenkins instance:

```bash
mvn hpi:run
```

### How to install

Run

```bash
mvn clean package
```

to create the plugin .hpi file.


To install:

1. copy the resulting ./target/accurev.hpi file to the $JENKINS_HOME/plugins directory. Don't forget to restart Jenkins afterwards.

2. or use the plugin management console (http://example.com:8080/pluginManager/advanced) to upload the hpi file. You have to restart Jenkins in order to find the pluing in the installed plugins list.

## Plugin releases

```bash
mvn release:prepare release:perform -B
```
