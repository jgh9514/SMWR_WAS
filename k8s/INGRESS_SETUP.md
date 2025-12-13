# Kubernetes Ingress 설정 가이드

## 1. Minikube Ingress 애드온 활성화

Minikube에서 Ingress를 사용하려면 먼저 Ingress 애드온을 활성화해야 합니다:

```bash
minikube addons enable ingress
```

## 2. Ingress 리소스 적용

### 프론트엔드 Ingress
```bash
kubectl apply -f k8s/smwr-front-ingress.yaml
```

### 백엔드 Ingress (선택사항)
```bash
kubectl apply -f k8s/smwr-backend-ingress.yaml
```

## 3. Minikube IP 주소 확인

```bash
minikube ip
```

출력되는 IP 주소를 복사합니다 (예: `192.168.49.2`)

## 4. 호스트 파일 수정

### Windows
1. 메모장을 **관리자 권한**으로 실행
2. 파일 열기: `C:\Windows\System32\drivers\etc\hosts`
3. 다음 줄 추가:
   ```
   {MINIKUBE_IP} smwr.local
   {MINIKUBE_IP} api.smwr.local
   ```
   예시:
   ```
   192.168.49.2 smwr.local
   192.168.49.2 api.smwr.local
   ```
4. 저장

### Linux / macOS
```bash
sudo nano /etc/hosts
```

다음 줄 추가:
```
{MINIKUBE_IP} smwr.local
{MINIKUBE_IP} api.smwr.local
```

예시:
```
192.168.49.2 smwr.local
192.168.49.2 api.smwr.local
```

저장 후 종료 (Ctrl+X, Y, Enter)

## 5. Ingress 상태 확인

```bash
# Ingress 리소스 확인
kubectl get ingress

# Ingress 상세 정보 확인
kubectl describe ingress smwr-front-ingress

# Ingress 컨트롤러 Pod 확인
kubectl get pods -n ingress-nginx
```

## 6. 접속 테스트

호스트 파일 수정 후 브라우저에서 접속:

- 프론트엔드: `http://smwr.local`
- 백엔드 API: `http://api.smwr.local`

## 7. 문제 해결

### Ingress가 생성되지 않는 경우
```bash
# Ingress 애드온이 활성화되었는지 확인
minikube addons list | grep ingress

# 활성화되지 않은 경우
minikube addons enable ingress
```

### 호스트 파일 수정이 적용되지 않는 경우
- Windows: 관리자 권한으로 메모장 실행 확인
- DNS 캐시 클리어:
  ```bash
  # Windows
  ipconfig /flushdns
  
  # Linux
  sudo systemd-resolve --flush-caches
  # 또는
  sudo /etc/init.d/nscd restart
  
  # macOS
  sudo dscacheutil -flushcache
  sudo killall -HUP mDNSResponder
  ```

### Ingress 컨트롤러가 실행되지 않는 경우
```bash
# Ingress 컨트롤러 Pod 로그 확인
kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx

# Ingress 컨트롤러 재시작
kubectl rollout restart deployment -n ingress-nginx
```

## 8. Ingress 삭제

```bash
kubectl delete -f k8s/smwr-front-ingress.yaml
kubectl delete -f k8s/smwr-backend-ingress.yaml
```

