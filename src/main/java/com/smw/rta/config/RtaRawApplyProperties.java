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

	/** {@code applyPendingArenaReplayRawFromDb} 한 번 호출당 SELECT 상한 */
	private int maxRowsPerRun = 100;

	/** 정규화 INSERT 청크 크기 (최소 1은 서비스에서 보정) */
	private int applyChunkSize = 200;

	/**
	 * 통합 배치({@code RtaUnifiedPipelineAggJob}) 한 실행에서 raw 정규화 루프(apply 호출 반복) 상한.
	 * 다음 스케줄에서 이어서 처리한다.
	 */
	private int maxRoundsPerUnifiedJob = 30;
}
