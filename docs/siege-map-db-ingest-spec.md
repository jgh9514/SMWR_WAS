# 점령전 지도 DB 적재 명세서 (수집 프로그램용)

**대상 독자:** 게임 API 프록시 로그를 PostgreSQL에 적재하는 배치/실시간 수집 프로그램  
**SMWR 웹:** 적재된 데이터만 조회 (`/siege/map`, `/siege/map/history`) — **프론트 로그 파일 업로드 없음**  
**DDL:**  
- `SMWR_WAS/sql/ddl/migrate_siege_map_snapshot.sql` (필수)  
- `SMWR_WAS/sql/ddl/migrate_siege_map_base_defense.sql` (거점 방덱 수집 시)  
- `SMWR_WAS/sql/ddl/migrate_siege_collector_full_storage.sql` (프록시 수집기·전투 로그·리플레이·아카이브)

**프록시 수집기 전체 명세:** [siege-collector-proxy-ingest-spec.md](./siege-collector-proxy-ingest-spec.md)

---

## 1. 수집 대상 API 요약

| 우선순위 | API | 수집 방식 | 용도 |
|---------|-----|----------|------|
| **필수** | `GetGuildSiegeMatchupInfo` | 매치당 **주기 폴링**(권장 30초) | 실시간 지도·3길드 점수·거점 39개 상태·타이머 |
| **조건부** | `GetGuildSiegeBaseDefenseUnitList` | **조회 가능한 거점**에 대해서만 | 거점 클릭 시 노출되는 방덱 3마리·전적 |

### 1.1 수집하지 않는 API (본 명세 범위 외)

- `GetGuildSiegeBattleLog` — 전투 로그(`log_list`)는 기존 점령전 업로드·`battle_log_list` 경로 사용
- `GetGuildSiegeRankingInfo` — 월드 랭킹 전용, 지도와 무관
- `UpdateAlive`, 던전 전투 등 — 노이즈, 무시

---

## 2. 공통 규칙

### 2.1 응답만 적재

- **Request JSON은 DB에 넣지 않는다.**
- **Response** 중 `command` 일치 + `ret_code === 0` 인 본문만 파싱한다.
- `ret_code != 0` 이면 해당 호출은 스킵(로그만 남김).

### 2.2 시각 키 `captured_at`

- 응답 JSON의 **`tvalue`**(유닉스 초)를 스냅샷 시각으로 사용한다.
- 없으면 `tvaluelocal`, 그것도 없으면 수집 시각(서버 clock) — **가급적 API `tvalue` 사용**.

### 2.3 중복 처리

| 테이블 | 유니크 키 | 중복 시 |
|--------|-----------|---------|
| `siege_map_snapshot` | `(match_id, captured_at)` | **INSERT 스킵** (기존 `snapshot_id` 유지, guild/base 자식도 추가하지 않음) |
| `siege_map_base_defense_capture` | `(match_id, base_number, captured_at)` | 동일 |

### 2.4 `match_id` / `season_yyyymm`

- `match_id`: `match_info.match_id` (예: `2026050401000008`, 문자열 32자 내외)
- `season_yyyymm`: **`match_id` 앞 6자리** (예: `202605`)

### 2.5 적재 순서 (트랜잭션 권장)

**MatchupInfo 1건:**

1. `siege_map_match` UPSERT  
2. `siege_map_snapshot` INSERT → `snapshot_id` 확보  
3. `siege_map_snapshot_guild` 배치 INSERT (3행 내외)  
4. `siege_map_snapshot_base` 배치 INSERT (39행 내외)  
5. `siege_map_match.snapshot_count` 등 메타 갱신 (SMWR 참고 구현과 동일)

**BaseDefenseUnitList 1건:**

1. `siege_map_base_defense_capture` INSERT → `capture_id`  
2. wizard / deck / unit / assign / deck_status 자식 INSERT  
3. (선택) 직전 Matchup 스냅샷의 `id`를 `matchup_snapshot_id`에 기록

### 2.6 `source` 컬럼

- 수집기 이름·채널 구분용. 예: `db_ingest`, `proxy_collector`, `batch_v1`

---

## 3. GetGuildSiegeMatchupInfo (필수)

### 3.1 호출·트리거

