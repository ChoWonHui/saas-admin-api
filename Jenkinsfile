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
        // 비밀 설정(S3·Pixabay·Brevo SMTP 키, QR base-url). 서버에만 두고 컨테이너에 읽기전용으로 붙인다.
        SECRETS_FILE   = '/opt/saas-admin/secrets/application-s3.yml'
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
                    //
                    // ⚠️ 비밀 설정 파일을 반드시 마운트한다.
                    //    S3 키 / Pixabay 키 / Brevo SMTP 키 / QR 이 가리킬 손님앱 주소가 이 파일에만 있다.
                    //    빠뜨리면 앱은 정상 기동하지만 이미지 업로드가 base64 로 떨어지고,
                    //    문의 알림 메일이 나가지 않으며, 새로 만든 QR 이 http://localhost:5175 를 가리킨다.
                    //    (실제로 이 마운트 없이 돌린 적이 있어 여기에 못 박아 둔다)
                    //    환경변수(-e)로 주지 않는 이유: docker inspect 에 비밀이 그대로 찍힌다.
                    sh """
                        docker run -d \
                            --name ${APP_NAME} \
                            -p ${HOST_PORT}:${CONTAINER_PORT} \
                            --memory 400m \
                            --restart unless-stopped \
                            -v ${SECRETS_FILE}:/config/application-s3.yml:ro \
                            -e SPRING_CONFIG_ADDITIONAL_LOCATION=/config/application-s3.yml \
                            -e LC_ALL=en_US.UTF-8 \
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
