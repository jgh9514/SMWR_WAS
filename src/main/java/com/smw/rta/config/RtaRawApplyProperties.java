package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 리플레이 raw 스테이징 → 정규화 적재 튜닝.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.raw-apply")
public class RtaRawApplyProperties {

	/** {@code applyPendingArenaReplayRawFromDb} 에서 매회 SELECT 할 상한 (ORDER BY rid LIMIT) */
	private int maxRowsPerRun = 1000;

	/**
	 * 한 Job 실행에서 SELECT→처리 반복 최대 횟수. 조회 결과가 빈 배열이면 그 전에 종료.
	 * 동일 행이 계속 pending 으로 남는 비정상 시 무한 루프 방지.
	 */
	private int maxBatchesPerJob = 1;

	/**
	 * backlog catch-up 시 raw 정규화 Job 당 라운드(배치) 절대 상한.
	 * {@link com.smw.rta.service.RtaBatchBacklogScaler} 가 {@code max-batches-per-job} 기본값보다 상향할 때 사용.
	 */
	private int maxBatchesCap = 20;

	/** 정규화 INSERT 청크 크기 (최소 1은 서비스에서 보정) */
	private int applyChunkSize = 1000;

	/**
	 * {@code INSERT ... VALUES (…),(…) ON CONFLICT} 한 문당 행 수 상한.
	 * 행마다 시즌·레이팅 서브쿼리가 붙어 문이 커지므로, 크면 PG JDBC 에서
	 * "An I/O error occurred while sending to the backend" 가 날 수 있음 (기본 50).
	 * {@link #copyBulkInsertEnabled} 가 true 면 COPY 경로가 우선하며, 이 값은 폴백(VALUES 다중행)에만 쓰인다.
	 */
	private int bulkInsertChunkSize = 50;

	/**
	 * true 이면 RTA 아레나 업로드 정규화 적재에 {@code COPY FROM STDIN} + TEMP + INSERT … ON CONFLICT 를 시도한다.
	 * 실패 시 기존 MyBatis 다중행 INSERT 로 폴백한다.
	 */
	private boolean copyBulkInsertEnabled = true;

	/**
	 * COPY 적재 구간에서 {@code SET LOCAL synchronous_commit = off} — 커밋 시 fsync 대기 생략(처리량↑, 장애 시 최근 트랜잭션 유실 가능성↑).
	 */
	private boolean copyBulkSynchronousCommitOff = true;

	/**
	 * true 이면 파싱·정규화 중 첫 오류 시 예외를 올려 Quartz 통합 Job 을 즉시 실패 처리한다.
	 * false 이면 기존처럼 해당 행만 failed 표기하고 계속한다.
	 */
	private boolean failFastOnError = true;
}
