# SMWR_WAS
서머너즈워 종합 커뮤니티

## CI/CD 설정 (GitHub Actions)

이 프로젝트는 GitHub Actions를 사용하여 자동 빌드 및 배포를 수행합니다.

### 설정 방법

1. **GitHub Secrets 설정**
   - GitHub 저장소의 Settings > Secrets and variables > Actions로 이동
   - 다음 Secrets를 추가:
     - `DOCKER_USERNAME`: Docker Hub 사용자명
     - `DOCKER_PASSWORD`: Docker Hub 비밀번호 또는 Access Token

2. **워크플로우 동작**
   - `main` 또는 `master` 브랜치에 푸시하면 자동으로:
     - Maven을 사용하여 Spring Boot 애플리케이션 빌드
     - Docker 이미지 빌드 및 Docker Hub에 푸시
     - 이미지 태그: `gilhwanjeon/smw-app:{branch}-{sha}`, `latest`, `{run_number}`

3. **Kubernetes 배포**
   - 현재는 Docker 이미지 빌드 및 푸시까지만 자동화됩니다
   - Kubernetes 배포는 수동으로 수행:
     ```bash
     # k8s/deployment.yaml의 image 태그를 업데이트한 후
     kubectl apply -f k8s/deployment.yaml
     ```

### 수동 빌드 및 배포

GitHub Actions를 사용하지 않는 경우:

```bash
# Docker 이미지 빌드
docker build -t smw-app:latest .

# Docker Hub에 푸시
docker tag smw-app:latest gilhwanjeon/smw-app:latest
docker push gilhwanjeon/smw-app:latest

# Kubernetes 배포
kubectl apply -f k8s/deployment.yaml
```
