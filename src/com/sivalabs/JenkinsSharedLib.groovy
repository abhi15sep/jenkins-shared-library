package com.sivalabs

class JenkinsSharedLib implements Serializable {

    // pipeline global properties
    def steps
    def env
    def params
    def scm
    def currentBuild

    JenkinsSharedLib(steps, env, params, scm, currentBuild) {
        this.steps = steps
        this.env = env
        this.params = params
        this.scm = scm
        this.currentBuild = currentBuild
    }

    def checkout() {
        steps.stage("Checkout") {
            steps.checkout scm
        }
    }

    def runMavenTests() {
        steps.stage("Test") {
            try {
                steps.sh './mvnw clean verify'
            } finally {
                steps.junit 'target/surefire-reports/*.xml'
                steps.junit 'target/failsafe-reports/*.xml'
                steps.publishHTML(target:[
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco-aggregate',
                        reportFiles: 'index.html',
                        reportName: "Jacoco Report"
                ])
            }
        }
    }

    def runOWASPChecks() {
        steps.stage("OWASP Checks") {
            try {
                steps.sh './mvnw dependency-check:check'
            } finally {
                steps.publishHTML(target:[
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target',
                        reportFiles: 'dependency-check-report.html',
                        reportName: "OWASP Dependency Check Report"
                ])
            }
        }
    }

    def publishDockerImage() {
        steps.stage("Publish Docker Image") {
            steps.echo "PUBLISH_TO_DOCKERHUB: ${params.PUBLISH_TO_DOCKERHUB}"
            if(params.PUBLISH_TO_DOCKERHUB) {
                steps.echo "Publishing to dockerhub. DOCKER_USERNAME=${env.DOCKER_USERNAME}, APPLICATION_NAME=${env.APPLICATION_NAME}"
                steps.sh "docker build -t ${env.DOCKER_USERNAME}/${env.APPLICATION_NAME}:${env.BUILD_NUMBER} -t ${env.DOCKER_USERNAME}/${env.APPLICATION_NAME}:latest ."

                steps.withCredentials([[$class: 'UsernamePasswordMultiBinding',
                                  credentialsId: 'docker-hub-credentials',
                                  usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    steps.sh "docker login --username $USERNAME --password $PASSWORD"
                }
                steps.sh "docker push ${env.DOCKER_USERNAME}/${env.APPLICATION_NAME}:latest"
                steps.sh "docker push ${env.DOCKER_USERNAME}/${env.APPLICATION_NAME}:${env.BUILD_NUMBER}"
            } else {
                steps.echo "Skipping Publish Docker Image"
            }
        }
    }
}