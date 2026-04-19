# RTA 전체 집계 파이프라인 상세 명세

| 항목 | 내용 |
|------|------|
| Quartz Job 클래스 | `com.smw.monster.batch.RtaUnifiedPipelineAggJob` |
| 배치 표시명 (`getBatchName`) | RTA 전체 집계 파이프라인 |
| 동시 실행 | **`@DisallowConcurrentExecution`** — 동일 Job 인스턴스는 한 번에 하나만 실행 |
| 스케줄 | DB `sys_batch_config` 의 `cron_expr` (주석 예: 약 5분마다, `bat_id` 예시 10001 — **실제 PK·크론은 DB 기준**) |
| 설정 바인딩 | `@EnableConfigurationProperties` — `RtaBatchProperties`, `RtaRawApplyProperties` (`Application.java`) |

---

## 1. 목적

한 번의 스케줄 실행으로 다음을 **순서대로** 처리한다.

1. **리플레이 raw**(`ranker_rtpvp_replay_raw`) 중 미적용 건을 **정규화 테이블**(`rta_match` 계열)로 반영  
2. **시너지·카운터 등 미집계 경기**를 `rta_agg_synergy_combo` 등에 반영 (`synergy_applied_at` 갱신)  
3. (선택) **SWEX 기반 보유 몬스터 집계** `user_monster_owned_agg`  
4. (선택) **레거시 몬스터 통계·티어 일별** — 기본은 생략하고 별도 Job 권장  
5. 마지막에 **RTA 조회 캐시 전역 무효화**

### 1.1 이 Job이 하지 않는 것

| 항목 | 비고 |
|------|------|
| `rta_match` 없는 자식 테이블 고아 삭제 | `ArenaRtaOrphanCleanupBatchJob` + `deleteArenaRtaOrphanChildrenGlobal` |
| 랭크컷 앵컷/등급 컷 스냅샷 | `RtaRankCutSnapshotAggJob` |
| 티어 일별(`rta_agg_tier_daily`) 전량 재적재 | 기본 스킵 → `RtaTierDailyAggJob` 권장 |
| 소환사 랭킹 agg 재적재 | 통합 파이프라인에서 제거됨(`rebuildSummonerRankingAgg`는 no-op) |

---

## 2. 파이프라인 단계 (로그 기준 `[n/3]`)

상수 `PIPELINE_STEPS = 3`. 단계 번호는 스킵 여부와 무관하게 **1→2→3** 고정.

### 2.1 [1/3] RTA raw 정규화

| 항목 | 내용 |
|------|------|
| 진입점 | `RtaBatchAggregationService.drainReplayRawPending(summonerswarService)` |
| 실제 처리 | `summonerswarService.applyPendingArenaReplayRawFromDb()` |
| SELECT 조건 | `apply_status IN ('pending','failed')`, `ORDER BY rid`, **`LIMIT maxRowsPerRun`** |
| 반복 | 빈 결과가 나올 때까지 동일 호출 내에서 SELECT→처리 반복, 최대 **`maxBatchesPerJob`** 회 (무한 루프 방지) |
| 반환 로그 | `RawApplyDrainResult(totalApplied, stopReason)` — `totalApplied==0` 이면 `stopReason` ≈ 적용할 raw 없음 |

**설정 (`smw.rta.raw-apply`)**

| 프로퍼티 | 기본(예) | 설명 |
|----------|----------|------|
| `max-rows-per-run` | 1000 | 매회 SELECT 상한 |
| `max-batches-per-job` | 2000 | 위 SELECT→처리 반복 상한 |
| `apply-chunk-size` | 1000 | 정규화 INSERT 청크 |
| `bulk-insert-chunk-size` | 50 | VALUES 폴백 시 문당 행 수 |
| `copy-bulk-insert-enabled` | true | COPY+TEMP 경로 |
| `copy-bulk-synchronous-commit-off` | true | COPY 구간 `synchronous_commit=off` |
| `fail-fast-on-error` | true | 첫 오류 시 Job 실패 |

환경변수 접두: `SMW_RTA_RAW_APPLY_*`, `SMW_RTA_ARENA_COPY_*` 등 (`application.yml` 참고).

---

### 2.2 [2/3] 시너지 집계 + (선택) 보유 몬스터

#### A) 시너지 drain

| 항목 | 내용 |
|------|------|
| 진입점 | `RtaBatchAggregationService.drainSynergyPending(...)` |
| 대상 rid | `RtaMapper.selectPendingSynergyAggRids(batchSize)` — `rta_match.synergy_applied_at IS NULL`, `replay_id` 오름차순, `LIMIT synergyBatchSize` |
| 라운드 | `batchSize` 만큼 rid 조회 → `RtaSynergyAggService.applySynergyBatch(rids)` → 성공 시 시너지/카운터 staging·UPSERT·완료 표시. **라운드 최대 `synergyMaxRoundsPerJob`** |
| 라운드 간 대기 | `synergyPauseMsBetweenRounds` (ms), 0이면 생략 |
| 캐시 | 통합 Job에서는 `evictCachesEachRound=false` — 라운드마다 캐시 비우지 않음 |
| 종료 사유 | pending 없음 / 라운드 상한으로 pending 잔여 / 완료 등 (`SynergyDrainResult.stopReason`) |

