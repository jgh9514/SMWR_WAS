# GitHub Actions 설정 가이드

## 1. GitHub Secrets 설정

GitHub 저장소에서 다음 Secrets를 설정해야 합니다:

1. **Settings** > **Secrets and variables** > **Actions**로 이동
2. **New repository secret** 클릭하여 다음을 추가:

### 필수 Secrets

- **DOCKER_USERNAME**: Docker Hub 사용자명 (예: `gilhwanjeon`)
- **DOCKER_PASSWORD**: Docker Hub 비밀번호 또는 Access Token

> **참고**: Docker Hub Access Token 사용을 권장합니다.
> - Docker Hub > Account Settings > Security > New Access Token

## 2. GitHub Actions 권한 설정

자동 커밋을 위해 다음 권한이 필요합니다:

1. **Settings** > **Actions** > **General**로 이동
2. **Workflow permissions** 섹션에서:
   - ✅ **Read and write permissions** 선택
   - ✅ **Allow GitHub Actions to create and approve pull requests** 체크 (선택사항)

## 3. 워크플로우 동작

### 트리거 조건
- `main` 또는 `master` 브랜치에 푸시 시 자동 실행
- GitHub Actions 탭에서 수동 실행 가능 (`workflow_dispatch`)

### 실행 단계
1. ✅ 코드 체크아웃
2. ✅ JDK 8 설정
3. ✅ Maven 의존성 캐시
4. ✅ Maven 빌드 (`mvn clean package -DskipTests`)
5. ✅ Docker 이미지 빌드
6. ✅ Docker Hub에 푸시
   - 태그: `{branch}-{sha}`, `latest`, `{run_number}`
7. ✅ Kubernetes deployment.yaml 자동 업데이트 및 커밋

### 이미지 태그 형식
- `gilhwanjeon/smw-app:main-abc1234` (브랜치-SHA)
- `gilhwanjeon/smw-app:latest` (기본 브랜치만)
- `gilhwanjeon/smw-app:123` (워크플로우 실행 번호)

## 4. Kubernetes 배포

### 자동 배포 (권장)
워크플로우가 자동으로 `k8s/deployment.yaml`을 업데이트하고 커밋합니다.
이후 수동으로 Kubernetes에 적용:

```bash
kubectl apply -f k8s/deployment.yaml
```

### 수동 배포
자동 커밋이 실패하거나 수동으로 배포하려면:

```bash
# 1. 최신 이미지 태그 확인 (GitHub Actions 로그에서)
# 2. k8s/deployment.yaml의 image 태그 수정
# 3. Kubernetes에 적용
kubectl apply -f k8s/deployment.yaml
```

## 5. 문제 해결

### Docker Hub 로그인 실패
- Secrets가 올바르게 설정되었는지 확인
- Docker Hub Access Token 사용 권장

### 자동 커밋 실패
- GitHub Actions 권한이 "Read and write"로 설정되었는지 확인
- 저장소의 기본 브랜치가 `main` 또는 `master`인지 확인

### 빌드 실패
- GitHub Actions 로그에서 오류 확인
- 로컬에서 동일한 명령어로 빌드 테스트:
  ```bash
  mvn clean package -DskipTests
  docker build -t smw-app:test .
  ```

## 6. 워크플로우 파일 위치

- `.github/workflows/build-and-deploy.yml`

워크플로우를 수정하려면 이 파일을 편집하면 됩니다.

