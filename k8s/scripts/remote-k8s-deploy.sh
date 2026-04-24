#!/usr/bin/env bash
# EC2( k3s )에서 Git pull 후 K8s 배포. GHA가 stdin으로
#   export K8S_ROLLOUT_MODE=parallel|staged
#   export IMAGE_TAG=<ref_name>-<40자 sha>  (예: main-..., master-...)
# 를 앞에 붙여 pipe 하거나, EC2의 ~/SMWR_WAS 에서 pull 후
#   K8S_ROLLOUT_MODE=... IMAGE_TAG=... bash k8s/scripts/remote-k8s-deploy.sh
# 로 호출.
# set -e 제거 (일부 명령어 실패해도 계속 — 기존 GHA와 동일)

K8S_ROLLOUT_MODE="${K8S_ROLLOUT_MODE:-parallel}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
export K8S_ROLLOUT_MODE
export IMAGE_TAG

cd ~/SMWR_WAS || { echo "ERROR: SMWR_WAS 디렉토리로 이동 실패"; exit 1; }

# Git 저장소 설정 (없는 경우)
if [ ! -d .git ]; then
	echo "=== Git 저장소 초기화 ==="
	git init || echo "Git init 실패 (무시)"
	git remote add origin https://github.com/jgh9514/SMWR_WAS.git 2>/dev/null || \
	git remote set-url origin https://github.com/jgh9514/SMWR_WAS.git 2>/dev/null || \
	echo "Git remote 설정 실패 (무시)"
fi

# 최신 코드 가져오기
echo "=== 최신 코드 가져오기 ==="
GIT_FETCH_SUCCESS=false
for i in {1..5}; do
	if timeout 30 git fetch origin main 2>&1; then
		echo "Git fetch 성공"
		GIT_FETCH_SUCCESS=true
		break
	else
		echo "Git fetch 실패, 재시도 $i/5... (5초 대기)"
		sleep 5
	fi
done

if [ "$GIT_FETCH_SUCCESS" = "true" ] && git rev-parse --verify origin/main >/dev/null 2>&1; then
	echo "=== 로컬 브랜치 업데이트 ==="
	git reset --hard origin/main 2>&1 || \
	git checkout -B main origin/main 2>&1 || \
	{ echo "ERROR: Git reset 실패 — 오염된 파일로 배포 불가"; exit 1; }
else
	echo "WARNING: Git fetch 실패 또는 origin/main 브랜치를 찾을 수 없음, 기존 코드 사용"
fi

echo "=== 현재 Git 상태 ==="
git log --oneline -1 2>/dev/null || echo "Git 로그 확인 실패"
git status --short 2>/dev/null || echo "Git 상태 확인 실패"

KUBECTL="sudo k3s kubectl"

dump_cluster_debug() {
	local dump_file="/tmp/k8s-cluster-info-dump-$(date +%Y%m%d%H%M%S).log"
	echo "=== cluster-info dump 수집 ==="
	if ${KUBECTL} cluster-info dump --request-timeout=30s > "${dump_file}" 2>&1; then
		echo "cluster-info dump 저장 완료: ${dump_file}"
	else
		echo "WARNING: cluster-info dump 수집 실패"
	fi
}

apply_manifest_with_replace_fallback() {
	local manifest_path="$1"
	local deploy_name="$2"
	local apply_output=""

	if apply_output=$(${KUBECTL} apply -f "${manifest_path}" --request-timeout=180s 2>&1); then
		echo "${apply_output}"
		return 0
	fi

	echo "${apply_output}"
	if echo "${apply_output}" | grep -q 'may not be specified when `value` is not empty'; then
		echo "기존 Deployment env 병합 충돌 감지: ${deploy_name}"
		echo "kubectl replace로 Deployment를 재적용합니다."
		if apply_output=$(${KUBECTL} replace -f "${manifest_path}" --request-timeout=180s 2>&1); then
			echo "${apply_output}"
			return 0
		fi
		echo "${apply_output}"
		dump_cluster_debug
		return 1
	fi

	echo "배포 실패, 재시도 중..."
	sleep 5
	if apply_output=$(${KUBECTL} apply -f "${manifest_path}" --request-timeout=180s 2>&1); then
		echo "${apply_output}"
		return 0
	fi

	echo "${apply_output}"
	dump_cluster_debug
	return 1
}

