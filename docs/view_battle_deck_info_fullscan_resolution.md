# view_battle_deck_info 풀스캔 해소 작업 기록

## 배경

`view_battle_deck_info`는 UNLOGGED TABLE (86,518행, ~105MB).  
몬스터 상세 페이지(`/monster-detail/{id}/matchup`) 호출 시 이 테이블 풀스캔으로 30초 타임아웃 발생.

---

## 원인

### 1. 플래그 미설정 — 항상 레거시 경로 진입

`summonerswarServiceImpl`의 `@Value("${smw.siege.use-defense-deck-stats:false}")` 기본값이 `false`라서  
환경변수 `SMW_SIEGE_USE_DEFENSE_DECK_STATS` 미설정 시 모든 쿼리가 `view_battle_deck_info` 직접 조회 경로로 진입.

### 2. 인덱스 부재

`view_battle_deck_info`에 인덱스가 없어 `type`, `monster_id_1`, `LEAST/GREATEST(monster_id_2, monster_id_3)` 필터가 항상 풀스캔.

### 3. 데드코드 레거시 SQL 조각

`selectEnemyTeamListLegacy`, `selectTotalPageCountLegacy` — 더 이상 사용되지 않는 SQL 조각이 XML에 잔존.

---

## 조치 내용

### A. 플래그 완전 제거 (summonerswarServiceImpl.java)

- `@Value("${smw.siege.use-defense-deck-stats:false}")` 필드 제거
- `applySiegeDeckStatsQueryFlag(param)` 메서드 제거
- 호출 5곳 전부 제거

**결과**: `siege_defense_deck_stats` 집계 경로가 무조건 사용됨.

### B. XML 분기 조건 단순화 (summonerswar.xml)

| 쿼리 ID | 변경 전 | 변경 후 |
|--------|---------|---------|
| `selectEnemyTeamList` | `use_siege_defense_deck_stats=="Y"` 조건의 `<choose>` | `<choose>` 제거, stats 경로만 유지 |
| `selectTotalPageCount` | 동일 | 동일 |
| `selectMonsterDetailList` | `use_siege_defense_deck_stats AND match_id==null AND guild_ids==0` | `match_id==null AND guild_ids==0` |
| `selectMonsterDetailTeamList` | 동일 | 동일 |
| `selectMonsterDetailTeamListCount` | 동일 | 동일 |

### C. 데드코드 제거 (summonerswar.xml)

- `<sql id="selectEnemyTeamListLegacy">` 전체 삭제
- `<sql id="selectTotalPageCountLegacy">` 전체 삭제

### D. 인덱스 추가 (migrate_view_battle_deck_info_idx.sql)

```sql
-- type + monster_id_1 필터 (guild_ids 지정 경로)
CREATE INDEX IF NOT EXISTS idx_vbdi_type_monster1
    ON public.view_battle_deck_info (type, monster_id_1);

-- match_id + log_id 조인 (selectRecordUserDetail, DELETE)
CREATE INDEX IF NOT EXISTS idx_vbdi_match_log
    ON public.view_battle_deck_info (match_id, log_id);

-- match_id + log_timestamp + type (이중 JOIN: selectMonsterDetailTeamList <otherwise>,
--   insertSiegeDefenseDeckStatsFromBattleLogs)
CREATE INDEX IF NOT EXISTS idx_vbdi_match_ts_type
    ON public.view_battle_deck_info (match_id, log_timestamp, type);

-- LEAST/GREATEST 표현식 복합 (match_id 없이 monster 필터 조합 시)
CREATE INDEX IF NOT EXISTS idx_vbdi_type_m1_least_greatest
    ON public.view_battle_deck_info (
        type, monster_id_1,
        LEAST(monster_id_2, monster_id_3),
        GREATEST(monster_id_2, monster_id_3)
    );
```

---

## 현재 view_battle_deck_info 접근 경로 현황

### 풀스캔 없음 (인덱스 커버됨)

| 쿼리 | 진입 조건 | 사용 인덱스 |
|------|-----------|------------|
| `selectMonsterDetailList` `<otherwise>` | match_id 지정 또는 guild_ids 비어있지 않음 | `idx_vbdi_type_m1_least_greatest` |
| `selectMonsterDetailTeamList` `<otherwise>` | 동일 | `idx_vbdi_match_ts_type` (double JOIN) |
| `selectMonsterDetailTeamListCount` `<otherwise>` | 동일 | `idx_vbdi_match_ts_type` |
| `selectRecordUserDetail` | 항상 (wizard_id 기준 JOIN) | `idx_vbdi_match_log` |
| `insertSiegeDefenseDeckStatsFromBattleLogs` | 배치 실행 시 | `idx_vbdi_match_ts_type` |
| `deleteGuildSiegeBattleDeckByMatchId` | match_id 단건 삭제 | `idx_vbdi_match_log` |
| `insertGuildSiegeBattleDeck` / `insertGuildSiegeBattleDeckBatch` | INSERT (읽기 없음) | — |

### 일반 조회 경로 (siege_defense_deck_stats 사용 — view_battle_deck_info 미접근)

| 쿼리 | 조건 |
|------|------|
| `selectEnemyTeamList` | 항상 |
| `selectTotalPageCount` | 항상 |
| `selectMonsterDetailList` | match_id 없음 AND guild_ids 없음 |
| `selectMonsterDetailTeamList` | 동일 |
| `selectMonsterDetailTeamListCount` | 동일 |

---

---

## 추가 풀스캔 조치 (기타 테이블)

### sys_user_login_log (dashboard.xml)

**문제**: `TO_DATE(SUBSTRING(login_date, 1, 8), 'YYYYMMDD')` 표현식 WHERE → 인덱스 사용 불가  
**조치**: 문자열 범위 비교로 변경 → `login_date >= TO_CHAR(CURRENT_DATE, 'YYYYMMDD')`  
**DDL**: `migrate_sys_user_login_log_idx.sql` — `idx_sys_user_login_log_date (login_date)`

### 검토 후 제외 항목

| 패턴 | 이유 |
|------|------|
| `adminMonster.xml` LIKE '%..%' | admin UI, monster 테이블 ~2,000행 |
| `accountSummary.xml` ILIKE '%keyword%' | 검색 UI (사용자 입력 필수), 허용 범위 |
| `rta-queries-batch-meta.xml` ILIKE on search_snap | GIN trigram 인덱스 이미 적용 (`idx_rta_agg_summoner_search_snap_name_trgm`) |
| `siege_defense_deck_stats` view_all_guilds=true | ~31,000행 + season_yyyymm 필터 상시 적용 |
| `guild.xml`, `guildJoinApplication.xml` | 소형 테이블 |

---

## DB 적용 필요 파일

```
SMWR_WAS/sql/ddl/migrate_view_battle_deck_info_idx.sql
SMWR_WAS/sql/ddl/migrate_siege_deck_user_vote_idx.sql
SMWR_WAS/sql/ddl/migrate_sys_user_login_log_idx.sql
```
