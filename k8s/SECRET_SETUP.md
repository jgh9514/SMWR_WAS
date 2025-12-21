# Kubernetes Secret 설정 가이드

## 보안을 위한 Secret 사용

비밀번호와 같은 민감한 정보는 Kubernetes Secret을 사용하여 관리합니다.

## Secret 생성 방법

### 방법 1: kubectl 명령어로 직접 생성 (권장)

EC2 서버에서 다음 명령어를 실행하세요:

```bash
kubectl create secret generic smw-db-secret \
  --from-literal=url='jdbc:log4jdbc:postgresql://YOUR_DB_HOST:YOUR_DB_PORT/YOUR_DB_NAME' \
  --from-literal=username='YOUR_DB_USERNAME' \
  --from-literal=password='YOUR_DB_PASSWORD'
```

### 방법 2: YAML 파일로 생성

1. `secret.yaml.example` 파일을 참고하여 `secret.yaml` 파일 생성 (Git에 커밋하지 마세요!)
2. base64로 인코딩:

```bash
echo -n 'jdbc:log4jdbc:postgresql://YOUR_DB_HOST:YOUR_DB_PORT/YOUR_DB_NAME' | base64
echo -n 'YOUR_DB_USERNAME' | base64
echo -n 'YOUR_DB_PASSWORD' | base64
```

3. 인코딩된 값을 `secret.yaml`에 입력
4. Secret 적용:

```bash
kubectl apply -f k8s/secret.yaml
```

## AWS S3 자격 증명 (IAM 역할 사용)

S3 업로드는 **IAM 역할**을 통해 자동으로 인증됩니다.

- ✅ **IAM 역할 사용**: EC2 인스턴스에 부여된 IAM 역할을 AWS SDK가 자동으로 감지합니다.
- ❌ **Access Key/Secret Key 불필요**: 코드나 설정 파일에 자격 증명을 저장할 필요가 없습니다.
- 🔒 **보안**: IAM 역할을 사용하면 자격 증명을 코드나 환경 변수에 저장하지 않아도 됩니다.

**IAM 역할 설정 방법**:
1. AWS 콘솔에서 EC2 인스턴스에 IAM 역할 부여
2. IAM 역할에 S3 버킷(`summonerswar-community`) 접근 권한 추가
3. 애플리케이션 코드는 자동으로 IAM 역할을 사용합니다.

## Secret 확인

```bash
# Secret 목록 확인
kubectl get secrets

# Secret 상세 정보 확인 (값은 base64로 표시됨)
kubectl get secret smw-db-secret -o yaml

# Secret 값 디코딩 (확인용)
kubectl get secret smw-db-secret -o jsonpath='{.data.password}' | base64 -d
```

## Secret 업데이트

```bash
# DB Secret 값 업데이트 (실제 값으로 변경하세요)
kubectl create secret generic smw-db-secret \
  --from-literal=url='jdbc:log4jdbc:postgresql://YOUR_DB_HOST:YOUR_DB_PORT/YOUR_DB_NAME' \
  --from-literal=username='YOUR_DB_USERNAME' \
  --from-literal=password='YOUR_DB_PASSWORD' \
  --dry-run=client -o yaml | kubectl apply -f -

# Pod 재시작 (새 Secret 적용)
kubectl rollout restart deployment/smw-app
```

## 주의사항

- ⚠️ **절대 `secret.yaml` 파일을 Git에 커밋하지 마세요!**
- `secret.yaml.example`은 예시 파일이므로 Git에 포함되어도 안전합니다.
- Secret은 base64로 인코딩되어 있지만, 암호화되지 않으므로 주의하세요.
- Production 환경에서는 Secret을 암호화하는 것이 좋습니다 (예: Sealed Secrets, External Secrets Operator).
