#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
def recentLTS = "2.361"
def configurations = [
    [ platform: "linux", jdk: "11", jenkins: recentLTS ],
    [ platform: "windows", jdk: "11", jenkins: recentLTS ],
]
buildPlugin(configurations: configurations)
