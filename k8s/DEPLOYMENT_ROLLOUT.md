# 메모리·노드 여유가 작을 때 배포 순서 (App 교체 + Batch 단계적 스케일)

동일 이미지로 **smw-app**·**smw-batch**를 띄우면, 배포 시점에 **API 롤아웃 + 배치 Pod 기동**이 겹치면서 노드 OOMKilled·스케줄 실패가 나기 쉽다. 그럴 땥 아래 순서로 **배치를 잠시 0**으로 두고 App만 교체한 뒤, 배치를 **1 → 동작 확인 → 2**로 올린다.

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

매니페스트에 `replicas: 2`로 두어 **평시 목표**를 Git에 둔다. 위처럼 `kubectl scale`로 임시 조정해도, 이후 `kubectl apply -f k8s/batch-deployment.yaml`을 하면 **파일에 적힌 replica로 되돌아갈 수 있음**. 운영 시:

- `apply` 전에 `batch-deployment.yaml`의 `replicas`를 배포 당시 목표(예: 1)로 맞추거나,
- `apply` 후 `kubectl scale`로 다시 맞추는 식으로 정리.

## ImagePullBackOff: `... not found` (태그)

배포 스크립트는 `main-<40자 full SHA>`(GitHub `github.sha`와 동일)로 `sed`한다.  
Docker Hub에 올리는 `docker/metadata`의 `type=sha` **기본은 short(7자)**라서, 예전엔 `main-55a27e0`만 푸시되고 K8s는 `main-55a27e0…(40자)`를 당겨 **NotFound**가 났다.  
워크플로에서 `type=sha,format=long`로 맞춤(`.github/workflows/build-and-deploy.yml`).

## 한 줄 요약

**batch 0 → app 교체·안정 → batch 1 → 확인 → batch 2**
