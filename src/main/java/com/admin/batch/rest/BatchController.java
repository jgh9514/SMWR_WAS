package com.admin.batch.rest;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.admin.batch.sse.BatchLogBroadcaster;
import com.admin.log.service.LogService;
import com.smw.monster.util.SlackNotifier;
import com.smw.rta.config.RtaBatchProperties;
import com.smw.rta.config.RtaRawApplyProperties;
import com.smw.rta.service.RtaBatchBacklogScaler;
import com.sysconf.annotation.RequireAdmin;
import com.sysconf.config.BatchConfig;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Batch Management", description = "배치 스케줄 관리 API")
@RequireAdmin
@RestController
@RequestMapping("/api/v1/batch")
public class BatchController {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private LogService logService;

	@Autowired
	private BatchLogBroadcaster batchLogBroadcaster;

	@Autowired
	private SlackNotifier slackNotifier;

	@Autowired
	private RtaBatchProperties rtaBatchProperties;

	@Autowired
	private RtaRawApplyProperties rtaRawApplyProperties;

	@Autowired
	private RtaBatchBacklogScaler rtaBatchBacklogScaler;

	@Operation(summary = "RTA 배치 backlog·catch-up 계획", description = "미처리 건수와 backlog 스케일링 적용 시 Job 당 처리 상한을 조회합니다.")
	@GetMapping("/backlog")
	public ResponseEntity<Map<String, Object>> batchBacklogPlan() {
		RtaBatchBacklogScaler.RtaBatchBacklogCounts counts = rtaBatchBacklogScaler.snapshot();
		int synergyBatch = Math.max(1, rtaBatchProperties.getSynergyBatchSize());
		int rawRows = Math.max(1, rtaRawApplyProperties.getMaxRowsPerRun());
		int pickChunk = Math.max(1, rtaBatchProperties.getPickSlotDrainBatchSize());

		int synergyRounds = rtaBatchBacklogScaler.resolveSynergyMaxRounds(
				counts.synergyPending(), synergyBatch, rtaBatchProperties.getSynergyMaxRoundsPerJob());
		int rawBatches = rtaBatchBacklogScaler.resolveRawMaxBatches(
				counts.rawPending(), rawRows, rtaRawApplyProperties.getMaxBatchesPerJob());
		int pickRounds = rtaBatchBacklogScaler.resolvePickSlotMaxRounds(
				counts.pickSlotPending(), pickChunk, 1);
		int synergyPause = rtaBatchBacklogScaler.resolveSynergyPauseMs(
				counts.synergyPending(), rtaBatchProperties.getSynergyPauseMsBetweenRounds(), synergyBatch);

		Map<String, Object> pending = new LinkedHashMap<>();
		pending.put("synergy", counts.synergyPending());
		pending.put("synergy_count_cached",
				rtaBatchProperties.getPendingCountCacheTtlSeconds() > 0);
		pending.put("raw", counts.rawPending());
		pending.put("pick_slot", counts.pickSlotPending());
		pending.put("summoner_ranking", counts.summonerRankingPending());

		Map<String, Object> configured = new LinkedHashMap<>();
		configured.put("synergy_max_rounds_per_job", rtaBatchProperties.getSynergyMaxRoundsPerJob());
		configured.put("synergy_batch_size", synergyBatch);
		configured.put("raw_max_batches_per_job", rtaRawApplyProperties.getMaxBatchesPerJob());
		configured.put("raw_max_rows_per_run", rawRows);
		configured.put("backlog_scaling_enabled", rtaBatchProperties.isBacklogScalingEnabled());

		Map<String, Object> scaled = new LinkedHashMap<>();
		scaled.put("synergy_max_rounds", synergyRounds);
		scaled.put("synergy_pause_ms", synergyPause);
		scaled.put("raw_max_batches", rawBatches);
		scaled.put("pick_slot_max_rounds", pickRounds);
		scaled.put("synergy_max_rounds_cap", rtaBatchProperties.getSynergyMaxRoundsCap());
		scaled.put("raw_max_batches_cap", rtaRawApplyProperties.getMaxBatchesCap());
		scaled.put("pick_slot_max_rounds_cap", rtaBatchProperties.getPickSlotMaxRoundsCap());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("result", Constant.SUCCESS);
		result.put("pending", pending);
		result.put("configured", configured);
		result.put("scaled_plan", scaled);
		return ResponseEntity.ok(result);
	}

