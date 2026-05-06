package com.smw.monster.batch;

import java.util.HashMap;
import java.util.Map;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.admin.batch.mapper.BatchMapper;
import com.admin.batch.sse.BatchLogBroadcaster;
import com.smw.monster.service.SwarfarmSyncService;
import com.smw.monster.util.SlackNotifier;
import com.smw.rta.config.RtaBatchProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 배치 Job의 기본 클래스
 * - 로그 수집 및 이력 관리
 * - 실행 이력은 짧은 트랜잭션으로 기록, 본문 배치는 서비스/매퍼 단위로 커밋
 */
@Slf4j
public abstract class BaseBatchJob implements Job {

    protected StringBuilder logContent = new StringBuilder();
    protected Long runSn = null;
    protected Long currentBatId = null;
    protected ApplicationContext applicationContext;
    protected BatchMapper batchMapper;
    protected PlatformTransactionManager transactionManager;
    protected MeterRegistry meterRegistry;

    /** 수동 실행 시 SSE로 실시간 로그를 보낼 때 사용하는 스트림 ID (없으면 null) */
    private String streamId;
    private BatchLogBroadcaster logBroadcaster;

    /**
     * ApplicationContext가 종료 중일 때도 슬랙 전송이 가능하도록 execute() 시작 시 미리 캡처.
     * getBean()을 실패 시점에 호출하면 컨텍스트 destroy 중 예외가 발생해 알림이 누락된다.
     */
    private String cachedSlackToken;
    private String cachedSlackChannelId;
    private SlackNotifier cachedSlackNotifier;
    
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long startTime = System.currentTimeMillis();
        logContent.setLength(0); // 로그 초기화
        streamId = null;
        logBroadcaster = null;
        Timer.Sample batchTimerSample = null;
        boolean completedOk = false;
        