- 점령전 **진행 중**인 `siege_id` / `match_id`에 대해 클라이언트가 쓰는 것과 같이 **주기 호출**.
- 샘플 로그 기준 **약 30초 간격**.
- Request 예: `{ "command":"GetGuildSiegeMatchupInfo", "siege_id": 2026050401, ... }`

### 3.2 Response → 테이블 매핑

#### `siege_map_match` (매치당 1행, UPSERT)

| DB 컬럼 | JSON 경로 | 비고 |
|---------|-----------|------|
| `match_id` | `match_info.match_id` | PK |
| `siege_id` | `match_info.siege_id` | |
| `season_yyyymm` | `match_id` 앞 6자 | |
| `rating_id` | `match_info.rating_id` | 문자열 저장 |
| `match_type` | `match_info.match_type` | |
| `match_start_time` | `match_info.match_start_time` | unix sec |
| `match_finish_time` | `match_info.match_finish_time` | unix sec |
| `first_snapshot_at` | 첫 스냅샷 시 | `to_timestamp(captured_at)` |
| `last_snapshot_at` | 매 스냅샷 시 | 갱신 |
| `snapshot_count` | 적재 성공 시 +1 | |

#### `siege_map_snapshot` (폴링 1회 = 1행)

| DB 컬럼 | JSON 경로 |
|---------|-----------|
| `match_id` | `match_info.match_id` |
| `captured_at` | `tvalue` |
| `war_rest_start_time` | `setup_values.war_rest_start_time` |
| `war_rest_finish_time` | `setup_values.war_rest_finish_time` |
| `max_match_score` | `setup_values.max_match_score` |
| `max_deck_count_per_member` | `setup_values.max_deck_count_per_member` |
| `max_attack_unit_count` | `setup_values.max_attack_unit_count` |
| `source` | 수집기 지정 |

#### `siege_map_snapshot_guild` — `guild_list[]` 각 원소

| DB 컬럼 | JSON 필드 |
|---------|-----------|
| `snapshot_id` | FK |
| `guild_id` | `guild_id` |
| `pos_id` | `pos_id` (1~3, 지도 색·순서) |
| `guild_name` | `guild_name` |
| `match_score` | `match_score` |
| `match_score_increment` | `match_score_increment` |
| `match_rank` | `match_rank` |
| `play_member_count` | `play_member_count` |
| `attack_count` | `attack_count` |
| `attack_unit_count` | `attack_unit_count` |
| `disqualified` | `disqualified` |

#### `siege_map_snapshot_base` — `base_list[]` 각 원소 (거점 1~39)

| DB 컬럼 | JSON 필드 | 비고 |
|---------|-----------|------|
| `snapshot_id` | FK | |
| `base_number` | `base_number` | 1~39 |
| `base_type` | `base_type` | 1=본진, 2=일반, 3=요새(게임 정의) |
| `guild_id` | `guild_id` | 소유 길드 |
| `base_status` | `base_status` | 0/1/2 — UI·타이머 해석용 |
| `battle_start_time` | `battle_start_time` | 진행 중 거점: 종료 시각 추정에 사용 |
| `construct_time` | `construct_time` | |

**UI 참고 (SMWR):** `base_status === 1` 이고 `battle_start_time > captured_at` 이면 남은 초 ≈ `battle_start_time - captured_at`.

#### MatchupInfo에 있으나 **본 DDL에 미저장** 필드 (필요 시 JSON 보관 테이블 별도 검토)

- `defense_deck_list`, `defense_deck_assign_list`, `defense_deck_status_list` (수백 행, 지도 UI 미사용)
- `wizard_info_list`, `member_info_list`, `my_defense_*`, `alert_*` 등

→ **1차 수집 범위는 위 4테이블만**이면 지도·히스토리 요구사항 충족.

### 3.3 수집 주기·볼륨 가이드

- 1매치 × 30초 × 24시간 ≈ 2,880 스냅샷/일  
- 스냅샷당 guild 3 + base 39 ≈ 42행 → **일 약 12만 자식행/매치**  
- 디스크·인덱스 감안해 **종료된 매치는 폴링 중단** 권장 (`match_finish_time < now`).

---

