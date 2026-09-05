// saas-admin-api CI/CD 파이프라인.
//
// 같은 Jenkins 에서 돌던 food-biz-api 파이프라인과 같은 구조다.
// 다른 점은 포트뿐이다. 8080 은 food-biz-api 가, 9090 은 Jenkins 가 이미 쓰고 있어
// 이 앱은 호스트 8081 로 받아 컨테이너 8089(application.yml 의 server.port)로 넘긴다.

pipeline {
    agent any

    environment {
        APP_NAME       = 'saas-admin-api'
        DOCKER_IMAGE   = "saas-admin-api:${BUILD_NUMBER}"
        CONTAINER_PORT = '8089'
        HOST_PORT      = '8081'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/ChoWonHui/saas-admin-api.git',
                    credentialsId: 'github-credentials'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    docker.build("${DOCKER_IMAGE}")
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    sh """
                        docker stop ${APP_NAME} || true
                        docker rm ${APP_NAME} || true
                    """
                    // --memory 로 상한을 둔다. 이 장비는 RAM 이 952MB 뿐이라
                    // 한 컨테이너가 부풀면 Jenkins 나 food-biz-api 가 밀려난다.
                    sh """
                        docker run -d \
                            --name ${APP_NAME} \
                            -p ${HOST_PORT}:${CONTAINER_PORT} \
                            --memory 400m \
                            --restart unless-stopped \
                            ${DOCKER_IMAGE}
                    """
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    // 이 장비는 1 vCPU 라 Spring Boot 기동이 느리다.
                    // 20초로는 모자라서 최대 90초까지 기다리며 확인한다.
                    sh """
                        for i in \$(seq 1 18); do
                            if curl -fs http://localhost:${HOST_PORT}/actuator/health > /dev/null; then
                                echo "기동 확인 (\$((i*5))초)"
                                exit 0
                            fi
                            sleep 5
                        done
                        echo "90초 안에 응답이 없다. 컨테이너 로그를 확인한다."
                        docker logs --tail 60 ${APP_NAME}
                        exit 1
                    """
                }
            }
        }

        stage('Cleanup Old Images') {
            steps {
                script {
                    // 최근 3개만 남긴다. 디스크가 20GB 뿐이라 이미지가 쌓이면 금방 찬다.
                    sh "docker images ${APP_NAME} --format '{{.Tag}}' | sort -rn | tail -n +4 | xargs -r -I {} docker rmi ${APP_NAME}:{} || true"
                }
            }
        }
    }

    post {
        success {
            echo "배포 성공. 빌드 #${BUILD_NUMBER} → http://3.34.183.170:${HOST_PORT}/swagger-ui.html"
        }
        failure {
            echo "배포 실패. 빌드 #${BUILD_NUMBER}"
        }
    }
}