	@Operation(summary = "Slack 테스트 발송", description = "smw.rta.batch.slack-token·slack-channel-id 로 배치 실패 알림과 동일 경로에 샘플 메시지를 보냅니다. 토큰/채널 미설정 시 실패 응답.")
	@PostMapping("/slack/test")
	public ResponseEntity<Map<String, Object>> testSlack(@RequestBody(required = false) Map<String, Object> body) {
		String text = "[SMW 관리자] Slack 연동 테스트 — " + Instant.now();
		if (body != null && body.get("message") != null) {
			String m = String.valueOf(body.get("message")).trim();
			if (!m.isEmpty()) {
				text = m.length() > 3500 ? m.substring(0, 3500) : m;
			}
		}
		SlackNotifier.SendOutcome o = slackNotifier.sendWithOutcome(
				rtaBatchProperties.getSlackToken(),
				rtaBatchProperties.getSlackChannelId(),
				text);
		Map<String, Object> result = new HashMap<>();
		result.put("result", o.isSuccess() ? Constant.SUCCESS : Constant.FAIL);
		result.put("configured", o.isConfigured());
		result.put("message", o.getDetail());
		HttpStatus status = o.isSuccess() ? HttpStatus.OK
				: (o.isConfigured() ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_REQUEST);
		return new ResponseEntity<>(result, status);
	}