## 4. GetGuildSiegeBaseDefenseUnitList (조건부)

### 4.1 언제 수집할지 (“조회 가능한 거점”)

게임은 **거점을 열 때마다** Request에 `base_number`를 넣어 호출한다. 수집 프로그램은 아래 중 **하나 이상**으로 범위를 정하면 된다.

| 전략 | 설명 | 권장 |
|------|------|------|
| **A. 프록시 실호출** | 실제로 발생한 `GetGuildSiegeBaseDefenseUnitList` Response만 저장 | 구현 단순, 유저가 연 거점만 |
| **B. Matchup 연동** | Matchup 직후 `visible_base_number_list` / `separated_base_number_list` 거점만 **주기적으로** API 재호출 | 커버리지↑, 계정·Rate limit 주의 |
| **C. 하이브리드** | A 기본 + B는 `base_status=1`(공성 중) 등 변경된 거점만 | **권장** |

**“조회 가능” 판단 힌트 (MatchupInfo 기준):**

- `visible_base_number_list` — 현재 맵에서 보이는 거점 번호 배열  
- `separated_base_number_list` — 분리/특수 거점  
- (없으면) `base_list`에서 `base_status != 2` 이거나 최근 `battle_start_time` 변경된 `base_number`

**Rate limit:** 거점 39개 전체를 매 30초마다 돌리지 말 것. **변경·노출된 거점만**, 최소 **거점당 5~10분 쿨다운** 권장.

### 4.2 Request (참고, DB 미저장)

```json
{
  "command": "GetGuildSiegeBaseDefenseUnitList",
  "base_number": 33,
  "siege_id": "…",
  …
}
```

- `base_number`: 1~39  
- 동일 `match_id`는 응답의 `defense_deck_status_list[].match_id` 또는 직전 Matchup의 `match_info.match_id`로 확정.

### 4.3 Response → 테이블 매핑

#### `siege_map_base_defense_capture`

| DB 컬럼 | 값 |
|---------|-----|
| `match_id` | 응답 `defense_deck_status_list[0].match_id` 또는 수집 컨텍스트 |
| `base_number` | Request `base_number` |
| `captured_at` | `tvalue` |
| `matchup_snapshot_id` | (선택) 같은 사이클 Matchup `siege_map_snapshot.id` |
| `source` | 수집기 |

#### `siege_map_base_defense_wizard` — `wizard_info_list[]`

| DB 컬럼 | JSON |
|---------|------|
| `wizard_id` | `wizard_id` |
| `channel_uid` | `channel_uid` |
| `wizard_name` | `wizard_name` |
| `wizard_level` | `wizard_level` |
| `rating_id` | `rating_id` |
| `guild_id` | `guild_id` |

#### `siege_map_base_defense_deck` — `defense_deck_list[]`

| DB 컬럼 | JSON |
|---------|------|
| `deck_id` | `deck_id` |
| `wizard_id` | `wizard_id` |
| `total_win_count` | `total_win_count` |
| `total_draw_count` | `total_draw_count` |
| `total_lose_count` | `total_lose_count` |
| `win_count` | `win_count` (기간/초기화 후) |
| `draw_count` | `draw_count` |
| `lose_count` | `lose_count` |
| `total_count` | `total_count` |
| `winning_rate` | `winning_rate` |

#### `siege_map_base_defense_unit` — `defense_unit_list[]`

| DB 컬럼 | JSON |
|---------|------|
| `deck_id` | `deck_id` |
| `pos_id` | `pos_id` (1~3) |
| `unit_id` | `unit_info.unit_id` |
| `unit_master_id` | `unit_info.unit_master_id` |
| `unit_class` | `unit_info.class` |
| `unit_level` | `unit_info.unit_level` |
| `costume_master_id` | `unit_info.costume_master_id` |
| `trans_item_ids` | `unit_info.trans_item_master_id_list` → **쉼표 구분 문자열** (예: `12804,20016`) |

- 룬/아티 상세는 `defense_unit_equip_info_summary` — **1차 미저장** (비어 있는 경우 많음).

#### `siege_map_base_defense_deck_assign` — `defense_deck_assign_list[]`

| DB 컬럼 | JSON |
|---------|------|
| `base_number` | `base_number` |
| `deck_id` | `deck_id` |
| `status` | `status` |

