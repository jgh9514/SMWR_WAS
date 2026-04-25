package com.admin.batch.rest;

import java.io.IOException;
import java.time.Instant;
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

import com.admin.batch.mapper.BatchMapper;
import com.admin.batch.sse.BatchLogBroadcaster;
import com.admin.log.service.LogService;
import com.smw.monster.util.SlackNotifier;
import com.smw.rta.config.RtaBatchProperties;
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
	private BatchMapper batchMapper;

	@Autowired
	private BatchLogBroadcaster batchLogBroadcaster;

	@Autowired
	private SlackNotifier slackNotifier;

	@Autowired
	private RtaBatchProperties rtaBatchProperties;

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
		List<Map<String, String>> list = logService.selectBatchConfig(param == null ? new HashMap<>() : param);
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

	@Operation(summary = "배치 수동 실행", description = "선택한 배치 작업을 즉시 한 번 실행합니다.")
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

		Object rawStreamId = requestBody.get("stream_id");
		if (rawStreamId != null && !String.valueOf(rawStreamId).isBlank()) {
			if (jobData == null) {
				jobData = new HashMap<>();
			}
			jobData.put("stream_id", String.valueOf(rawStreamId).trim());
		}

		if (!applicationContext.containsBean("batchConfig")) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "Quartz Scheduler 또는 BatchConfig 빈이 없습니다. spring-boot-starter-quartz 포함 여부를 확인하세요.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		BatchConfig batchConfig = (BatchConfig) applicationContext.getBean("batchConfig");
		boolean executed = batchConfig.runOnce(jobKey, jobData);

		Map<String, Object> result = new HashMap<>();
		result.put("result", executed ? Constant.SUCCESS : Constant.FAIL);
		if (executed) {
			result.put("message", "배치가 백그라운드에서 시작되었습니다. 완료 후 아래 실행 이력에서 결과(로그)를 확인하세요.");
		} else {
			result.put("message", "배치 트리거에 실패했습니다. job_key·배치 설정(job_class)을 확인하세요.");
		}
		return new ResponseEntity<>(result, executed ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
	}

	@Operation(summary = "배치 실행 이력 목록", description = "배치 실행 이력을 조회합니다.")
	@PostMapping("/run-his")
	public ResponseEntity<?> selectBatchRunHis(@RequestBody(required = false) Map<String, Object> param) {
		Map<String, Object> query = param == null ? new HashMap<>() : new HashMap<>(param);
		if (!query.containsKey("limit")) {
			query.put("limit", 10);
		}
		List<Map<String, ?>> list = batchMapper.selectBatchRunHisList(query);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "배치 실행 이력 상세", description = "특정 실행 이력 상세를 조회합니다.")
	@GetMapping("/run-his/{runSn}")
	public ResponseEntity<?> selectBatchRunHisDetail(@PathVariable Long runSn) {
		Map<String, ?> detail = batchMapper.selectBatchRunHisDetail(runSn);
		return new ResponseEntity<>(detail, HttpStatus.OK);
	}
}
