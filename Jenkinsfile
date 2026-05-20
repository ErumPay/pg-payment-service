pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        IMAGE_NAME = 'pgpayment'
        AWS_REGION = 'ap-northeast-2'
        ECR_REPOSITORY = 'erumpay/pg-payment-service'
        AWS_CREDENTIALS_ID = 'aws-erumpay-ecr'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
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
                    def imageTag = "${env.IMAGE_NAME}:${env.BUILD_NUMBER}"

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
                        def localImage = "${env.IMAGE_NAME}:${env.BUILD_NUMBER}"
                        def ecrImage = "${ecrRegistry}/${env.ECR_REPOSITORY}:${env.BUILD_NUMBER}"

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
    }

    post {
        always {
            junit testResults: 'build/test-results/test/*.xml', allowEmptyResults: true
        }
    }
}
