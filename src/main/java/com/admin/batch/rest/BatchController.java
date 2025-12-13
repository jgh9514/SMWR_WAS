package com.admin.batch.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.admin.batch.mapper.BatchMapper;
import com.admin.log.service.LogService;
import com.sysconf.config.BatchConfig;
import com.sysconf.constants.Constant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Batch Management", description = "배치 스케줄 관리 API")
@RestController
@RequestMapping("/api/v1/batch")
public class BatchController {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private LogService logService;
	
	@Autowired
	private BatchMapper batchMapper;

	@Operation(summary = "배치 설정 목록 조회", description = "배치 스케줄 설정 값을 조회합니다.")
	@PostMapping("/config")
	public ResponseEntity<?> selectBatchConfig(@RequestBody(required = false) Map<String, Object> param) {
		List<Map<String, String>> list = logService.selectBatchConfig(param == null ? new HashMap<>() : param);
		return new ResponseEntity<>(list, HttpStatus.OK);
	}

	@Operation(summary = "배치 재시작", description = "배치 스케줄러를 재시작합니다.")
	@PostMapping("/restart")
	public ResponseEntity<?> restartBatch(@RequestBody(required = false) Map<String, Object> param) {
		if (!applicationContext.containsBean("batchConfig")) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "배치 설정이 비활성화되어 있습니다.");
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
		Map<String, Object> request = param != null ? param : new HashMap<>();

		Object rawJobKey = request.containsKey("job_key") ? request.get("job_key") : request.get("cd");
		String jobKey = rawJobKey != null ? String.valueOf(rawJobKey) : null;
		if (jobKey == null || jobKey.trim().isEmpty()) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "job_key(또는 cd) 값이 필요합니다.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		Map<String, Object> jobData = null;
		Object rawJobData = request.get("job_data");
		if (rawJobData instanceof Map<?, ?>) {
			Map<?, ?> rawMap = (Map<?, ?>) rawJobData;
			Map<String, Object> parsedJobData = new HashMap<>();
			rawMap.forEach((k, v) -> parsedJobData.put(String.valueOf(k), v));
			jobData = parsedJobData;
		}

		if (!applicationContext.containsBean("batchConfig")) {
			Map<String, Object> error = new HashMap<>();
			error.put("result", Constant.FAIL);
			error.put("message", "배치 설정이 비활성화되어 있습니다.");
			return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
		}

		BatchConfig batchConfig = (BatchConfig) applicationContext.getBean("batchConfig");
		boolean executed = batchConfig.runOnce(jobKey, jobData);

		Map<String, Object> result = new HashMap<>();
		result.put("result", executed ? Constant.SUCCESS : Constant.FAIL);
		return new ResponseEntity<>(result, executed ? HttpStatus.OK : HttpStatus.BAD_REQUEST);
	}

	@Operation(summary = "배치 실행 이력 목록", description = "배치 실행 이력을 조회합니다.")
	@PostMapping("/run-his")
	public ResponseEntity<?> selectBatchRunHis(@RequestBody(required = false) Map<String, Object> param) {
		Map<String, Object> query = param == null ? new HashMap<>() : param;
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

