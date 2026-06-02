pipeline {

    agent any

    triggers {
        githubPush()
    }

    environment {
        SERVER_USER = 'TK-RUBSUB'
        SERVER_HOST = '203.150.106.14'
        SERVER_PORT = '14321'

        SSH_KEY = '/var/jenkins_home/.ssh/jenkins_ed25519'

        JAVA_HOME = '/usr/lib/jvm/temurin-21-jdk-arm64'
        MAVEN_OPTS = '-Djava.awt.headless=true'

        SERVER_APP_PATH = '/opt/carbon-edge/api'
        DOCKER_COMPOSE_PATH = '/opt/carbon-edge'
        DOCKER_COMPOSE_FILE = '/opt/carbon-edge/docker-compose.yml'

        DOCKER_SERVICE_NAME = 'lms-api'
        DOCKER_CONTAINER_NAME = 'lms-api'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build API') {
            steps {
                sh '''
                    export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-arm64
                    export PATH=$JAVA_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

                    echo "JAVA_HOME=$JAVA_HOME"
                    echo "PATH=$PATH"

                    which java
                    which javac

                    java -version
                    javac -version

                    mvn -version

                    chmod +x mvnw || true

                    if [ -f "./mvnw" ]; then
                        ./mvnw clean package -DskipTests
                    else
                        mvn clean package -DskipTests
                    fi
                '''
            }
        }

        stage('Prepare Server Directory') {
            steps {
                sh """
                    ssh -i ${SSH_KEY} \
                    -o StrictHostKeyChecking=no \
                    -o UserKnownHostsFile=/dev/null \
                    -p ${SERVER_PORT} \
                    ${SERVER_USER}@${SERVER_HOST} \
                    "mkdir -p ${DOCKER_COMPOSE_PATH} ${SERVER_APP_PATH}/src ${SERVER_APP_PATH}/template ${SERVER_APP_PATH}/upload-image"
                """
            }
        }

        stage('Deploy API Source to Ubuntu') {
            steps {
                sh """
                    rsync -avz \
                    -e "ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -p ${SERVER_PORT}" \
                    --exclude '.git' \
                    --exclude '.idea' \
                    --exclude 'target' \
                    --exclude 'upload-image' \
                    ./ \
                    ${SERVER_USER}@${SERVER_HOST}:${SERVER_APP_PATH}/
                """
            }
        }

        stage('Deploy Docker Compose to Ubuntu') {
            steps {
                sh """
                    rsync -avz \
                    -e "ssh -i ${SSH_KEY} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -p ${SERVER_PORT}" \
                    docker-compose.yml \
                    ${SERVER_USER}@${SERVER_HOST}:${DOCKER_COMPOSE_PATH}/docker-compose.yml
                """
            }
        }

        stage('Restart API Container') {
            steps {
                sh """
                    ssh -i ${SSH_KEY} \
                    -o StrictHostKeyChecking=no \
                    -o UserKnownHostsFile=/dev/null \
                    -p ${SERVER_PORT} \
                    ${SERVER_USER}@${SERVER_HOST} \
                    "bash -lc 'set -e; cd ${DOCKER_COMPOSE_PATH}; test -f ${DOCKER_COMPOSE_FILE}; sudo docker rm -f ${DOCKER_CONTAINER_NAME} || true; sudo docker compose -f ${DOCKER_COMPOSE_FILE} up -d --build --remove-orphans ${DOCKER_SERVICE_NAME}'"
                """
            }
        }
    }

    post {

        success {
            echo 'API deploy success'
        }

        failure {
            echo 'API deploy failed'
        }
    }
}