**설정 (`smw.rta.batch`)**

| 프로퍼티 | 기본(예) | 환경변수 예 |
|----------|----------|-------------|
| `synergy-batch-size` | 1000 | `SMW_RTA_SYNERGY_BATCH_SIZE` |
| `synergy-max-rounds-per-job` | 40 | `SMW_RTA_SYNERGY_MAX_ROUNDS` |
| `synergy-pause-ms-between-rounds` | 0 | `SMW_RTA_SYNERGY_PAUSE_MS` |

시너지·카운터 대량 경로는 `RtaSynergyAggServiceImpl` 내부의 COPY 스테이징·`smw.rta.synergy-agg.*`, `smw.rta.counter-agg.*` 로 튜닝.

#### B) 사용자 보유 몬스터 (시너지 직후, 같은 step 번호로 로그)

| 조건 | `skipUserMonsterOwnedAggInUnifiedJob == false` 일 때만 실행 |
| 처리 | `AccountSummaryMapper.deleteAllUserMonsterOwnedAgg()` → `insertUserMonsterOwnedAggFromSwex()` → `countUserMonsterOwnedAgg()` |
| 기본값 | `application.yml` 에서 스킵 **true** (`SMW_RTA_UNIFIED_SKIP_USER_OWNED_AGG`) — 운영에서 false 로 켜는 패턴 |

---

### 2.3 [3/3] 부가 집계 (설정 시만)

| 플래그 | 처리 | 구현 |
|--------|------|------|
| `skipMonsterStatsInUnifiedJob == false` | 레거시 몬스터 통계 | `rebuildMonsterStatsAgg` — 현재 **항상 (0,0) no-op**, API는 `rta_agg_synergy_combo` |
| `skipTierAggDailyInUnifiedJob == false` | 티어 일별 | `rebuildTierAggDaily` — 시즌별 `rta_agg_tier_daily` 재적재(무거움) |

기본값은 둘 다 **스킵(true)**. 둘 다 꺼져 있으면 안내 로그 한 줄(티어는 `RtaTierDailyAggJob`, 랭크컷은 `RtaRankCutSnapshotAggJob`).

---

### 2.4 종료 처리

- `RtaCacheEvictor.evictAllRtaCaches()` — RTA 관련 조회 캐시 무효화  
- 로그: `[종료] (3/3) RTA 조회 캐시 무효화`

---

## 3. 의존 컴포넌트 요약

| 빈/타입 | 역할 |
|---------|------|
| `summonerswarService` | raw 정규화 |
| `RtaMapper` | pending rid 조회, 티어/몬스터 등 집계 SQL |
| `RtaSynergyAggService` | `applySynergyBatch` |
| `RtaBatchAggregationService` | drain·rebuild 래퍼 |
| `RtaCacheEvictor` | 캐시 무효화 |
| `AccountSummaryMapper` | SWEX → `user_monster_owned_agg` |
| `RtaBatchProperties` | `smw.rta.batch.*` |
| `RtaRawApplyProperties` | `smw.rta.raw-apply.*` |

---

## 4. 운영 체크리스트

1. **`sys_batch_config`** 에 본 Job FQCN 등록, `use_yn`, `cron_expr` 확인  
2. **`smw.batch.quartz.enabled`** — 자동 크론 등록 여부 (`false` 면 수동 `/api/v1/batch` 등)  
3. 백필·대량 시: `application-bulkload.yml` 또는 `SMW_RTA_*` 로 raw·시너지 라운드 상향  
4. DB 부하: `synergy-pause-ms-between-rounds`, `max-rows-per-run`, `synergy-max-rounds-per-job` 조정  
5. 고아 행·티어 일별·랭크컷은 **별도 Job** 과 역할 분리 유지  

---

## 5. 관련 문서·소스

| 경로 | 내용 |
|------|------|
| `docs/배치명세서.md` | 전체 Quartz Job 목록 |
| `RtaUnifiedPipelineAggJob.java` | 본 파이프라인 구현 |
| `RtaBatchAggregationService.java` | drain/rebuild 공통 로직 |
| `summonerswarServiceImpl.applyPendingArenaReplayRawFromDb` | raw 반복 적용 |
| `application.yml` — `smw.rta` | 기본 설정·환경변수 |

---

*본 문서는 저장소 소스 기준이며, 운영 `bat_id`·크론·DB 데이터는 배포 환경을 정본으로 한다.*
