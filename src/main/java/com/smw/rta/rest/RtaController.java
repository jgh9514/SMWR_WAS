package com.smw.rta.rest;

import com.smw.rta.service.RtaService;
import com.smw.rta.support.RtaTierKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.web.bind.annotation.RequestMethod.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Tag(name = "RTA", description = "RTA(Real-Time Arena) 관련 API")
@RestController
@RequestMapping("/api/v1/rta")
public class RtaController {

    @Autowired
    private RtaService rtaService;

    private static String pickSeasonCode(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        Object sc = param.get("seasonCode");
        if (sc == null) {
            sc = param.get("season_code");
        }
        if (sc == null) {
            return null;
        }
        String s = String.valueOf(sc).trim();
        return s.isEmpty() ? null : s;
    }

    /** 세부 티어 키 Ch1~G3 — body/query: tierKey / tier_key (구 tierLeague 도 허용) */
    private static String pickTierKey(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        Object t = param.get("tierKey");
        if (t == null) {
            t = param.get("tier_key");
        }
        if (t == null) {
            t = param.get("tierLeague");
        }
        if (t == null) {
            t = param.get("tier_league");
        }
        if (t == null) {
            return null;
        }
        String s = String.valueOf(t).trim();
        return s.isEmpty() ? null : s;
    }

