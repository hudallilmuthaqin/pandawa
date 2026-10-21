// PB Pandawa CI/CD
//   every branch : test backend + frontend, build Docker images
//   main only    : deploy to the local Docker engine (compose project "pandawa"), smoke test,
//                  automatic rollback to the previous images if the deploy fails
pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    environment {
        CACHE_DIR       = '/var/jenkins_home/caches'
        COMPOSE_PROJECT = 'pandawa'
        KEEP_IMAGES     = '5'
    }

    stages {
        stage('Prepare') {
            steps {
                // Written by Deploy only; a leftover from an older build must never trigger a rollback.
                sh 'rm -f .previous-tag'
                script {
                    env.IMAGE_TAG = env.GIT_COMMIT.take(7)
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Test') {
            parallel {
                stage('Backend') {
                    agent {
                        docker {
                            image 'eclipse-temurin:21-jdk'
                            reuseNode true
                        }
                    }
                    environment {
                        MAVEN_USER_HOME = "${CACHE_DIR}/maven-wrapper"
                    }
                    steps {
                        dir('backend') {
                            sh './mvnw -B -ntp -Dmaven.repo.local="$CACHE_DIR/m2" verify'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'backend/target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('Frontend') {
                    agent {
                        docker {
                            image 'node:22-alpine'
                            reuseNode true
                        }
                    }
                    environment {
                        npm_config_cache = "${CACHE_DIR}/npm"
                    }
                    steps {
                        dir('frontend') {
                            sh 'npm ci --no-audit --no-fund'
                            sh 'npx vitest run --reporter=default --reporter=junit --outputFile.junit=reports/junit.xml'
                            sh 'npm run build'
                        }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'frontend/reports/junit.xml'
                        }
                    }
                }
            }
        }

        stage('Build images') {
            steps {
                sh '''
                    set -eu
                    for app in backend frontend; do
                        docker build --pull \
                            --label org.opencontainers.image.revision="$GIT_COMMIT" \
                            --label org.opencontainers.image.source="$GIT_URL" \
                            -t "pandawa-$app:$IMAGE_TAG" "$app"
                    done
                '''
            }
        }

        stage('Deploy') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'pandawa-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -eu
                        # Remember what is running now so a failed deploy can be rolled back.
                        running=$(docker compose -p "$COMPOSE_PROJECT" ps -q backend 2>/dev/null || true)
                        if [ -n "$running" ]; then
                            image=$(docker inspect --format '{{.Config.Image}}' "$running")
                            echo "${image##*:}" > .previous-tag
                        else
                            rm -f .previous-tag
                        fi

                        docker compose -p "$COMPOSE_PROJECT" --env-file "$ENV_FILE" -f docker-compose.yml \
                            up -d --no-build --wait --wait-timeout 300 db backend frontend
                    '''
                }
            }
        }

        stage('Smoke test') {
            when { branch 'main' }
            steps {
                withCredentials([file(credentialsId: 'pandawa-prod-env', variable: 'ENV_FILE')]) {
                    sh '''
                        set -eu
                        compose="docker compose -p $COMPOSE_PROJECT --env-file $ENV_FILE -f docker-compose.yml"
                        # Frontend serves the SPA, and proxies the API through to the backend and database.
                        $compose exec -T frontend wget -qO- http://localhost/ | grep -q '<div id="root"'
                        $compose exec -T frontend wget -qO- http://localhost/api/settings/public > /dev/null
                        $compose exec -T frontend wget -qO- http://backend:8080/actuator/health | grep -q '"UP"'
                        echo "Smoke test passed for $IMAGE_TAG"
                    '''
                }
            }
            post {
                success {
                    sh '''
                        docker tag "pandawa-backend:$IMAGE_TAG" pandawa-backend:latest
                        docker tag "pandawa-frontend:$IMAGE_TAG" pandawa-frontend:latest
                    '''
                }
            }
        }
    }

    post {
        failure {
            script {
                if (env.BRANCH_NAME == 'main' && fileExists('.previous-tag')) {
                    def previous = readFile('.previous-tag').trim()
                    if (previous && previous != env.IMAGE_TAG) {
                        echo "Deploy of ${env.IMAGE_TAG} failed, rolling back to ${previous}"
                        withCredentials([file(credentialsId: 'pandawa-prod-env', variable: 'ENV_FILE')]) {
                            withEnv(["IMAGE_TAG=${previous}"]) {
                                sh '''
                                    docker compose -p "$COMPOSE_PROJECT" --env-file "$ENV_FILE" -f docker-compose.yml \
                                        up -d --no-build --wait --wait-timeout 300 db backend frontend
                                '''
                            }
                        }
                    }
                }
            }
        }
        always {
            // Keep the newest images per app; images still used by a container are skipped by `docker rmi`.
            sh '''
                for repo in pandawa-backend pandawa-frontend; do
                    docker image ls "$repo" --format '{{.Tag}}' | grep -vx latest | tail -n +$((KEEP_IMAGES + 1)) \
                        | xargs -r -I{} docker rmi "$repo:{}" > /dev/null 2>&1 || true
                done
                docker image prune -f > /dev/null
            '''
        }
    }
}
