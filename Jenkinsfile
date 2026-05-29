pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_NAME = 'pg-payment-service'
        AWS_REGION = 'ap-northeast-2'
        ECR_REPOSITORY = 'erumpay/pg-payment-service'
        AWS_CREDENTIALS_ID = 'aws-erumpay-ecr'
        INFRA_REPOSITORY_URL = 'https://github.com/ErumPay/erumpay-infra.git'
        INFRA_BRANCH = 'develop'
        INFRA_CREDENTIALS_ID = 'github-erumpay-infra-write'
        INFRA_HELM_VALUES_PATH = 'helm/values/dev/pg-payment-service.yaml'
        GIT_COMMITTER_NAME = 'erumpay-jenkins'
        GIT_COMMITTER_EMAIL = 'jenkins@erumpay.local'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Image Metadata') {
            steps {
                script {
                    env.IMAGE_TAG = env.GIT_COMMIT?.take(7)

                    if (!env.IMAGE_TAG) {
                        if (isUnix()) {
                            env.IMAGE_TAG = sh(
                                    script: 'git rev-parse --short HEAD',
                                    returnStdout: true
                            ).trim()
                        } else {
                            env.IMAGE_TAG = bat(
                                    script: '@git rev-parse --short HEAD',
                                    returnStdout: true
                            ).trim()
                        }
                    }

                    echo "Image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Gradle Build') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'chmod +x ./gradlew'
                        sh './gradlew clean build --no-daemon'
                    } else {
                        bat '.\\gradlew.bat clean build --no-daemon'
                    }
                }
            }
        }

        stage('Archive Artifact') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true, onlyIfSuccessful: true
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    def imageTag = "${env.IMAGE_NAME}:${env.IMAGE_TAG}"

                    if (isUnix()) {
                        sh "docker build -t ${imageTag} ."
                    } else {
                        bat "docker build -t ${imageTag} ."
                    }
                }
            }
        }

        stage('ECR Push') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: "${env.AWS_CREDENTIALS_ID}"]]) {
                    script {
                        def accountId

                        if (isUnix()) {
                            accountId = sh(
                                    script: "aws sts get-caller-identity --query Account --output text",
                                    returnStdout: true
                            ).trim()
                        } else {
                            accountId = bat(
                                    script: "@aws sts get-caller-identity --query Account --output text",
                                    returnStdout: true
                            ).trim()
                        }

                        def ecrRegistry = "${accountId}.dkr.ecr.${env.AWS_REGION}.amazonaws.com"
                        def localImage = "${env.IMAGE_NAME}:${env.IMAGE_TAG}"
                        def ecrImageRepository = "${ecrRegistry}/${env.ECR_REPOSITORY}"
                        def ecrImage = "${ecrImageRepository}:${env.IMAGE_TAG}"

                        env.ECR_IMAGE_REPOSITORY = ecrImageRepository
                        env.ECR_IMAGE = ecrImage

                        if (isUnix()) {
                            sh "aws ecr get-login-password --region ${env.AWS_REGION} | docker login --username AWS --password-stdin ${ecrRegistry}"
                            sh "docker tag ${localImage} ${ecrImage}"
                            sh "docker push ${ecrImage}"
                        } else {
                            bat "aws ecr get-login-password --region %AWS_REGION% | docker login --username AWS --password-stdin ${ecrRegistry}"
                            bat "docker tag ${localImage} ${ecrImage}"
                            bat "docker push ${ecrImage}"
                        }
                    }
                }
            }
        }

        stage('Update Infra Image Tag') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(
                        credentialsId: "${env.INFRA_CREDENTIALS_ID}",
                        usernameVariable: 'GIT_USERNAME',
                        passwordVariable: 'GIT_PASSWORD'
                )]) {
                    dir('erumpay-infra') {
                        checkout([
                                $class: 'GitSCM',
                                branches: [[name: "*/${env.INFRA_BRANCH}"]],
                                userRemoteConfigs: [[
                                        url: "${env.INFRA_REPOSITORY_URL}",
                                        credentialsId: "${env.INFRA_CREDENTIALS_ID}"
                                ]]
                        ])

                        script {
                            def values = readFile(env.INFRA_HELM_VALUES_PATH)
                            values = values.replaceFirst(
                                    '(?m)^\\s*repository:\\s*.+$',
                                    "  repository: \"${env.ECR_IMAGE_REPOSITORY}\""
                            )
                            values = values.replaceFirst(
                                    '(?m)^\\s*tag:\\s*.+$',
                                    "  tag: \"${env.IMAGE_TAG}\""
                            )
                            writeFile file: env.INFRA_HELM_VALUES_PATH, text: values
                        }

                        script {
                            if (isUnix()) {
                                sh '''
                                    git config user.name "$GIT_COMMITTER_NAME"
                                    git config user.email "$GIT_COMMITTER_EMAIL"

                                    if git diff --quiet -- "$INFRA_HELM_VALUES_PATH"; then
                                      echo "No infra image tag changes to commit."
                                    else
                                      git add "$INFRA_HELM_VALUES_PATH"
                                      git commit -m "chore: update pg-payment image tag to $IMAGE_TAG"
                                      git remote set-url origin "https://${GIT_USERNAME}:${GIT_PASSWORD}@github.com/ErumPay/erumpay-infra.git"
                                      git push origin "HEAD:${INFRA_BRANCH}"
                                    fi
                                '''
                            } else {
                                bat '''
                                    @echo off
                                    git config user.name "%GIT_COMMITTER_NAME%"
                                    git config user.email "%GIT_COMMITTER_EMAIL%"

                                    git diff --quiet -- "%INFRA_HELM_VALUES_PATH%"
                                    if %ERRORLEVEL% EQU 0 (
                                      echo No infra image tag changes to commit.
                                    ) else (
                                      git add "%INFRA_HELM_VALUES_PATH%"
                                      git commit -m "chore: update pg-payment image tag to %IMAGE_TAG%"
                                      git remote set-url origin "https://%GIT_USERNAME%:%GIT_PASSWORD%@github.com/ErumPay/erumpay-infra.git"
                                      git push origin "HEAD:%INFRA_BRANCH%"
                                    )
                                '''
                            }
                        }
                    }
                }
            }
        }
    }


    post {
        always {
            junit testResults: 'build/test-results/test/*.xml', allowEmptyResults: true
        }
    }
}
