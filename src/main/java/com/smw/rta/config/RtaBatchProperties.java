package com.smw.rta.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * RTA 통합 배치({@code RtaUnifiedPipelineAggJob}) 튜닝. 운영에서 부하·지연에 맞게 조정한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "smw.rta.batch")
public class RtaBatchProperties {

	/** {@code synergy_applied_at IS NULL} rid 를 한 라운드에서 가져오는 건수 */
	private int synergyBatchSize = 200;

	/** 매치 스냅샷 pending 을 한 라운드에서 처리하는 rid 건수 */
	private int snapshotBatchSize = 3000;

	/** 통합 Job 한 실행에서 스냅샷 drain 라운드 상한 (레거시 단계; 대부분 no-op) */
	private int snapshotMaxRoundsPerJob = 200;

	/** 통합 Job 한 실행에서 시너지 집계 라운드 상한 */
	private int synergyMaxRoundsPerJob = 500;

	/**
	 * true 이면 스냅샷 drain 단계를 생략한다. v2 스키마에서 pending 스냅샷이 항상 비어 있을 때 불필요한 SELECT 를 줄인다.
	 */
	private boolean skipLegacySnapshotStep = false;
}