    @Operation(summary = "RTA 매치 목록 조회", description = "RTA 매치 목록을 페이지네이션하여 조회합니다. seasonCode로 시즌 구간 필터. "
            + "목록은 최대 10페이지(요청 limit를 페이지 크기로 볼 때 offset이 10페이지 이상이면 DB 미조회, 빈 배열).")
    @RequestMapping(value = "/matches", method = { GET, POST })
    public ResponseEntity<List<Map<String, Object>>> getRtaMatches(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String seasonCode,
            @RequestParam(required = false) String tierKey) {
        
        try {
            int l = 50;
            int o = 0;
            if (param != null) {
                if (param.get("limit") != null) l = Integer.parseInt(param.get("limit").toString());
                if (param.get("offset") != null) o = Integer.parseInt(param.get("offset").toString());
            }
            if (limit != null) l = limit;
            if (offset != null) o = offset;
            String sc = seasonCode != null && !seasonCode.trim().isEmpty() ? seasonCode.trim() : pickSeasonCode(param);
            String tkRaw = tierKey != null && !tierKey.trim().isEmpty() ? tierKey.trim() : pickTierKey(param);
            String tk = RtaTierKeyUtil.normalize(tkRaw);
            
            List<Map<String, Object>> matches = rtaService.getRtaMatches(l, o, sc, tk);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("RTA matches 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "플레이어별 RTA 매치 조회", description = "특정 플레이어의 RTA 매치 목록을 조회합니다.")
    @PostMapping("/matches/player/{wizardId}")
    public ResponseEntity<List<Map<String, Object>>> getPlayerRtaMatches(
            @PathVariable String wizardId,
            @RequestBody(required = false) Map<String, Object> param) {
        
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            int limit = p.get("limit") != null ? Integer.parseInt(p.get("limit").toString()) : 50;
            int offset = p.get("offset") != null ? Integer.parseInt(p.get("offset").toString()) : 0;
            String sc = pickSeasonCode(p);
            
            List<Map<String, Object>> matches = rtaService.getPlayerRtaMatches(wizardId, limit, offset, sc);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("RTA player matches 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 목록 페이지 묶음", description = "매치 목록 + stats.hasMore(다음 페이지 여부). 시즌 전체 건수 COUNT는 하지 않음(부하). /rta 화면 권장(HTTP 1회).")
    @RequestMapping(value = "/page", method = { GET, POST })
    public ResponseEntity<Map<String, Object>> getRtaListPage(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) String seasonCode,
            @RequestParam(required = false) String tierKey) {
        try {
            int l = 50;
            int o = 0;
            if (param != null) {
                if (param.get("limit") != null) {
                    l = Integer.parseInt(param.get("limit").toString());
                }
                if (param.get("offset") != null) {
                    o = Integer.parseInt(param.get("offset").toString());
                }
            }
            if (limit != null) {
                l = limit;
            }
            if (offset != null) {
                o = offset;
            }
            String sc = seasonCode != null && !seasonCode.trim().isEmpty() ? seasonCode.trim() : pickSeasonCode(param);
            String tkRaw = tierKey != null && !tierKey.trim().isEmpty() ? tierKey.trim() : pickTierKey(param);
            String tk = RtaTierKeyUtil.normalize(tkRaw);
            Map<String, Object> body = rtaService.getRtaListPage(l, o, sc, tk);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("RTA page 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 통계 조회", description = "RTA 관련 통계 데이터를 조회합니다.")
    @PostMapping("/stats")
    public ResponseEntity<Object> getRtaStats(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) String tierKey) {
        
        try {
            String sc = pickSeasonCode(param);
            String tkRaw = tierKey != null && !tierKey.trim().isEmpty() ? tierKey.trim() : pickTierKey(param);
            String tk = RtaTierKeyUtil.normalize(tkRaw);
            Object stats = rtaService.getRtaStats(sc, tk);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("RTA stats 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Operation(summary = "RTA 테스트 데이터 조회", description = "RTA 테스트용 데이터를 조회합니다.")
    @PostMapping("/test")
    public ResponseEntity<Object> testRtaData() {
        
        try {
            Object testData = rtaService.testRtaData();
            return ResponseEntity.ok(testData);
        } catch (Exception e) {
            log.error("RTA test data 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 요약", description = "수집 리플레이 기준 최신 점수·글로벌 순위·승패 집계 (상세 헤더용)")
    @PostMapping("/player/{wizardId}/summary")
    public ResponseEntity<Map<String, Object>> getRtaPlayerSummary(
            @PathVariable String wizardId,
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            String sc = pickSeasonCode(param);
            Map<String, Object> response = rtaService.getRtaPlayerSummary(wizardId, sc);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA player summary 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 랭킹", description = "수집된 리플레이 기준 소환사별 최신 경기 점수 순 랭킹 (페이지네이션)")
    @PostMapping("/summoner-ranking")
    public ResponseEntity<Map<String, Object>> getRtaSummonerRanking(@RequestBody(required = false) Map<String, Object> param) {
        try {
            int limit = 50;
            int offset = 0;
            if (param != null) {
                if (param.get("limit") != null) {
                    limit = Integer.parseInt(param.get("limit").toString());
                }
                if (param.get("offset") != null) {
                    offset = Integer.parseInt(param.get("offset").toString());
                }
            }
            if (limit < 1) {
                limit = 50;
            }
            if (limit > 500) {
                limit = 500;
            }
            if (offset < 0) {
                offset = 0;
            }
            String sc = pickSeasonCode(param);
            String countryFilter = null;
            if (param != null) {
                Object co = param.get("country");
                if (co != null) {
                    String cf = co.toString().trim();
                    if (!cf.isEmpty()) {
                        countryFilter = cf;
                    }
                }
            }
            Map<String, Object> response = rtaService.getRtaSummonerRanking(limit, offset, sc, countryFilter);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA summoner ranking 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 검색", description = "시즌 구간 내 라이브 랭킹에서 닉네임 부분 일치 또는 위자드 ID 정확 일치 검색")
    @PostMapping("/summoner-search")
    public ResponseEntity<Map<String, Object>> searchRtaSummoners(@RequestBody(required = false) Map<String, Object> param) {
        try {
            String q = null;
            if (param != null && param.get("q") != null) {
                q = param.get("q").toString();
            }
            String sc = pickSeasonCode(param);
            Map<String, Object> response = rtaService.searchRtaSummoners(q, sc);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA summoner search 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 시즌 목록", description = "rta_season 조회. startYmdKst·lastInclusiveYmdKst·endExclusiveYmdKst는 Asia/Seoul 일자(티어 bucket과 동일 기준)")
    @GetMapping("/seasons")
    public ResponseEntity<Map<String, Object>> getRtaSeasons() {
        try {
            Map<String, Object> response = rtaService.getRtaSeasons();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA seasons 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 공식 티어 규칙(참고)", description = "rta_rating_grade 에 등록된 tier_key·승점·랭킹 설명")
    @GetMapping("/rating-grade-rules")
    public ResponseEntity<List<Map<String, Object>>> listRtaRatingGradeRules() {
        try {
            return ResponseEntity.ok(rtaService.listRtaRatingGradeReference());
        } catch (Exception e) {
            log.error("RTA rating grade rules 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 대시보드", description = "일별×티어 집계 전체 + 날짜 범위 (seasonCode로 시즌 필터)")
    @PostMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getRtaDashboard(@RequestBody(required = false) Map<String, Object> param) {
        try {
            String sc = pickSeasonCode(param);
            Map<String, Object> response = rtaService.getRtaDashboard(sc);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 몬스터별 통계 조회", description = "RTA 몬스터별 통계. limit=페이지 크기(기본 20), offset 또는 stats_offset=솔로 오프셋, duo_offset, trio_offset=듀오·트리오 오프셋")
    @PostMapping("/monster-stats")
    public ResponseEntity<Map<String, Object>> getRtaMonsterStats(@RequestBody Map<String, Object> param) {
        
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            int pageSize = clampInt(parseIntOpt(p.get("limit"), 20), 1, 500);
            Object statsOffObj = p.get("stats_offset");
            if (statsOffObj == null) {
                statsOffObj = p.get("offset");
            }
            int statsOffset = Math.max(0, parseIntOpt(statsOffObj, 0));
            int duoOffset = Math.max(0, parseIntOpt(p.get("duo_offset"), 0));
            int trioOffset = Math.max(0, parseIntOpt(p.get("trio_offset"), 0));
            String sc = pickSeasonCode(param);
            
            Object tierObj = p.get("tierKey");
            if (tierObj == null) {
                tierObj = p.get("tier_key");
            }
            String tierKey = null;
            if (tierObj != null) {
                String ts = String.valueOf(tierObj).trim();
                tierKey = ts.isEmpty() ? null : ts;
            }

            Map<String, Object> response = rtaService.getRtaMonsterStats(pageSize, statsOffset, duoOffset, trioOffset, sc,
                    tierKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster stats 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static int parseIntOpt(Object o, int defaultVal) {
        if (o == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Operation(summary = "RTA 몬스터 상세 정보 조회", description = "특정 몬스터의 상세 정보를 조회합니다. (기본정보, 강한상대, 좋은콤비, 3체인콤비, 카운터매치업, 최근경기)")
    @PostMapping("/monster-detail")
    public ResponseEntity<Map<String, Object>> getRtaMonsterDetail(@RequestBody Map<String, Object> param) {
        
        try {
            int monsterId = param.get("monster_id") != null ? Integer.parseInt(param.get("monster_id").toString()) : 0;
            
            if (monsterId == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String sc = pickSeasonCode(param);
            
            Map<String, Object> response = rtaService.getRtaMonsterDetail(monsterId, sc);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster detail 조회 실패 param={}", param, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
