# 메모리·노드 여유가 작을 때 배포 순서 (App 교체 + Batch 단계적 스케일)

> **운영 환경 정본**: [docs/운영_인프라_환경.md](../../docs/운영_인프라_환경.md)  
> t4g.medium = K8s(redis, app, front, batch) · t4g.small = PostgreSQL only

## t4g.medium (4GB) 메모리 배분

단일 노드(t4g.medium **4GB**, ARM64)에 **smw-app · smw-batch · smw-redis · smwr-front** 만 둘 때 기준.

**PostgreSQL은 별도 EC2 t4g.small (2GB)** — 아래 limits에 DB 메모리는 포함하지 않는다. 노드 4GB는 앱·캐시·프론트·K3s에 사용한다.

| Pod | requests | limits | 런타임 |
|-----|----------|--------|--------|
| smw-app | 512Mi | **1Gi** | `-Xmx512m` `MaxMetaspaceSize=192m` · OTEL ON (off-heap 여유 필요) |
| smw-batch | 512Mi | 1024Mi | `-Xmx512m` `MaxMetaspaceSize=256m` · OTEL agent OFF |
| smw-redis | 128Mi | 256Mi | `--maxmemory 192mb` LRU |
| smwr-front | 192Mi | 384Mi | `NODE_OPTIONS --max-old-space-size=256` |

- **limits 합 ~2.7GB** (app 1Gi + batch 1Gi + redis 256Mi + front 384Mi) → OS·K3s·버스트 여유 ~1.3GB
- **JAVA_OPTS** 는 Dockerfile ENTRYPOINT에서 미사용 → **`JAVA_TOOL_OPTIONS`** 로 힙 지정
- Hikari: API `10` / 배치 `8` (`SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE`)
- RTA 시너지(배치 Pod, 평시): `SMW_RTA_SYNERGY_BATCH_SIZE=1000`, `SMW_RTA_SYNERGY_MAX_ROUNDS_CAP=1`, `SMW_RTA_BATCH_BACKLOG_SCALING=false` — Job 장시간(5~12h) 방지. 대량 백필만 `bulkload` 프로필·cap 상향. `GET /api/v1/batch/backlog` 로 scaled 라운드 확인
- 배포·롤아웃 중 OOM 시 아래 **staged rollout** 순서 사용

동일 이미지로 **smw-app**·**smw-batch**를 띄우면, 배포 시점에 **API 롤아웃 + 배치 Pod 기동**이 겹치면서 노드 OOMKilled·스케줄 실패가 나기 쉽다. 그럴 땐 아래 순서로 **배치를 잠시 0**으로 두고 App만 교체한 뒤, 배치를 **1**로 올리고(평시 목표는 `batch-deployment`의 `replicas`와 같음) 필요하면 **2**로 스케일한다.

## GitHub Actions (기본: 둘 다 동시에 교체)

`main` **push** 시 워크플로는 기본 **parallel** — app·batch 매니페스트 적용 후 **둘 다** `rollout restart` 와 기존과 같다.

- **push에서도** 순차(staged)로 돌리려면: GitHub **Repository variables**에 `K8S_STAGED_ROLLOUT` = `true` (문자열 그대로).
- **Actions → Build and Deploy → Run workflow** 로 수동 실행 시 **k8s_rollout_mode = staged** 선택 가능.

`k8s/scripts/remote-k8s-deploy.sh` 가 `K8S_ROLLOUT_MODE=staged` 이면 batch 0 → app → batch 1 → 매니페스트의 replicas 목표까지 올림.

## 전제

- `kubectl` 컨텍스트·네임스페이스가 맞는지 확인.
- 이미지 태그는 CI/CD·`kubectl set image` 등 기존 방식대로.

## 권장 순서

1. **배치 스케일 0** (Quartz/집계 부하·메모리 제거)

   ```bash
   kubectl scale deployment smw-batch --replicas=0
   ```

2. **App 롤아웃** (새 이미지·매니페스트 반영)

   ```bash
   kubectl rollout restart deployment smw-app
   # 또는 kubectl apply -f k8s/deployment.yaml, set image 등
   kubectl rollout status deployment smw-app
   ```

3. **배치 1**으로 기동

   ```bash
   kubectl scale deployment smw-batch --replicas=1
   kubectl rollout status deployment smw-batch
   ```

4. **동작 확인** — 로그·헬스·대표 Job·DB 부하(환경에 맞게).

5. **필요 시 배치 2** (또는 `k8s/batch-deployment.yaml`에 맞는 목표 replica)

   ```bash
   kubectl scale deployment smw-batch --replicas=2
   ```

## `batch-deployment.yaml`의 `replicas`와의 관계

매니페스트에 `replicas`(현재 기본 1)로 **평시 목표**를 Git에 둔다. 위처럼 `kubectl scale`로 임시 조정해도, 이후 `kubectl apply -f k8s/batch-deployment.yaml`을 하면 **파일에 적힌 replica로 되돌아갈 수 있음**. 운영 시:

- `apply` 전에 `batch-deployment.yaml`의 `replicas`를 배포 당시 목표(예: 1)로 맞추거나,
- `apply` 후 `kubectl scale`로 다시 맞추는 식으로 정리.

## OOMKilled (Exit 137) · Readiness 실패

- **원인**: Pod limit 768Mi + `-Xmx512m` 이면 Metaspace·스레드·OTEL·네이티브가 limit을 넘기기 쉽다. 부팅·GC 중 probe `timeoutSeconds: 3` 도 `context deadline exceeded` 를 유발한다.
- **조치** (`k8s/deployment.yaml`): app limit **1Gi**, `MaxMetaspaceSize=192m`, startup/readiness **timeout 10s** · startup **initialDelay 25s** · failureThreshold 상향.
- **배포 시**: App·Batch 동시 롤아웃은 노드 메모리 스파이크 → **staged rollout**(batch 0 → app → batch 1) 권장.
- **노드 4GB가 빠듯하면**: limit **1280Mi** 로 한 단계 더 올리거나, 배치 replica 0 상태에서 app만 먼저 안정화.

## ImagePullBackOff: `... not found` (태그)

- `docker/metadata` `type=sha` **기본 short(7자)** vs 배포 `ref_name+full SHA(40자)` → `format=long` 필수.  
- 브랜치가 `master`인데 배포가 `main-<sha>`로 고정이면, Hub에는 `master-<sha>`만 있어 **NotFound** — 배포 `IMAGE_TAG`는 `ref_name`+`sha`로 푸시한 브랜치와 맞춤.

## 한 줄 요약

**batch 0 → app 교체·안정 → batch 1 → 확인 → (선택) scale 2**
