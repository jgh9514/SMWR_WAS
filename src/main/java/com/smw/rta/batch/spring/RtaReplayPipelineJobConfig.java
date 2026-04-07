package com.smw.rta.batch.spring;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smw.monster.service.summonerswarService;
import com.smw.rta.mapper.RtaMapper;
import com.smw.rta.model.RtaCounterMatchupUpsertRow;
import com.smw.rta.model.RtaSynergyAggUpsertRow;
import com.smw.rta.service.RtaSynergyAggService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RTA 2-Step 배치 파이프라인.
 * <ul>
 * <li>Step1: NDJSON(1경기=1행) → 기존 {@link summonerswarService#applyArenaRtaNormalizedChunk} 로 rta_match / participant / unit_pick 적재 (중복 rid 는 ON CONFLICT 무시)</li>
 * <li>Step2: {@code synergy_applied_at IS NULL} 인 rid 를 청크로 읽어 시너지·카운터 매치업 행을 배치 UPSERT 후 {@code markSynergyAggDoneForRids}</li>
 * </ul>
 * 미집계 여부는 {@code rta_match.synergy_applied_at} (사용자 설계의 is_summarized 와 동일 역할).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RtaReplayPipelineJobConfig {

	public static final String JOB_PIPELINE = "rtaReplayPipelineJob";
	public static final String JOB_SYNERGY_ONLY = "rtaSynergyAggregateBatchJob";

	/** NDJSON Step1: 한 청크 커밋당 라인 수 */
	public static final int DEFAULT_CHUNK = 1000;

	/** 시너지 Step2: reader 페이지·청크 크기(회당 rid 처리량) */
	public static final int SYNERGY_DEFAULT_CHUNK = 10000;

	/** MyBatis foreach 안전 한도 */
	private static final int SYNERGY_UPSERT_SLICE = 500;

	private static final int COUNTER_UPSERT_SLICE = 500;

	/** 시너지 집계 행 수: 필드 3마리(×2진영)=14, 4마리=28 */
	private static boolean isValidSynergyAggRowCount(int rowCount) {
		return rowCount == 14 || rowCount == 28;
	}

	private final DataSource dataSource;
	private final ObjectMapper objectMapper;
	private final summonerswarService summonerswarService;
	private final RtaSynergyAggService rtaSynergyAggService;
	private final RtaMapper rtaMapper;
	private final PlatformTransactionManager transactionManager;

	@Bean
	public Job rtaReplayPipelineJob(JobRepository jobRepository, Step rtaLoadRawNdjsonStep, Step rtaSynergyAggregateStep) {
		return new JobBuilder(JOB_PIPELINE, jobRepository)
				.incrementer(new RunIdIncrementer())
				.start(rtaLoadRawNdjsonStep)
				.next(rtaSynergyAggregateStep)
				.build();
	}

	@Bean
	public Job rtaSynergyAggregateBatchJob(JobRepository jobRepository, Step rtaSynergyAggregateStep) {
		return new JobBuilder(JOB_SYNERGY_ONLY, jobRepository)
				.incrementer(new RunIdIncrementer())
				.start(rtaSynergyAggregateStep)
				.build();
	}

	@Bean
	public Step rtaLoadRawNdjsonStep(
			JobRepository jobRepository,
			FlatFileItemReader<String> rtaNdjsonLineReader,
			ItemProcessor<String, Map<String, Object>> rtaNdjsonLineToMapProcessor,
			ItemWriter<Map<String, Object>> rtaNormalizedChunkItemWriter) {
		return new StepBuilder("rtaLoadRawNdjsonStep", jobRepository)
				.<String, Map<String, Object>>chunk(DEFAULT_CHUNK, transactionManager)
				.reader(rtaNdjsonLineReader)
				.processor(rtaNdjsonLineToMapProcessor)
				.writer(rtaNormalizedChunkItemWriter)
				.build();
	}

	@Bean
	public Step rtaSynergyAggregateStep(
			JobRepository jobRepository,
			JdbcPagingItemReader<Long> synergyPendingReplayReader,
			ItemProcessor<Long, RtaSynergyBatchItem> rtaSynergyBatchItemProcessor,
			ItemWriter<RtaSynergyBatchItem> rtaSynergyBatchItemWriter) {
		return new StepBuilder("rtaSynergyAggregateStep", jobRepository)
				.<Long, RtaSynergyBatchItem>chunk(SYNERGY_DEFAULT_CHUNK, transactionManager)
				.reader(synergyPendingReplayReader)
				.processor(rtaSynergyBatchItemProcessor)
				.writer(rtaSynergyBatchItemWriter)
				.build();
	}

	@Bean
	@StepScope
	public FlatFileItemReader<String> rtaNdjsonLineReader(
			@Value("#{jobParameters['inputFile']}") String inputFile) {
		if (inputFile == null || inputFile.isBlank()) {
			throw new IllegalStateException(
					"rtaReplayPipelineJob Step1 에는 job parameter 'inputFile' (NDJSON 절대/상대 경로)이 필요합니다.");
		}
		FlatFileItemReader<String> reader = new FlatFileItemReader<>();
		reader.setName("rtaNdjsonLineReader");
		reader.setResource(new FileSystemResource(inputFile.trim()));
		reader.setLineMapper(new PassThroughLineMapper());
		reader.setEncoding(StandardCharsets.UTF_8.name());
		reader.setStrict(true);
		return reader;
	}

	@Bean
	public ItemProcessor<String, Map<String, Object>> rtaNdjsonLineToMapProcessor() {
		return line -> {
			if (line == null || line.isBlank()) {
				return null;
			}
			try {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = objectMapper.readValue(line, Map.class);
				return map;
			} catch (Exception e) {
				throw new IllegalStateException("NDJSON 라인 파싱 실패: " + e.getMessage(), e);
			}
		};
	}

	@Bean
	public ItemWriter<Map<String, Object>> rtaNormalizedChunkItemWriter() {
		return chunk -> {
			List<Map<String, ?>> rows = new ArrayList<>();
			for (Map<String, Object> m : chunk.getItems()) {
				rows.add(m);
			}
			if (rows.isEmpty()) {
				return;
			}
			Map<String, Integer> result = summonerswarService.applyArenaRtaNormalizedChunk(rows);
			log.debug("[rta-batch] Step1 chunk success={} fail={}", result.get("success"), result.get("fail"));
		};
	}

	@Bean
	@StepScope
	public JdbcPagingItemReader<Long> synergyPendingReplayReader(
			@Value("#{jobParameters['synergyPageSize'] != null ? T(Integer).parseInt(jobParameters['synergyPageSize'].toString()) : 10000}") int pageSize)
			throws Exception {
		PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
		queryProvider.setSelectClause("replay_id");
		queryProvider.setFromClause("public.rta_match");
		queryProvider.setWhereClause("synergy_applied_at IS NULL");
		Map<String, Order> sortKeys = new HashMap<>();
		sortKeys.put("replay_id", Order.ASCENDING);
		queryProvider.setSortKeys(sortKeys);

		int ps = Math.max(1, pageSize);
		JdbcPagingItemReader<Long> reader = new JdbcPagingItemReader<>();
		reader.setName("synergyPendingReplayReader");
		reader.setDataSource(dataSource);
		reader.setQueryProvider(queryProvider);
		reader.setPageSize(ps);
		reader.setFetchSize(ps);
		reader.setRowMapper((rs, i) -> rs.getLong("replay_id"));
		reader.afterPropertiesSet();
		return reader;
	}

	@Bean
	public ItemProcessor<Long, RtaSynergyBatchItem> rtaSynergyBatchItemProcessor() {
		return rid -> {
			List<RtaSynergyAggUpsertRow> rows = rtaSynergyAggService.buildSynergyRowsForRid(rid);
			if (!isValidSynergyAggRowCount(rows.size())) {
				throw new IllegalStateException(
						"조합 행 수 불일치 rid=" + rid + " n=" + rows.size() + " (필드 3마리→14, 4마리→28)");
			}
			List<RtaCounterMatchupUpsertRow> counterRows = rtaSynergyAggService.buildCounterMatchupRowsForRid(rid);
			return new RtaSynergyBatchItem(rid, rows, counterRows);
		};
	}

	@Bean
	public ItemWriter<RtaSynergyBatchItem> rtaSynergyBatchItemWriter() {
		return chunk -> {
			List<RtaSynergyAggUpsertRow> all = new ArrayList<>();
			List<RtaCounterMatchupUpsertRow> allCounter = new ArrayList<>();
			List<Long> rids = new ArrayList<>();
			for (RtaSynergyBatchItem item : chunk.getItems()) {
				all.addAll(item.rows());
				rids.add(item.replayId());
				if (item.counterRows() != null) {
					allCounter.addAll(item.counterRows());
				}
			}
			if (rids.isEmpty()) {
				return;
			}
			for (int i = 0; i < all.size(); i += SYNERGY_UPSERT_SLICE) {
				int end = Math.min(i + SYNERGY_UPSERT_SLICE, all.size());
				rtaMapper.upsertRtaSynergyAgg(all.subList(i, end));
			}
			for (int i = 0; i < allCounter.size(); i += COUNTER_UPSERT_SLICE) {
				int end = Math.min(i + COUNTER_UPSERT_SLICE, allCounter.size());
				rtaMapper.upsertRtaCounterMatchupAgg(allCounter.subList(i, end));
			}
			int n = rtaMapper.markSynergyAggDoneForRids(rids);
			if (n != rids.size()) {
				log.warn("[rta-batch] Step2 mark done expected={} actual={}", rids.size(), n);
			}
		};
	}
}
