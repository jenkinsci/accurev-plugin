#!/usr/bin/env groovy

/* `buildPlugin` step provided by: https://github.com/jenkins-infra/pipeline-library */
def recentLTS = "2.176.4"
def configurations = [
    [ platform: "linux", jdk: "8", jenkins: null ],
    [ platform: "linux", jdk: "8", jenkins: recentLTS, javaLevel: "8" ],
    [ platform: "linux", jdk: "11", jenkins: recentLTS, javaLevel: "8" ],
    [ platform: "windows", jdk: "8", jenkins: null ],
    [ platform: "windows", jdk: "8", jenkins: recentLTS, javaLevel: "8" ],
    [ platform: "windows", jdk: "11", jenkins: recentLTS, javaLevel: "8" ],
]
buildPlugin(configurations: configurations)