	@Operation(summary = "배치 수동 실행 로그 스트림(SSE)", description = "수동 실행 전에 연결한 뒤, 같은 stream_id로 /batch/run을 호출하면 로그가 실시간으로 전달됩니다.")
	@GetMapping(value = "/logs/stream/{streamId}")
	public ResponseEntity<?> streamBatchLogs(@PathVariable String streamId, HttpServletRequest request) {
		if (streamId == null || streamId.length() > 80 || !streamId.matches("[a-fA-F0-9\\-]{8,80}")) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", Constant.FAIL);
			body.put("message", "stream_id가 유효하지 않습니다.");
			return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
		}
		SseEmitter emitter = batchLogBroadcaster.register(streamId);
		try {
			emitter.send(SseEmitter.event().comment("ok"));
		} catch (IOException e) {
			Map<String, Object> body = new HashMap<>();
			body.put("result", Constant.FAIL);
			body.put("message", "스트림을 열 수 없습니다.");
			return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);
		}
		return ResponseEntity.ok()
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(emitter);
	}

	@Operation(summary = "배치 설정 목록 조회", description = "배치 스케줄 설정 값을 조회합니다.")
	@PostMapping("/config")
	public ResponseEntity<?> selectBatchConfig(@RequestBody(required = false) Map<String, Object> param) {
		List<Map<String, ?>> list = logService.selectBatchConfig(param == null ? new HashMap<>() : param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "배치 재시작", description = "배치 스케줄러를 재시작합니다.")
	@PostMapping("/restart")
	public ResponseEntity<?> restartBatch() {
		if (!applicationContext.containsBean("batchConfig")) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "Quartz Scheduler 또는 BatchConfig 빈이 없습니다.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		BatchConfig batchConfig = (BatchConfig) applicationContext.getBean("batchConfig");
		batchConfig.clear();
		batchConfig.start();

		return new ResponseEntity<>(Constant.SUCCESS, HttpStatus.OK);
	}

	@Operation(summary = "배치 수동 실행", description = "선택한 배치를 트리거하고 Job 완료까지 동기 대기한 뒤 실행 로그를 반환합니다.")
	@PostMapping("/run")
	public ResponseEntity<?> runBatch(@RequestBody Map<String, Object> param) {
		Map<String, Object> requestBody = param != null ? param : new HashMap<>();

		Object rawJobKey = requestBody.containsKey("job_key") ? requestBody.get("job_key") : requestBody.get("cd");
		String jobKey = rawJobKey != null ? String.valueOf(rawJobKey) : null;
		if (jobKey == null || jobKey.trim().isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "job_key(또는 cd) 값이 필요합니다.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		Map<String, Object> jobData = null;
		Object rawJobData = requestBody.get("job_data");
		if (rawJobData instanceof Map<?, ?>) {
			Map<?, ?> rawMap = (Map<?, ?>) rawJobData;
			Map<String, Object> parsedJobData = new HashMap<>();
			rawMap.forEach((k, v) -> parsedJobData.put(String.valueOf(k), v));
			jobData = parsedJobData;
		}

		if (!applicationContext.containsBean("batchConfig")) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "Quartz Scheduler 또는 BatchConfig 빈이 없습니다. spring-boot-starter-quartz 포함 여부를 확인하세요.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		BatchConfig batchConfig = (BatchConfig) applicationContext.getBean("batchConfig");
		com.admin.batch.ManualBatchRunOutcome outcome = batchConfig.runOnceAndWait(jobKey, jobData);

		Map<String, Object> result = new HashMap<>();
		if (!outcome.triggered()) {
			result.put("result", Constant.FAIL);
			result.put("message", "배치 트리거에 실패했습니다. job_key·배치 설정(job_class)을 확인하세요.");
			return new ResponseEntity<>(result, HttpStatus.BAD_REQUEST);
		}
		if (outcome.runSn() != null) {
			result.put("run_sn", outcome.runSn());
		}
		if (outcome.rsltCd() != null) {
			result.put("rslt_cd", outcome.rsltCd());
		}
		if (outcome.rsltTxt() != null) {
			result.put("rslt_txt", outcome.rsltTxt());
		}
		result.put("elapsed_ms", outcome.elapsedMs());
		if (outcome.timedOut()) {
			result.put("result", Constant.FAIL);
			result.put("message", "배치 완료 대기 시간을 초과했습니다. 실행 이력(run_sn)에서 진행 상태를 확인하세요.");
			return new ResponseEntity<>(result, HttpStatus.GATEWAY_TIMEOUT);
		}
		boolean success = "SUCCESS".equals(outcome.rsltCd());
		result.put("result", success ? Constant.SUCCESS : Constant.FAIL);
		result.put("message", success ? "배치가 완료되었습니다." : "배치가 실패했습니다.");
		return new ResponseEntity<>(result, HttpStatus.OK);
	}

	@Operation(summary = "배치 실행 이력 목록", description = "배치 실행 이력을 조회합니다. 로그 본문은 미리보기(512자)만 포함하며, 전문은 /run-his/detail 을 사용합니다.")
	@PostMapping("/run-his")
	public ResponseEntity<?> selectBatchRunHis(@RequestBody(required = false) Map<String, Object> param) {
		Map<String, Object> query = param == null ? new HashMap<>() : new HashMap<>(param);
		if (!query.containsKey("limit")) {
			query.put("limit", 30);
		}
		try {
			List<Map<String, ?>> list = logService.selectBatchRunHisList(query);
			return new ResponseEntity<>(list, HttpStatus.OK);
		} catch (Exception e) {
			log.error("배치 실행 이력 조회 실패 param={}", query, e);
			Map<String, Object> err = new HashMap<>();
			err.put("result", Constant.FAIL);
			err.put("message", "배치 실행 이력을 조회할 수 없습니다.");
			return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "배치 실행 이력 상세", description = "특정 실행 이력 상세를 조회합니다. body: runSn 또는 run_sn")
	@PostMapping("/run-his/detail")
	public ResponseEntity<?> selectBatchRunHisDetail(@RequestBody(required = false) Map<String, Object> param) {
		Long runSn = parseRunSn(param);
		if (runSn == null) {
			Map<String, Object> err = new HashMap<>();
			err.put("result", Constant.FAIL);
			err.put("message", "runSn(또는 run_sn)이 필요합니다.");
			return new ResponseEntity<>(err, HttpStatus.BAD_REQUEST);
		}
		Map<String, ?> detail = logService.selectBatchRunHisDetail(runSn);
		return new ResponseEntity<>(detail, HttpStatus.OK);
	}

	/** runSn / run_id 스타일 long 파싱 */
	private static Long parseRunSn(Map<String, Object> param) {
		if (param == null) {
			return null;
		}
		Object o = param.get("runSn");
		if (o == null) {
			o = param.get("run_sn");
		}
		if (o == null) {
			return null;
		}
		if (o instanceof Number) {
			long v = ((Number) o).longValue();
			return v > 0 ? v : null;
		}
		try {
			long v = Long.parseLong(String.valueOf(o).trim());
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
