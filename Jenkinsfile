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
      description: 'Jenkins Username/Password credential ID for Nexus deploy. Tests still run when this credential is missing; deploy is skipped.'
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
        script {
          def xmlEscape = { String s ->
            if (s == null) return ''
            return s.replace('&', '&amp;')
                    .replace('<', '&lt;')
                    .replace('>', '&gt;')
                    .replace('"', '&quot;')
                    .replace("'", '&apos;')
          }
          // Unit tests resolve Apache Hop and the other libraries from Maven Central.
          // A <server> entry is only required for mvn deploy to the hopper hosted repo.
          def writeSettings = { String user, String pass ->
            def serverId = xmlEscape(params.NEXUS_SERVER_ID)
            def deployUrl = xmlEscape(params.NEXUS_DEPLOY_URL)
            def localRepo = xmlEscape(env.MAVEN_REPO_LOCAL)
            def servers = ''
            if (user != null && !user.isEmpty()) {
              def userXml = xmlEscape(user)
              def passXml = xmlEscape(pass == null ? '' : pass)
              servers = """
  <servers>
    <server>
      <id>${serverId}</id>
      <username>${userXml}</username>
      <password>${passXml}</password>
    </server>
  </servers>"""
            }
            writeFile file: "${env.WORKSPACE}/ci-settings.xml", text: """\
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <localRepository>${localRepo}</localRepository>${servers}
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
          }

          env.NEXUS_CREDS_PRESENT = 'false'
          try {
            withCredentials([usernamePassword(
                credentialsId: "${params.NEXUS_CREDENTIALS_ID}",
                usernameVariable: 'NEXUS_USER',
                passwordVariable: 'NEXUS_PASS')]) {
              writeSettings(env.NEXUS_USER, env.NEXUS_PASS)
              env.NEXUS_CREDS_PRESENT = 'true'
              echo "Generated ci-settings.xml with Nexus server '${params.NEXUS_SERVER_ID}'"
            }
          } catch (err) {
            def message = err.toString()
            if (!message.contains('Could not find credentials')) {
              throw err
            }
            echo "Jenkins credential '${params.NEXUS_CREDENTIALS_ID}' does not exist. " +
                 "Generated ci-settings.xml without a server password so tests can resolve public artifacts. " +
                 "Deploy is skipped until that Username/Password credential is created " +
                 "(the controller currently provisions only 'nexus-hop-community')."
            writeSettings('', '')
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
        script {
          if (env.NEXUS_CREDS_PRESENT == 'true') {
            sh 'mvn -B deploy -DskipTests -s ci-settings.xml'
          } else {
            // Set during the stage so the final result stays UNSTABLE. post { success }
            // does not run after this, and a later stage will not reset it to SUCCESS.
            currentBuild.result = 'UNSTABLE'
            echo "Deploy skipped: create Jenkins Username/Password credential '${params.NEXUS_CREDENTIALS_ID}' to publish to ${params.NEXUS_DEPLOY_URL}"
          }
        }
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
