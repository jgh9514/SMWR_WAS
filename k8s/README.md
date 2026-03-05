# Kubernetes 배포 가이드

## Redis & Kafka 배포 순서

```bash
# 1. ConfigMap (Redis/Kafka 연결 정보)
kubectl apply -f infra-configmap.yaml

# 2. Redis
kubectl apply -f redis.yaml

# 3. Kafka
kubectl apply -f kafka.yaml

# 4. 앱 배포 (Redis/Kafka 준비 후)
kubectl apply -f deployment.yaml
```

## 외부 서비스 사용 시

- **AWS ElastiCache (Redis)**: `infra-configmap.yaml`의 `redis.host`를 ElastiCache 엔드포인트로 변경
- **AWS MSK (Kafka)**: `kafka.bootstrap-servers`를 MSK 브로커 목록으로 변경
- 이 경우 `redis.yaml`, `kafka.yaml`은 적용하지 않음

## 환경 변수 (ConfigMap)

| 키 | 기본값 | 설명 |
|----|--------|------|
| redis.host | smwr-redis | Redis 호스트 |
| redis.port | 6379 | Redis 포트 |
| kafka.bootstrap-servers | smwr-kafka:9092 | Kafka 브로커 |
| cache.provider | redis | caffeine \| redis |
| kafka.enabled | true | Kafka 사용 여부 |
