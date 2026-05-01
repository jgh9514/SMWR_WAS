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

	/** {@code synergy_applied_at IS NULL}(미집계) rid 를 한 라운드에서 가져오는 건수. {@code smw.rta.batch.synergy-batch-size} 와 맞출 것. */
	private int synergyBatchSize = 2000;

	/**
	 * 시너지 라운드 사이 대기(ms). 0 이면 생략. GC·DB 부하 완화.
	 */
	private int synergyPauseMsBetweenRounds = 50;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildMonsterStatsAgg} 생략 (no-op).
	 * 기본 true — application.yml 과 동일.
	 */
	private boolean skipMonsterStatsInUnifiedJob = true;

	/**
	 * true 이면 통합 Job 에서 {@link RtaBatchAggregationService#rebuildTierAggDaily} 단계 생략.
	 * 티어 일별은 부하가 커서 {@link com.smw.monster.batch.RtaTierDailyAggJob} 등 긴 주기로 분리하는 것을 권장(기본 true).
	 */
	private boolean skipTierAggDailyInUnifiedJob = true;

	/**
	 * 시너지 단독 Job({@link com.smw.monster.batch.RtaSynergyOnlyAggJob})에서
	 * 각 라운드가 끝날 때마다 RTA 조회 캐시를 무효화할지 여부.
	 * <p>
	 * {@code true} — 라운드마다 캐시 무효화. pending 이 많을 때 중간 결과를 즉시 조회 가능(부하↑).
	 * {@code false}(기본) — 전체 drain 완료 후 한 번만 무효화. 메모리·CPU 절약.
	 */
	private boolean synergyOnlyEvictCachesEachRound = false;

	/** 미사용 — 카운터 테이블이 solo/duo/trio 로 분리되면서 별도 인덱스 DROP/REBUILD 불필요. 하위 호환 유지용. */
	@Deprecated
	private boolean dropCounterMatchupQueryIndexDuringUnifiedJob = false;

	/**
	 * 시너지 staging(COPY+merge)과 카운터 staging(COPY+merge)을 한 rid 라운드 끝에서
	 * <strong>병렬(스레드 2)</strong>로 flush 할지, <strong>순차</strong>로 할지.
	 * <p>
	 * false — 처리량은 다소 느려질 수 있으나 동시에 다른 잡·Pod·세션과 {@code rta_agg_*} 락이 겹쳐
	 * {@code lock timeout} 이 나는 환경에서 유리.
	 */
	private boolean parallelSynergyCounterStagingFlush = true;

	/** 배치 실패 시 Slack 알림용 Bot Token (xoxb-...). 비어 있으면 알림 생략. */
	private String slackToken = "";

	/** 배치 실패 알림을 보낼 Slack 채널 ID. */
	private String slackChannelId = "";
}
