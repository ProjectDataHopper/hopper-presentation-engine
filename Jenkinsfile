pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 1, unit: 'HOURS')
  }

  parameters {
    string(
      name: 'NEXUS_DEPLOY_URL',
      defaultValue: 'https://repository.data-hopper.com/repository/hopper/',
      description: 'Nexus hosted repository URL'
    )
    string(
      name: 'NEXUS_SERVER_ID',
      defaultValue: 'hopper',
      description: 'Maven server ID matching pom.xml distributionManagement'
    )
    string(
      name: 'NEXUS_CREDENTIALS_ID',
      defaultValue: 'nexus-hopper-build',
      description: 'Jenkins Username/Password credential ID'
    )
    booleanParam(
      name: 'SKIP_TESTS',
      defaultValue: false,
      description: 'Skip unit tests during build'
    )
  }

  environment {
    MAVEN_OPTS = '-Xmx2g -Djava.awt.headless=true'
    MAVEN_REPO_LOCAL = "${env.WORKSPACE}/.m2/repository"
  }

  tools {
    jdk 'jdk-21'
    maven 'Maven 3.9.9'
  }

  stages {
    stage('Show Toolchain') {
      steps {
        sh '''
          set -euo pipefail
          java -version
          mvn -version
        '''
      }
    }

    stage('Prepare Maven Settings') {
      steps {
        withCredentials([usernamePassword(
            credentialsId: "${params.NEXUS_CREDENTIALS_ID}",
            usernameVariable: 'NEXUS_USER',
            passwordVariable: 'NEXUS_PASS')]) {
          script {
            def xmlEscape = { String s ->
              if (s == null) return ''
              return s.replace('&', '&amp;')
                      .replace('<', '&lt;')
                      .replace('>', '&gt;')
                      .replace('"', '&quot;')
                      .replace("'", '&apos;')
            }
            def userXml = xmlEscape(env.NEXUS_USER)
            def passXml = xmlEscape(env.NEXUS_PASS)
            def serverId = xmlEscape(params.NEXUS_SERVER_ID)
            def deployUrl = xmlEscape(params.NEXUS_DEPLOY_URL)
            def localRepo = xmlEscape(env.MAVEN_REPO_LOCAL)

            writeFile file: "${env.WORKSPACE}/ci-settings.xml", text: """\
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>${localRepo}</localRepository>
  <servers>
    <server>
      <id>${serverId}</id>
      <username>${userXml}</username>
      <password>${passXml}</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>data-hopper</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
          <id>${serverId}</id>
          <url>${deployUrl}</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>data-hopper</activeProfile>
  </activeProfiles>
</settings>
"""
            echo "Generated ci-settings.xml"
          }
        }
      }
    }

    stage('Build & Test') {
      when {
        expression { return !params.SKIP_TESTS }
      }
      steps {
        sh 'mvn -B clean test -s ci-settings.xml'
      }
    }

    stage('Deploy to Nexus') {
      steps {
        sh 'mvn -B deploy -DskipTests -s ci-settings.xml'
      }
    }
  }

  post {
    success {
      echo "Successfully deployed artifacts to ${params.NEXUS_DEPLOY_URL}"
    }
    failure {
      echo "Build or deployment failed. Check Console Output for details."
    }
  }
}
