package com.smw.monster.batch;

import java.util.HashMap;
import java.util.Locale;
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
import com.sysconf.logging.LogPayloadTrimmer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * 배치 Job의 기본 클래스
 * - 로그 수집 및 이력 관리
 * - 실행 이력은 짧은 트랜잭션으로 기록, 본문 배치는 서비스/매퍼 단위로 커밋
 * <p>
 * {@link #addLog} 규칙: 처리 <b>건수·행 수·affected 합계</b> 등 집계 수치는 기록한다.
 * SELECT 결과 <b>row 내용</b>(컬럼 값·ResultSet 테이블 덤프·Map row 직렬화)는 기록하지 않는다.
 * {@link #addLogPlain} 은 SQL·예외 원문 등 동적 문자열용.
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
        boolean runHisFinalized = false;
        String pendingRsltCd = null;
        String pendingRsltTxt = null;
        
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
            
            pendingRsltCd = "SUCCESS";
            pendingRsltTxt = getLogContent();
            runHisFinalized = updateBatchRunHisWithRetry(pendingRsltCd, pendingRsltTxt, 3);
            recordBatchMetrics("SUCCESS", batchTimerSample);
            completedOk = true;
            
        } catch (Exception e) {
            // 실패 처리
            long elapsedTime = System.currentTimeMillis() - startTime;
            addLog("===== 배치 실행 실패 =====");
            addLog("오류 메시지: %s", sanitizeErrorMessage(e));
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
            
            pendingRsltCd = "FAIL";
            pendingRsltTxt = errorLog;
            runHisFinalized = updateBatchRunHisWithRetry("FAIL", errorLog, 3);
            recordBatchMetrics("FAIL", batchTimerSample);

            String infraReason = classifyInfraFailure(e);
            if (infraReason != null) {
                // 커넥션 종료·idle-in-tx·rollback 실패 등 — 이력은 FAIL, Quartz ERROR·Job 재던지기는 생략
                log.warn("[batch] 인프라 오류로 실패 — 스케줄 유지 (batchName={}, reason={}, error={})",
                        getBatchName(), infraReason, sanitizeErrorMessage(e));
                sendSlackFailureAlert(getBatchName(), e, false, infraReason);
                return;
            }
            sendSlackFailureAlert(getBatchName(), e, true, null);
            disableJobOnFailure();
            log.error("배치 실행 중 오류 발생", e);
            throw new JobExecutionException("배치 실행 실패: " + e.getMessage(), e);
        } finally {
            if (!runHisFinalized && runSn != null) {
                if (pendingRsltCd != null) {
                    runHisFinalized = updateBatchRunHisWithRetry(pendingRsltCd, pendingRsltTxt, 5);
                }
                if (!runHisFinalized) {
                    String partial = getLogContent();
                    if (partial == null || partial.isBlank()) {
                        partial = "[auto] Job 종료 시 이력 갱신 실패";
                    } else {
                        partial = partial + "\n[auto] Job 종료 시 SUCCESS/FAIL 갱신 재시도 실패";
                    }
                    updateBatchRunHisWithRetry(completedOk ? "SUCCESS" : "FAIL", partial, 3);
                }
            }
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
     * 배치 이력({@code sys_batch_run_his.rslt_txt})에만 남기고, 앱 로그(stdout)에는 남기지 않는다.
     * SQL·예외 메시지 등 {@code %} 가 포함된 문자열용.
     */
    protected void addLogPlain(String message) {
        appendBatchLogLine(message, false);
    }

    /**
     * 로그 추가 — {@code sys_batch_run_his} 전문 + SSE(수동 실행 시).
     * <p>
     * {@code %} 포맷은 템플릿(첫 인자)에만 사용한다. 동적 문자열은 {@link #addLogPlain} 을 쓴다.
     */
    protected void addLog(String message, Object... args) {
        String logMessage = formatBatchLogMessage(message, args);
        boolean milestone = message != null && message.startsWith("=====");
        appendBatchLogLine(logMessage, milestone);
    }

    private static String formatBatchLogMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(Locale.ROOT, message, args);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder(message != null ? message.length() + 64 : 64);
            if (message != null) {
                sb.append(message);
            }
            sb.append(" [fmt:");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.valueOf(args[i]));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    private void appendBatchLogLine(String logMessage, boolean mirrorToAppLogInfo) {
        String timestamp = java.time.LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        logContent.append("[").append(timestamp).append("] ").append(logMessage).append("\n");
        String safeForExternal = LogPayloadTrimmer.truncateUtf8(logMessage, LogPayloadTrimmer.DEFAULT_MAX_MESSAGE_BYTES);
        if (mirrorToAppLogInfo) {
            log.info(safeForExternal);
        } else if (log.isDebugEnabled()) {
            log.debug(safeForExternal);
        }
        pushStreamLine("[" + timestamp + "] " + safeForExternal);
    }
    
    /**
     * 현재 로그 내용 가져오기
     */
    protected String getLogContent() {
        return logContent.toString();
    }

    /** Swarfarm 동기화 진행(페이지·건수 요약). API row 원문은 서비스에서 넣지 않는다. */
    protected void attachServiceLogCallback(SwarfarmSyncService service) {
        if (service == null) {
            return;
        }
        service.setLogCallback((msg) -> {
            String timestamp = java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String safeMsg = LogPayloadTrimmer.truncateUtf8(String.valueOf(msg), LogPayloadTrimmer.DEFAULT_MAX_MESSAGE_BYTES);
            String line = "[" + timestamp + "] " + safeMsg;
            logContent.append(line).append("\n");
            pushStreamLine(line);
        });
    }
    
    /**
     * 배치 실행 이력 업데이트
     * 별도 트랜잭션(REQUIRES_NEW)으로 처리하여 연결 누수 방지
     * @return 갱신 성공 여부
     */
    protected boolean updateBatchRunHis(String rsltCd, String rsltTxt) {
        return updateBatchRunHisWithRetry(rsltCd, rsltTxt, 1);
    }

    private boolean updateBatchRunHisWithRetry(String rsltCd, String rsltTxt, int maxAttempts) {
        int attempts = Math.max(1, maxAttempts);
        for (int i = 1; i <= attempts; i++) {
            if (doUpdateBatchRunHis(rsltCd, rsltTxt)) {
                return true;
            }
            if (i < attempts) {
                try {
                    Thread.sleep(200L * i);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return false;
    }

    private boolean doUpdateBatchRunHis(String rsltCd, String rsltTxt) {
        if (runSn == null || batchMapper == null) {
            log.warn("배치 실행 이력 업데이트 실패: runSn={}, batchMapper={}", runSn, batchMapper);
            return false;
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
            return true;
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
        return false;
    }
    
    /**
     * 예외 메시지에서 연결 문자열·자격증명 패턴을 제거한 안전한 문자열 반환.
     * addLog 또는 Slack 알림에 e.getMessage()를 직접 쓰는 대신 이 메서드를 사용한다.
     */
    protected static String sanitizeErrorMessage(Throwable e) {
        String type = e.getClass().getSimpleName();
        String msg = e.getMessage();
        if (msg == null) {
            return type;
        }
        msg = msg.replaceAll("(?i)(redis|jdbc|mongodb|amqp|http|https)://[^\\s]*", "[REDACTED_URL]");
        msg = msg.replaceAll("(?i)(password|passwd|pwd)[=:\\s]+\\S+", "password=[REDACTED]");
        msg = msg.replaceAll("(?i)(token|secret|key)[=:\\s]+\\S+", "token=[REDACTED]");
        msg = stripSqlAndTabularNoiseFromBatchError(msg);
        if (msg.length() > 200) {
            msg = msg.substring(0, 197) + "...";
        }
        return type + ": " + msg;
    }

    /** 배치 이력·알림용 — SQL 전문·psql 형태 컬럼 꼬리 제거 */
    private static String stripSqlAndTabularNoiseFromBatchError(String msg) {
        if (msg == null || msg.isEmpty()) {
            return msg;
        }
        String lower = msg.toLowerCase(java.util.Locale.ROOT);
        String[] sqlStarts = {"insert into", "update ", "delete from", "select ", "merge into", "copy "};
        int cut = -1;
        for (String s : sqlStarts) {
            int i = lower.indexOf(s);
            if (i >= 0 && (cut < 0 || i < cut)) {
                cut = i;
            }
        }
        if (cut >= 0) {
            msg = msg.substring(0, cut).trim();
            if (msg.isEmpty()) {
                return "(DB operation failed)";
            }
            msg = msg + " [SQL omitted]";
        }
        return msg.replaceAll("\\|true\\s*\\|true\\s*\\|null\\s*\\|[^|\\n]*\\|?\\s*$", "").trim();
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

    private void sendSlackFailureAlert(String batchName, Exception e, boolean disabled, String infraReason) {
        if (cachedSlackNotifier == null) {
            log.warn("[slack] 슬랙 노티파이어 미캡처 — 배치 실패 알림 생략 (batchName={})", batchName);
            return;
        }
        try {
            String suffix = disabled
                    ? "\n분류: 로직 오류 — 스케줄 비활성화됨, 확인 후 수동으로 재활성화하세요."
                    : String.format("\n분류: 인프라 오류 (%s) — 스케줄 유지, 다음 실행에 재처리", infraReason);
            String msg = String.format("[배치 실패] *%s*\n오류: %s%s", batchName, sanitizeErrorMessage(e), suffix);
            cachedSlackNotifier.send(cachedSlackToken, cachedSlackChannelId, msg);
        } catch (Exception ex) {
            log.warn("[slack] 배치 실패 알림 전송 중 오류", ex);
        }
    }

    /**
     * 커넥션 풀 종료·앱 재시작·백엔드 전송 실패 등 인프라 문제로 인한 예외 분류.
     * Spring DAO 타입을 우선 체크하고, 그 외 JDBC·메시지 패턴으로 보완한다.
     * null 반환 시 인프라 오류 아님(로직 오류로 처리).
     */
    private static String classifyInfraFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            // Spring DAO 타입 직접 체크 — 문자열 패턴보다 명확하고 견고
            if (t instanceof org.springframework.dao.DataAccessResourceFailureException) {
                return "DataAccessResourceFailure";
            }
            if (t instanceof org.springframework.dao.TransientDataAccessException) {
                return "TransientDataAccess";
            }
            if (t instanceof org.springframework.transaction.TransactionSystemException) {
                return "TransactionSystem";
            }

            String msg = t.getMessage();
            if (msg != null) {
                String lc = msg.toLowerCase();
                if (lc.contains("jdbc rollback failed")
                        || lc.contains("idle_in_transaction_session_timeout")
                        || lc.contains("canceling statement due to user request")) {
                    return "ConnectionOrTxTimeout";
                }
                if (lc.contains("has been closed") || lc.contains("connection is closed") || lc.contains("connection closed")) {
                    return "ConnectionClosed";
                }
                if (lc.contains("hikaripool")) {
                    return "HikariPool";
                }
                if (lc.contains("i/o error") || lc.contains("socket closed") || lc.contains("broken pipe")) {
                    return "IOError";
                }
                if (lc.contains("sending to the backend")) {
                    return "BackendSendError";
                }
                if (lc.contains("datasource")) {
                    return "DataSource";
                }
            }

            if (t instanceof java.sql.SQLException) {
                String cn = t.getClass().getName();
                if (cn.contains("Connection") || cn.contains("Hikari")) {
                    return "SQLConnectionError";
                }
            }
        }
        return null;
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