        try {
            // ApplicationContext 가져오기
            applicationContext = (ApplicationContext) context.getJobDetail().getJobDataMap().get("applicationContext");
            if (applicationContext == null) {
                applicationContext = (ApplicationContext) context.getScheduler().getContext().get("applicationContext");
            }
            
            if (applicationContext == null) {
                throw new JobExecutionException("ApplicationContext를 찾을 수 없습니다.");
            }

            streamId = resolveStreamId(context);
            if (streamId != null && !streamId.isEmpty()) {
                try {
                    logBroadcaster = applicationContext.getBean(BatchLogBroadcaster.class);
                } catch (Exception e) {
                    log.debug("BatchLogBroadcaster 사용 불가", e);
                }
            }

            // 슬랙 정보를 컨텍스트가 살아 있는 지금 미리 캡처 — 실패 시 컨텍스트가 종료 중일 수 있음
            try {
                RtaBatchProperties slackProps = applicationContext.getBean(RtaBatchProperties.class);
                cachedSlackToken     = slackProps.getSlackToken();
                cachedSlackChannelId = slackProps.getSlackChannelId();
                cachedSlackNotifier  = applicationContext.getBean(SlackNotifier.class);
            } catch (Exception e) {
                log.debug("[slack] 슬랙 설정 캡처 실패 — 실패 알림 불가", e);
            }

            meterRegistry = applicationContext.getBeanProvider(MeterRegistry.class).getIfAvailable();
            if (meterRegistry != null) {
                batchTimerSample = Timer.start(meterRegistry);
            }
            
            // BatchMapper 가져오기
            batchMapper = applicationContext.getBean(BatchMapper.class);
            
            // TransactionManager 가져오기
            try {
                transactionManager = applicationContext.getBean(PlatformTransactionManager.class);
            } catch (Exception e) {
                log.warn("TransactionManager를 찾을 수 없습니다. 트랜잭션 없이 실행합니다.", e);
            }
            
            // 배치 ID 가져오기
            Long batId = getBatchId(context);
            if (batId == null) {
                throw new JobExecutionException("배치 ID를 찾을 수 없습니다.");
            }
            currentBatId = batId;
            
            // 실행 이력 RUNNING 등록만 짧은 트랜잭션으로 커밋 — 본문 배치는 묶지 않음(장시간·대량 시 한 덩어리 롤백 방지)
            addLog("===== 배치 실행 시작 =====");
            addLog("배치 ID: %d", batId);
            addLog("배치명: %s", getBatchName());
            
            Map<String, Object> runHis = new HashMap<>();
            runHis.put("bat_id", batId);
            runHis.put("rslt_cd", "RUNNING");
            runHis.put("rslt_txt", getLogContent());
            insertBatchRunStartingInNewTx(runHis);
            runSn = (Long) runHis.get("run_sn");
            
            addLog("실행 이력 등록 완료. run_sn: %d", runSn);
            
            // 실제 배치 로직 실행
            addLog("===== 배치 로직 실행 시작 =====");
            executeBatch(context);
            addLog("===== 배치 로직 실행 완료 =====");
            
            // 성공 처리
            long elapsedTime = System.currentTimeMillis() - startTime;
            addLog("===== 배치 실행 완료 =====");
            addLog("소요 시간: %.2f초", elapsedTime / 1000.0);
            
            updateBatchRunHis("SUCCESS", getLogContent());
            recordBatchMetrics("SUCCESS", batchTimerSample);
            completedOk = true;
            
        } catch (Exception e) {
            // 실패 처리
            long elapsedTime = System.currentTimeMillis() - startTime;
            addLog("===== 배치 실행 실패 =====");
            addLog("오류 메시지: %s", e.getMessage());
            addLog("소요 시간: %.2f초", elapsedTime / 1000.0);
            
            String errorLog = getLogContent();
            if (e.getCause() != null) {
                String stackTrace = getStackTrace(e);
                // 스택 트레이스는 최대 2000자로 제한
                if (stackTrace.length() > 2000) {
                    stackTrace = stackTrace.substring(0, 1997) + "...";
                }
                errorLog += "\n\n상세 오류:\n" + stackTrace;
            }
            
            updateBatchRunHis("FAIL", errorLog);
            recordBatchMetrics("FAIL", batchTimerSample);

            if (isInfraFailure(e)) {
                // 커넥션 풀 종료·앱 재시작 등 인프라 문제 — 배치 비활성화 없이 다음 스케줄에서 재시도
                log.warn("[batch] 인프라 오류로 실패 — 스케줄 유지 (batchName={}, error={})",
                        getBatchName(), e.getMessage());
                sendSlackFailureAlert(getBatchName(), e, false);
            } else {
                sendSlackFailureAlert(getBatchName(), e, true);
                disableJobOnFailure();
            }

            log.error("배치 실행 중 오류 발생", e);
            throw new JobExecutionException("배치 실행 실패: " + e.getMessage(), e);
        } finally {
            completeLogStream(completedOk);
        }
    }

    /** RUNNING 이력 INSERT 를 Job 본문과 분리해 즉시 커밋 */
    private void insertBatchRunStartingInNewTx(Map<String, Object> runHis) {
        if (transactionManager == null) {
            batchMapper.insertBatchRunHis(runHis);
            return;
        }
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionStatus histTx = transactionManager.getTransaction(def);
        try {
            batchMapper.insertBatchRunHis(runHis);
            transactionManager.commit(histTx);
        } catch (Exception e) {
            if (!histTx.isCompleted()) {
                try {
                    transactionManager.rollback(histTx);
                } catch (Exception rollbackEx) {
                    log.error("배치 RUNNING 이력 등록 롤백 실패", rollbackEx);
                }
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("배치 RUNNING 이력 등록 실패", e);
        }
    }

    private String resolveStreamId(JobExecutionContext context) {
        Object v = context.getMergedJobDataMap().get("stream_id");
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private void completeLogStream(boolean success) {
        if (streamId == null || logBroadcaster == null) {
            return;
        }
        try {
            logBroadcaster.complete(streamId, success ? "SUCCESS" : "FAIL");
        } catch (Exception e) {
            log.debug("SSE 완료 처리 실패", e);
        }
    }

    private void pushStreamLine(String line) {
        if (streamId == null || logBroadcaster == null) {
            return;
        }
        logBroadcaster.sendLog(streamId, line);
    }
    
    /**
     * 실제 배치 로직 실행 (하위 클래스에서 구현)
     */
    protected abstract void executeBatch(JobExecutionContext context) throws Exception;
    
    /**
     * 배치 ID 가져오기
     */
    protected Long getBatchId(JobExecutionContext context) {
        try {
            String jobKey = context.getJobDetail().getKey().getName();
            return Long.valueOf(jobKey);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 배치명 가져오기 (하위 클래스에서 오버라이드 가능)
     */
    protected String getBatchName() {
        return this.getClass().getSimpleName();
    }
    
    /**
     * 로그 추가
     * %d, %s, %f 등 String.format 형식 지원
     */
    protected void addLog(String message, Object... args) {
        String logMessage;
        if (args != null && args.length > 0) {
            try {
                // String.format 형식 (%d, %s, %f 등) 직접 지원
                logMessage = String.format(message, args);
            } catch (Exception e) {
                // 형식 오류 시 단순 치환 시도
                logMessage = message;
                for (Object arg : args) {
                    logMessage = logMessage.replaceFirst("\\{\\}", String.valueOf(arg));
                }
                log.warn("로그 형식 오류, 단순 치환 사용: {}", message);
            }
        } else {
            logMessage = message;
        }
        
        String timestamp = java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logContent.append("[").append(timestamp).append("] ").append(logMessage).append("\n");
        log.info(logMessage);
        pushStreamLine("[" + timestamp + "] " + logMessage);
    }
    
    /**
     * 현재 로그 내용 가져오기
     */
    protected String getLogContent() {
        return logContent.toString();
    }

    protected void attachServiceLogCallback(SwarfarmSyncService service) {
        if (service == null) {
            return;
        }
        service.setLogCallback((msg) -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String line = "[" + timestamp + "] " + msg;
            logContent.append(line).append("\n");
            pushStreamLine(line);
        });
    }
    
    /**
     * 배치 실행 이력 업데이트
     * 별도 트랜잭션(REQUIRES_NEW)으로 처리하여 연결 누수 방지
     */
    protected void updateBatchRunHis(String rsltCd, String rsltTxt) {
        if (runSn == null || batchMapper == null) {
            log.warn("배치 실행 이력 업데이트 실패: runSn={}, batchMapper={}", runSn, batchMapper);
            return;
        }
        
        TransactionStatus updateTxStatus = null;
        try {
            // 별도 트랜잭션으로 처리 (REQUIRES_NEW) - 메인 트랜잭션과 독립적으로 실행
            if (transactionManager != null) {
                DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
                updateTxStatus = transactionManager.getTransaction(def);
            }
            
            // TEXT 타입이므로 길이 제한 없이 저장 (최대 1GB까지 가능하지만, 실용적으로는 충분)
            // 너무 긴 경우를 대비해 최대 100만자로 제한 (약 2MB)
            String finalTxt = rsltTxt;
            if (finalTxt != null && finalTxt.length() > 1000000) {
                finalTxt = finalTxt.substring(0, 999997) + "...\n\n[로그가 100만자를 초과하여 잘렸습니다.]";
                addLog("로그 내용이 100만자를 초과하여 잘렸습니다.");
            }
            
            Map<String, Object> runHis = new HashMap<>();
            runHis.put("run_sn", runSn);
            runHis.put("rslt_cd", rsltCd);
            runHis.put("rslt_txt", finalTxt);
            batchMapper.updateBatchRunHis(runHis);
            
            // 트랜잭션 커밋 (연결이 제대로 닫히도록 보장)
            if (updateTxStatus != null && !updateTxStatus.isCompleted()) {
                transactionManager.commit(updateTxStatus);
            }
            
            log.info("배치 실행 이력 업데이트 완료. run_sn={}, rslt_cd={}, 로그 길이={}자", 
                    runSn, rsltCd, finalTxt != null ? finalTxt.length() : 0);
        } catch (Exception e) {
            // 트랜잭션 롤백
            if (updateTxStatus != null && !updateTxStatus.isCompleted()) {
                try {
                    transactionManager.rollback(updateTxStatus);
                } catch (Exception rollbackEx) {
                    log.error("배치 이력 업데이트 트랜잭션 롤백 중 오류 발생", rollbackEx);
                }
            }
            log.error("배치 실행 이력 업데이트 중 오류 발생", e);
        }
    }
    
    /**
     * 예외 스택 트레이스 가져오기
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void sendSlackFailureAlert(String batchName, Exception e, boolean disabled) {
        if (cachedSlackNotifier == null) {
            log.warn("[slack] 슬랙 노티파이어 미캡처 — 배치 실패 알림 생략 (batchName={})", batchName);
            return;
        }
        try {
            String suffix = disabled
                    ? "\n스케줄이 비활성화되었습니다. 확인 후 수동으로 재활성화하세요."
                    : "\n인프라 오류(커넥션 등)로 인한 일시 실패 — 스케줄은 유지됩니다.";
            String msg = String.format("[배치 실패] *%s*\n오류: %s%s", batchName, e.getMessage(), suffix);
            cachedSlackNotifier.send(cachedSlackToken, cachedSlackChannelId, msg);
        } catch (Exception ex) {
            log.warn("[slack] 배치 실패 알림 전송 중 오류", ex);
        }
    }

    /**
     * 커넥션 풀 종료·앱 재시작 등 인프라 문제로 인한 예외 여부.
     * 이 경우 배치 자체 로직 오류가 아니므로 스케줄을 비활성화하지 않는다.
     */
    private static boolean isInfraFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null) {
                String lc = msg.toLowerCase();
                if (lc.contains("has been closed")
                        || lc.contains("hikaripool")
                        || lc.contains("connection is closed")
                        || lc.contains("connection closed")
                        || lc.contains("datasource")
                        || lc.contains("i/o error")
                        || lc.contains("socket closed")
                        || lc.contains("broken pipe")
                        || lc.contains("sending to the backend")) {
                    return true;
                }
            }
            if (t instanceof java.sql.SQLException
                    || t.getClass().getName().contains("HikariPool")) {
                String cn = t.getClass().getName();
                if (cn.contains("Connection") || cn.contains("Hikari")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void disableJobOnFailure() {
        if (currentBatId == null) {
            return;
        }
        try {
            // 1) Quartz 트리거 일시정지
            org.quartz.Scheduler scheduler = applicationContext.getBean(org.quartz.Scheduler.class);
            org.quartz.JobKey jobKey = org.quartz.JobKey.jobKey(String.valueOf(currentBatId));
            if (scheduler.checkExists(jobKey)) {
                scheduler.pauseJob(jobKey);
                log.warn("[batch-disable] Quartz job 일시정지 jobKey={}", jobKey);
            }
        } catch (Exception ex) {
            log.warn("[batch-disable] Quartz 일시정지 실패", ex);
        }
        try {
            // 2) DB use_yn = 'N'
            batchMapper.disableBatch(currentBatId);
            log.warn("[batch-disable] sys_batch_config use_yn=N batId={}", currentBatId);
        } catch (Exception ex) {
            log.warn("[batch-disable] DB 비활성화 실패", ex);
        }
    }

    private void recordBatchMetrics(String result, Timer.Sample sample) {
        if (meterRegistry == null) {
            return;
        }

        Tags tags = Tags.of(
                "batch_name", getBatchName(),
                "result", result
        );

        Counter.builder("smw.batch.execution.count")
                .description("Batch execution count")
                .tags(tags)
                .register(meterRegistry)
                .increment();

        if (sample != null) {
            sample.stop(Timer.builder("smw.batch.execution.duration")
                    .description("Batch execution duration")
                    .tags(tags)
                    .register(meterRegistry));
        }
    }
}


