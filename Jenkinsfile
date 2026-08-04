pipeline {
    agent { label 'built-in' }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        gitLabConnection('laive-gitlab')
    }

    stages {
        stage('Report Pending') {
            steps {
                updateGitlabCommitStatus name: 'jenkins', state: 'pending'
            }
        }

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

        stage('AI Test') {
            environment {
                GROQ_API_KEY = 'ci-test-placeholder'
                PYTHONPATH = '.:chat-ai:chat-collector'
            }
            steps {
                dir('AI') {
                    sh 'uv sync --frozen --dev'
                    sh '''
                        mkdir -p test-results
                        uv run pytest -q \
                            --junitxml=test-results/pytest.xml \
                            tests \
                            chat-ai/tests \
                            chat-collector/tests
                    '''
                }
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'AI/test-results/*.xml'
                }
            }
        }

        stage('Deploy Production') {
            when {
                expression {
                    env.GIT_BRANCH == 'origin/master' || env.BRANCH_NAME == 'master'
                }
            }
            steps {
                sh '''
                    ssh -i "$HOME/.ssh/laive_deploy" \
                        -o BatchMode=yes \
                        -o IdentitiesOnly=yes \
                        -o StrictHostKeyChecking=yes \
                        deploy@host.docker.internal
                '''
            }
        }
    }

    post {
        success {
            updateGitlabCommitStatus name: 'jenkins', state: 'success'
        }
        unstable {
            updateGitlabCommitStatus name: 'jenkins', state: 'failed'
        }
        failure {
            updateGitlabCommitStatus name: 'jenkins', state: 'failed'
        }
        aborted {
            updateGitlabCommitStatus name: 'jenkins', state: 'canceled'
        }
    }
}
