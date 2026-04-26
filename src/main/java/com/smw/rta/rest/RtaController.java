package com.smw.rta.rest;

import com.smw.rta.service.RtaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.web.bind.annotation.RequestMethod.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.ArrayList;
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

    /** body/query: seasonId / season_id — 양수일 때만 */
    private static Long pickSeasonId(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        Object o = param.get("seasonId");
        if (o == null) {
            o = param.get("season_id");
        }
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            long v = ((Number) o).longValue();
            return v > 0 ? v : null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long mergeSeasonId(Map<String, Object> param, Long querySeasonId) {
        Long fromBody = pickSeasonId(param);
        if (fromBody != null) {
            return fromBody;
        }
        if (querySeasonId != null && querySeasonId > 0) {
            return querySeasonId;
        }
        return null;
    }

    /** body/query: ratingId (Integer) */
    private static Integer pickRatingId(Map<String, Object> param) {
        if (param == null) return null;
        Object v = param.get("ratingId");
        if (v == null) v = param.get("rating_id");
        if (v == null) return null;
        try {
            int i = Integer.parseInt(v.toString().trim());
            return i > 0 ? i : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> pickRatingIds(Map<String, Object> param) {
        if (param == null) {
            return null;
        }
        Object o = param.get("ratingIds");
        if (o == null) {
            o = param.get("rating_ids");
        }
        if (o == null) {
            return null;
        }
        if (!(o instanceof List)) {
            return null;
        }
        List<?> raw = (List<?>) o;
        List<Integer> out = new ArrayList<>();
        for (Object x : raw) {
            if (x == null) {
                continue;
            }
            try {
                int i = Integer.parseInt(x.toString().trim());
                if (i > 0) {
                    out.add(i);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out.isEmpty() ? null : out;
    }

    @Operation(summary = "RTA 매치 목록 조회", description = "RTA 매치 목록을 페이지네이션하여 조회합니다.")
    @RequestMapping(value = "/matches", method = { GET, POST })
    public ResponseEntity<List<Map<String, Object>>> getRtaMatches(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) Integer ratingId) {
        try {
            int l = 50;
            int o = 0;
            if (param != null) {
                if (param.get("limit") != null) l = Integer.parseInt(param.get("limit").toString());
                if (param.get("offset") != null) o = Integer.parseInt(param.get("offset").toString());
            }
            if (limit != null) l = limit;
            if (offset != null) o = offset;
            Long sid = mergeSeasonId(param, seasonId);
            Integer rid = ratingId != null ? ratingId : pickRatingId(param);
            List<Integer> rids = pickRatingIds(param);

            List<Map<String, Object>> matches = rtaService.getRtaMatches(l, o, sid, rid, rids);
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
            Long sid = pickSeasonId(p);

            List<Map<String, Object>> matches = rtaService.getPlayerRtaMatches(wizardId, limit, offset, sid);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("RTA player matches 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 목록 페이지 묶음", description = "매치 목록 + stats.hasMore.")
    @RequestMapping(value = "/page", method = { GET, POST })
    public ResponseEntity<Map<String, Object>> getRtaListPage(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) Integer ratingId) {
        try {
            int l = 50;
            int o = 0;
            if (param != null) {
                if (param.get("limit") != null) l = Integer.parseInt(param.get("limit").toString());
                if (param.get("offset") != null) o = Integer.parseInt(param.get("offset").toString());
            }
            if (limit != null) l = limit;
            if (offset != null) o = offset;
            Long sid = mergeSeasonId(param, seasonId);
            Integer rid = ratingId != null ? ratingId : pickRatingId(param);
            List<Integer> rids = pickRatingIds(param);
            Map<String, Object> body = rtaService.getRtaListPage(l, o, sid, rid, rids);
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
            @RequestParam(required = false) Integer ratingId) {
        try {
            Long sid = pickSeasonId(param);
            Integer rid = ratingId != null ? ratingId : pickRatingId(param);
            List<Integer> rids = pickRatingIds(param);
            Object stats = rtaService.getRtaStats(sid, rid, rids);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("RTA stats 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 테스트 데이터 조회")
    @PostMapping("/test")
    public ResponseEntity<Object> testRtaData() {
        try {
            return ResponseEntity.ok(rtaService.testRtaData());
        } catch (Exception e) {
            log.error("RTA test data 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 요약", description = "수집 리플레이 기준 최신 점수·글로벌 순위·승패 집계")
    @PostMapping("/player/{wizardId}/summary")
    public ResponseEntity<Map<String, Object>> getRtaPlayerSummary(
            @PathVariable String wizardId,
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaPlayerSummary(wizardId, sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA player summary 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 몬스터 사용(스냅)", description = "시즌별 픽/밴/승/선첫비밴/보유 — rta_agg_summoner_monster_snap")
    @PostMapping("/player/{wizardId}/monster-usage")
    public ResponseEntity<Map<String, Object>> getRtaPlayerMonsterUsage(
            @PathVariable String wizardId,
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaPlayerMonsterUsage(wizardId, sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA player monster-usage 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 랭킹")
    @PostMapping("/summoner-ranking")
    public ResponseEntity<Map<String, Object>> getRtaSummonerRanking(@RequestBody(required = false) Map<String, Object> param) {
        try {
            final int pageSize = 50;
            final int maxPages = 10;
            final int maxOffset = pageSize * (maxPages - 1);
            int limit = pageSize;
            int offset = 0;
            if (param != null) {
                if (param.get("limit") != null) {
                    int parsed = Integer.parseInt(param.get("limit").toString());
                    if (parsed >= 1) limit = Math.min(parsed, pageSize);
                }
                if (param.get("offset") != null) {
                    offset = Integer.parseInt(param.get("offset").toString());
                }
            }
            if (offset < 0) offset = 0;
            if (offset > maxOffset) offset = maxOffset;
            Long sid = pickSeasonId(param);
            String countryFilter = null;
            if (param != null) {
                Object co = param.get("country");
                if (co != null) {
                    String cf = co.toString().trim();
                    if (!cf.isEmpty()) countryFilter = cf;
                }
            }
            Map<String, Object> response = rtaService.getRtaSummonerRanking(limit, offset, sid, countryFilter);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA summoner ranking 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 소환사 검색")
    @PostMapping("/summoner-search")
    public ResponseEntity<Map<String, Object>> searchRtaSummoners(@RequestBody(required = false) Map<String, Object> param) {
        try {
            String q = null;
            if (param != null && param.get("q") != null) {
                q = param.get("q").toString();
            }
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.searchRtaSummoners(q, sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA summoner search 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 시즌 목록", description = "조회 API는 POST 규칙에 따릅니다. (body 없이 호출 가능)")
    @PostMapping("/seasons")
    public ResponseEntity<Map<String, Object>> getRtaSeasons() {
        try {
            return ResponseEntity.ok(rtaService.getRtaSeasons());
        } catch (Exception e) {
            log.error("RTA seasons 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 공식 티어 규칙(참고)", description = "선택: body에 seasonId·season_id")
    @PostMapping("/rating-grade-rules")
    public ResponseEntity<List<Map<String, Object>>> listRtaRatingGradeRules(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Long seasonId = pickSeasonId(param);
            Long sid = rtaService.resolveSeasonId(seasonId);
            if (sid == null) {
                return ResponseEntity.ok(java.util.Collections.emptyList());
            }
            return ResponseEntity.ok(rtaService.listRtaRatingGradeReference(sid));
        } catch (Exception e) {
            log.error("RTA rating grade rules 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 대시보드", description = "일별×티어 + 랭크 컷(호환용 단일 응답)")
    @PostMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getRtaDashboard(@RequestBody(required = false) Map<String, Object> param) {
        try {
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaDashboard(sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 대시보드 — 티어별 분포", description = "일별×티어 집계만 (소환사 티어별 분포 카드용)")
    @PostMapping("/dashboard/tier-distribution")
    public ResponseEntity<Map<String, Object>> getRtaDashboardTierDistribution(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaDashboardTierDistribution(sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard tier-distribution 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 대시보드 — 랭크 컷", description = "앵커·스냅샷만 (랭크 컷 섹션용)")
    @PostMapping("/dashboard/rank-cutoff")
    public ResponseEntity<Map<String, Object>> getRtaDashboardRankCutoff(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaDashboardRankCutoff(sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard rank-cutoff 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 메인 4패널 미리보기", description = "솔·듀·트·소환사 랭킹 상위 N건을 한 번에(서버 병렬 조회)")
    @PostMapping("/dashboard/link-preview")
    public ResponseEntity<Map<String, Object>> getRtaDashboardLinkPreview(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            Long sid = pickSeasonId(p);
            int preview = clampInt(parseIntOpt(p.get("previewLimit"), 5), 1, 50);
            Map<String, Object> response = rtaService.getRtaDashboardLinkPreview(sid, preview);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard link-preview 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "대시보드 프리뷰 — 솔로 TOP N", description = "스냅 테이블 전용, 전체 티어 합산")
    @PostMapping("/dashboard/preview/solo")
    public ResponseEntity<Map<String, Object>> getDashboardPreviewSolo(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            Long sid = pickSeasonId(p);
            int limit = clampInt(parseIntOpt(p.get("limit"), 5), 1, 50);
            return ResponseEntity.ok(rtaService.getDashboardPreviewSolo(sid, limit));
        } catch (Exception e) {
            log.error("대시보드 프리뷰 solo 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "대시보드 프리뷰 — 듀오 TOP N", description = "스냅 테이블 전용, 전체 티어 합산")
    @PostMapping("/dashboard/preview/duo")
    public ResponseEntity<Map<String, Object>> getDashboardPreviewDuo(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            Long sid = pickSeasonId(p);
            int limit = clampInt(parseIntOpt(p.get("limit"), 5), 1, 50);
            return ResponseEntity.ok(rtaService.getDashboardPreviewDuo(sid, limit));
        } catch (Exception e) {
            log.error("대시보드 프리뷰 duo 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "대시보드 프리뷰 — 트리오 TOP N", description = "스냅 테이블 전용, 전체 티어 합산")
    @PostMapping("/dashboard/preview/trio")
    public ResponseEntity<Map<String, Object>> getDashboardPreviewTrio(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            Long sid = pickSeasonId(p);
            int limit = clampInt(parseIntOpt(p.get("limit"), 5), 1, 50);
            return ResponseEntity.ok(rtaService.getDashboardPreviewTrio(sid, limit));
        } catch (Exception e) {
            log.error("대시보드 프리뷰 trio 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "대시보드 프리뷰 — 소환사 랭킹 TOP N", description = "스냅 테이블 전용")
    @PostMapping("/dashboard/preview/summoner")
    public ResponseEntity<Map<String, Object>> getDashboardPreviewSummoner(
            @RequestBody(required = false) Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            Long sid = pickSeasonId(p);
            int limit = clampInt(parseIntOpt(p.get("limit"), 5), 1, 50);
            return ResponseEntity.ok(rtaService.getRtaSummonerRanking(limit, 0, sid, null));
        } catch (Exception e) {
            log.error("대시보드 프리뷰 summoner 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 몬스터별 통계 조회")
    @PostMapping("/monster-stats")
    public ResponseEntity<Map<String, Object>> getRtaMonsterStats(@RequestBody Map<String, Object> param) {
        try {
            Map<String, Object> p = param != null ? param : new HashMap<>();
            int pageSize = clampInt(parseIntOpt(p.get("limit"), 20), 1, 500);
            int offset = Math.max(0, parseIntOpt(p.get("offset"), 0));
            Long sid = pickSeasonId(param);

            Integer ratingId = pickRatingId(p);
            List<Integer> ratingIds = pickRatingIds(p);

            Object typeObj = p.get("type");
            String type = typeObj != null ? String.valueOf(typeObj).trim() : "solo";

            Map<String, Object> response = rtaService.getRtaMonsterStats(pageSize, offset, type, sid, ratingId,
                    ratingIds);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster stats 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static int parseIntOpt(Object o, int defaultVal) {
        if (o == null) return defaultVal;
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    @Operation(summary = "RTA 몬스터 상세 정보 조회")
    @PostMapping("/monster-detail")
    public ResponseEntity<Map<String, Object>> getRtaMonsterDetail(@RequestBody Map<String, Object> param) {
        try {
            int monsterId = param.get("monster_id") != null ? Integer.parseInt(param.get("monster_id").toString()) : 0;
            if (monsterId == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            Long sid = pickSeasonId(param);
            Map<String, Object> response = rtaService.getRtaMonsterDetail(monsterId, sid);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster detail 조회 실패 param={}", param, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
