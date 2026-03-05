package com.smw.common.event;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiegeUploadedEvent {
	private long publishedAtEpochMs;
	private int insertedSiegeCount;
	private int insertedBattleCount;
	private List<String> affectedMatchIds = new ArrayList<>();
}
