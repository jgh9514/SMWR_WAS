package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * staging → {@code rta_agg_*} merge 직전 세션·통계 준비.
 * <p>
 * {@code workMemMb}·{@code maxParallelWorkersPerGather} 는 0 이면 비활성(서버 기본값).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.staging-merge")
public class RtaStagingMergeProperties {

	/**
	 * COPY 직후 {@code ANALYZE &lt;staging&gt;} — 플래너가 스테이징 카디널리티를 잡기 쉬움.
	 */
	private boolean analyzeStagingBeforeMerge = true;

	/**
	 * merge 구간 {@code SET LOCAL jit = off} — 대형 DML 에서 JIT 컴파일 오버헤드 완화(PG11+).
	 */
	private boolean jitOffForMerge = true;

	/**
	 * merge 직전 {@code SET LOCAL work_mem = 'N MB'}. 대량 {@code ON CONFLICT} 시 해시·소트에 유리할 수 있음.
	 * 커넥션 풀×세션 동시성 고려 — 과도하면 OOM 위험.
	 */
	private int workMemMb = 0;

	/**
	 * merge 직전 {@code SET LOCAL max_parallel_workers_per_gather}. staging 풀스캔·조인 병렬화에 도움될 수 있음.
	 */
	private int maxParallelWorkersPerGather = 0;
}