echo "=== K3s 클러스터 상태 확인 ==="
export DEBIAN_FRONTEND=noninteractive
sudo systemctl status k3s --no-pager 2>/dev/null | head -5 || echo "K3s 서비스 상태 확인 실패 (무시)"

echo "=== kubectl 클러스터 정보 확인 ==="
${KUBECTL} cluster-info --request-timeout=20s || {
	echo "k3s kubectl cluster-info 실패"
	exit 1
}

${KUBECTL} version --client --request-timeout=20s || echo "kubectl version 확인 실패"
${KUBECTL} get nodes --request-timeout=20s || {
	echo "노드 확인 실패"
	exit 1
}

# 이미지 태그
echo "=== 이미지 태그 업데이트 (IMAGE_TAG=${IMAGE_TAG}) ==="
if [ -f k8s/deployment.yaml ]; then
	sed -i "s|image: gilhwanjeon/smw-app:.*|image: gilhwanjeon/smw-app:${IMAGE_TAG}|g" k8s/deployment.yaml
	echo "app 이미지 OK"
	grep "image:" k8s/deployment.yaml || true
else
	echo "WARNING: k8s/deployment.yaml 없음"
fi
if [ -f k8s/batch-deployment.yaml ]; then
	sed -i "s|image: gilhwanjeon/smw-app:.*|image: gilhwanjeon/smw-app:${IMAGE_TAG}|g" k8s/batch-deployment.yaml
	echo "batch 이미지 OK"
	grep "image:" k8s/batch-deployment.yaml | head -3 || true
fi

# batch 목표 replica (staged 2단계에서 사용)
BATCH_REPLICA_TARGET=2
if [ -f k8s/batch-deployment.yaml ]; then
	BATCH_REPLICA_TARGET=$(awk '/^spec:/{a=1} a&&/replicas:/{print $2; exit}' k8s/batch-deployment.yaml)
	[ -z "$BATCH_REPLICA_TARGET" ] && BATCH_REPLICA_TARGET=2
fi
echo "batch 목표 replica(매니페스트): ${BATCH_REPLICA_TARGET}"

echo "=== Redis 배포 ==="
if [ -f k8s/redis.yaml ]; then
	${KUBECTL} apply -f k8s/redis.yaml --request-timeout=180s || { echo "WARNING: redis 적용 실패"; }
else
	echo "WARNING: k8s/redis.yaml 없음"
fi

# ----- staged: batch 0 -> app -> batch 1 -> batch target -----
if [ "$K8S_ROLLOUT_MODE" = "staged" ]; then
	echo "=== [staged] smw-batch replica 0 ==="
	if ${KUBECTL} get deployment smw-batch 2>/dev/null; then
		${KUBECTL} scale deployment smw-batch --replicas=0 --request-timeout=180s
		_w=0
		while [ "$_w" -lt 180 ]; do
			_n=$(${KUBECTL} get pods -l app=smw-batch --no-headers 2>/dev/null | wc -l)
			if [ "$_n" -eq 0 ] || [ -z "$_n" ]; then
				break
			fi
			echo "  batch pod 대기... (${_n}개)"
			sleep 2
			_w=$((_w + 2))
		done
	else
		echo "smw-batch Deployment 없음(스킵)"
	fi

	echo "=== [staged] smw-app 배포·재시작 ==="
	apply_manifest_with_replace_fallback k8s/deployment.yaml smw-app || { echo "ERROR: app 배포 실패"; exit 1; }
	if [ -f k8s/smwr-backend-ingress.yaml ]; then
		${KUBECTL} apply -f k8s/smwr-backend-ingress.yaml --request-timeout=180s || { echo "WARNING: ingress"; }
	fi
	${KUBECTL} rollout restart deployment/smw-app --request-timeout=180s || { echo "WARNING: app restart"; }
	${KUBECTL} rollout status deployment/smw-app --timeout=5m || { echo "WARNING: app 롤아웃 대기 초과"; }

	echo "=== [staged] smw-batch 적용, replica 1 -> 목표 ==="
	if [ -f k8s/batch-deployment.yaml ]; then
		apply_manifest_with_replace_fallback k8s/batch-deployment.yaml smw-batch || { echo "WARNING: batch apply"; }
		${KUBECTL} scale deployment smw-batch --replicas=1 --request-timeout=180s
		${KUBECTL} rollout status deployment/smw-batch --timeout=5m || { echo "WARNING: batch(1) 대기"; }
		if [ "${BATCH_REPLICA_TARGET}" -gt 1 ] 2>/dev/null; then
			echo "=== [staged] smw-batch replica -> ${BATCH_REPLICA_TARGET} ==="
			${KUBECTL} scale deployment smw-batch --replicas="${BATCH_REPLICA_TARGET}" --request-timeout=180s
			${KUBECTL} rollout status deployment/smw-batch --timeout=5m || { echo "WARNING: batch(목표) 대기"; }
		fi
	else
		echo "WARNING: batch 매니페스트 없음"
	fi

