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

    @Operation(summary = "RTA 매치 목록 조회", description = "RTA 매치 목록을 페이지네이션하여 조회합니다.")
    @RequestMapping(value = "/matches", method = { GET, POST })
    public ResponseEntity<List<Map<String, Object>>> getRtaMatches(
            @RequestBody(required = false) Map<String, Object> param,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        
        try {
            int l = 50;
            int o = 0;
            if (param != null) {
                if (param.get("limit") != null) l = Integer.parseInt(param.get("limit").toString());
                if (param.get("offset") != null) o = Integer.parseInt(param.get("offset").toString());
            }
            if (limit != null) l = limit;
            if (offset != null) o = offset;
            
            List<Map<String, Object>> matches = rtaService.getRtaMatches(l, o);
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
            @RequestBody Map<String, Object> param) {
        
        try {
            int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 50;
            int offset = param.get("offset") != null ? Integer.parseInt(param.get("offset").toString()) : 0;
            
            List<Map<String, Object>> matches = rtaService.getPlayerRtaMatches(wizardId, limit, offset);
            return ResponseEntity.ok(matches);
        } catch (Exception e) {
            log.error("RTA player matches 조회 실패 wizardId={}", wizardId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 매치 수 조회", description = "전체 RTA 매치의 총 개수를 조회합니다.")
    @RequestMapping(value = "/matches/count", method = { GET, POST })
    public ResponseEntity<Map<String, Object>> getRtaMatchesCount() {
        
        try {
            long count = rtaService.getRtaMatchesCount();
            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA matches count 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 통계 조회", description = "RTA 관련 통계 데이터를 조회합니다.")
    @PostMapping("/stats")
    public ResponseEntity<Object> getRtaStats() {
        
        try {
            Object stats = rtaService.getRtaStats();
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
    public ResponseEntity<Map<String, Object>> getRtaPlayerSummary(@PathVariable String wizardId) {
        try {
            Map<String, Object> response = rtaService.getRtaPlayerSummary(wizardId);
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
            if (limit > 200) {
                limit = 200;
            }
            if (offset < 0) {
                offset = 0;
            }
            Map<String, Object> response = rtaService.getRtaSummonerRanking(limit, offset);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA summoner ranking 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 대시보드", description = "일별×티어 집계 전체 + 날짜 범위 (기간 필터는 클라이언트에서 합산)")
    @PostMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getRtaDashboard() {
        try {
            Map<String, Object> response = rtaService.getRtaDashboard();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA dashboard 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 몬스터별 통계 조회", description = "RTA 몬스터별 통계 데이터를 조회합니다. (픽횟수, 픽률, 승률, 선픽율, 벤율)")
    @PostMapping("/monster-stats")
    public ResponseEntity<Map<String, Object>> getRtaMonsterStats(@RequestBody Map<String, Object> param) {
        
        try {
            int limit = param.get("limit") != null ? Integer.parseInt(param.get("limit").toString()) : 20;
            int offset = param.get("offset") != null ? Integer.parseInt(param.get("offset").toString()) : 0;
            
            Map<String, Object> response = rtaService.getRtaMonsterStats(limit, offset);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster stats 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "RTA 몬스터 상세 정보 조회", description = "특정 몬스터의 상세 정보를 조회합니다. (기본정보, 강한상대, 좋은콤비, 3체인콤비, 최근경기)")
    @PostMapping("/monster-detail")
    public ResponseEntity<Map<String, Object>> getRtaMonsterDetail(@RequestBody Map<String, Object> param) {
        
        try {
            int monsterId = param.get("monster_id") != null ? Integer.parseInt(param.get("monster_id").toString()) : 0;
            
            if (monsterId == 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            Map<String, Object> response = rtaService.getRtaMonsterDetail(monsterId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("RTA monster detail 조회 실패 param={}", param, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
