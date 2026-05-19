package com.smw.monster.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;

public interface SiegeMapMapper {

	int upsertSiegeMapMatch(Map<String, Object> row);

	int insertSiegeMapSnapshot(Map<String, Object> row);

	int insertSiegeMapSnapshotGuildBatch(@Param("rows") List<Map<String, Object>> rows);

	int insertSiegeMapSnapshotBaseBatch(@Param("rows") List<Map<String, Object>> rows);

	void incrementSnapshotCount(@Param("match_id") String matchId, @Param("captured_at") long capturedAt);

	List<Map<String, ?>> selectSiegeMapMatchHistory(Map<String, Object> param);

	int selectSiegeMapMatchHistoryCount(Map<String, Object> param);

	Map<String, ?> selectSiegeMapMatchById(@Param("match_id") String matchId);

	Map<String, ?> selectLatestSnapshotHeader(@Param("match_id") String matchId);

	List<Map<String, ?>> selectSnapshotGuilds(@Param("snapshot_id") long snapshotId);

	List<Map<String, ?>> selectSnapshotBases(@Param("snapshot_id") long snapshotId);

	List<Map<String, ?>> selectSnapshotTimeline(@Param("match_id") String matchId);

	Map<String, ?> selectSnapshotHeaderById(@Param("snapshot_id") long snapshotId);

	Long selectSnapshotIdByMatchAndCaptured(@Param("match_id") String matchId, @Param("captured_at") long capturedAt);

	Map<String, ?> selectLatestBaseDefenseCapture(Map<String, Object> param);

	List<Map<String, ?>> selectBaseDefenseDecks(@Param("capture_id") long captureId, @Param("base_number") int baseNumber);

	List<Map<String, ?>> selectBaseDefenseUnits(@Param("capture_id") long captureId);

	Map<String, ?> selectSnapshotBase(
			@Param("snapshot_id") long snapshotId,
			@Param("base_number") int baseNumber);

	List<Map<String, ?>> selectSiegeMapBaseLayoutMaster();

	List<Map<String, ?>> selectSiegeMapBaseImageMaster();

}