else
	# parallel (기존: app + batch apply 후 둘 다 restart)
	echo "=== [parallel] smw-app 배포 ==="
	apply_manifest_with_replace_fallback k8s/deployment.yaml smw-app || { echo "ERROR: 배포 실패"; exit 1; }
	if [ -f k8s/smwr-backend-ingress.yaml ]; then
		${KUBECTL} apply -f k8s/smwr-backend-ingress.yaml --request-timeout=180s || { echo "WARNING: backend ingress"; }
	fi

	echo "=== [parallel] smw-batch 배포 ==="
	if [ -f k8s/batch-deployment.yaml ]; then
		apply_manifest_with_replace_fallback k8s/batch-deployment.yaml smw-batch || { echo "WARNING: batch deploy"; }
	else
		echo "WARNING: k8s/batch-deployment.yaml 없음"
	fi

	echo "=== [parallel] Pod 재시작 (최신 이미지) ==="
	for _DEP in smw-app smw-batch; do
		${KUBECTL} rollout restart deployment/${_DEP} --request-timeout=180s || {
			echo "WARNING: ${_DEP} restart"
			${KUBECTL} delete pod -l app=${_DEP} --request-timeout=180s || true
		}
	done

	${KUBECTL} get pods --request-timeout=30s || true
	${KUBECTL} get services --request-timeout=30s || true

	echo "=== [parallel] 배포 완료 대기 (최대 5분) ==="
	for _DEP in smw-app smw-batch; do
		${KUBECTL} rollout status deployment/${_DEP} --timeout=5m || { echo "WARNING: ${_DEP} timeout"; }
	done
fi

# 공통: 상태 로그
echo "=== Pod 상태 ==="
${KUBECTL} get pods -l app=smw-app -o wide --request-timeout=30s || true
for _APP in smw-app smw-batch smw-redis; do
	_POD_NAME=$(${KUBECTL} get pods -l app=${_APP} -o jsonpath='{.items[0].metadata.name}' --request-timeout=15s 2>/dev/null || echo "")
	if [ -n "$_POD_NAME" ] && [ "$_POD_NAME" != "null" ]; then
		echo "=== ${_APP} 로그 (최근 10줄) ==="
		${KUBECTL} logs --tail=10 "$_POD_NAME" --request-timeout=15s 2>/dev/null || true
	fi
done

API_READY_PODS=$(${KUBECTL} get pods -l app=smw-app -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' --request-timeout=15s 2>/dev/null | wc -w)
BATCH_READY_PODS=$(${KUBECTL} get pods -l app=smw-batch -o jsonpath='{.items[?(@.status.phase=="Running")].metadata.name}' --request-timeout=15s 2>/dev/null | wc -w)
if [ "$API_READY_PODS" -gt 0 ]; then
	echo "✅ 배포 성공: api=${API_READY_PODS}개, batch=${BATCH_READY_PODS}개 Pod Running (mode=${K8S_ROLLOUT_MODE})"
else
	echo "❌ API Pod Running 0"
	exit 1
fi
echo "=== 배포 작업 완료 ==="