#### `siege_map_base_defense_deck_status` — `defense_deck_status_list[]`

| DB 컬럼 | JSON |
|---------|------|
| `base_number` | `base_number` |
| `deck_id` | `deck_id` |
| `defense_guild_id` | `defense_guild_id` |
| `status` | `status` |
| `attack_guild_id` | `attack_guild_id` |
| `attack_wizard_id` | `attack_wizard_id` |
| `battle_start_time` | `battle_start_time` |

### 4.4 `deck_id` 의미 (수집 시 유의)

- 게임 내 **방덱 슬롯 고유 ID** (몬스터 3종 조합과 1:1 아님).  
- 동일 조합을 여러 슬롯에 복사하면 **`deck_id`는 다름**.  
- 공격 이력 초기화 시 `win_count`/`lose_count`만 리셋되고 **`deck_id`는 유지**되는 패턴.

---

## 5. 로그 파싱 (프록시 텍스트에서 추출 시)

### 5.1 파일 형식

```
API Command: GetGuildSiegeMatchupInfo - Mon May 18 2026 13:08:30 GMT+0900
Request:
{ ... }
Response:
{ ... }
```

- 한 Response JSON이 **한 줄**인 경우가 많음(대용량).  
- `API Command:` / `Request:` / `Response:` 라인은 무시.

### 5.2 추출 마커

| API | Response 마커 |
|-----|----------------|
| MatchupInfo | `"command":"GetGuildSiegeMatchupInfo","ret_code":0` |
| BaseDefense | `"command":"GetGuildSiegeBaseDefenseUnitList","ret_code":0` |

- 마커 앞에서 `{` 를 찾아 **괄호 균형**으로 JSON 한 덩어리 파싱 (문자열 내부 `{` 주의).

### 5.3 배치 vs 실시간

| 모드 | MatchupInfo | BaseDefense |
|------|-------------|-------------|
| 실시간 | 프록시 스트림에서 파싱 → 즉시 INSERT | 실호출분만 INSERT |
| 배치 | 로그 파일 NDJSON/블록 재생 | 동일 |

---

## 6. (선택) SMWR WAS HTTP API

DB 직접 INSERT 대신 WAS를 쓸 경우:

- `POST /api/v1/summonerswar/siege-map/snapshot-upload`  
- Body: `{ "matchup": { ... GetGuildSiegeMatchupInfo response ... }, "source": "proxy_collector" }`  
- BaseDefense는 **현재 HTTP API 없음** → **DB 직접 적재** 또는 추후 API 추가 협의.

---

## 7. 검증 체크리스트

- [ ] `migrate_siege_map_snapshot.sql` 적용  
- [ ] (방덱 수집 시) `migrate_siege_map_base_defense.sql` 적용  
- [ ] Matchup 1건 적재 후 `siege_map_snapshot` + guild 3 + base 39 확인  
- [ ] 동일 `(match_id, captured_at)` 재전송 시 중복 행 없음  
- [ ] SMWR `/siege/map/history` 에 매치 노출  
- [ ] `/siege/map/{matchId}` 슬라이더·거점 색·타이머 표시  
- [ ] BaseDefense: 거점 33 등 1건 적재 후 unit 15행(5덱×3) 근사 확인

---

## 8. ER 개요

```
siege_map_match (match_id PK)
    └── siege_map_snapshot (id, match_id, captured_at UNIQUE)
            ├── siege_map_snapshot_guild
            └── siege_map_snapshot_base

siege_map_base_defense_capture (id, match_id, base_number, captured_at UNIQUE)
    ├── siege_map_base_defense_wizard
    ├── siege_map_base_defense_deck
    ├── siege_map_base_defense_unit
    ├── siege_map_base_defense_deck_assign
    └── siege_map_base_defense_deck_status
```

---

## 9. 문의·변경

- 지도 UI 1차는 **MatchupInfo 스냅샷만** 사용. BaseDefense는 **거점 상세·분석 화면** 확장 시 소비.  
- DDL·필드 추가는 SMWR 저장소 `SMWR_WAS/sql/ddl/` 와 동기화 후 수집기 배포.

**문서 버전:** 2026-05-18
