pipeline {
    agent { label 'built-in' }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Spring Test') {
            steps {
                dir('BackEnd/spring/laive') {
                    sh 'chmod +x gradlew'
                    sh './gradlew clean test --no-daemon'
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'BackEnd/spring/laive/build/test-results/test/*.xml'
                }
            }
        }
    }
}
