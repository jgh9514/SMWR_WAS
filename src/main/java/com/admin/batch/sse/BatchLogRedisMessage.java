package com.admin.batch.sse;

/**
 * Redis {@link BatchLogRedisPublisher#CHANNEL} 메시지.
 */
public record BatchLogRedisMessage(String streamId, String type, String payload) {

	public static BatchLogRedisMessage log(String streamId, String line) {
		return new BatchLogRedisMessage(streamId, "log", line != null ? line : "");
	}

	public static BatchLogRedisMessage done(String streamId, String status) {
		return new BatchLogRedisMessage(streamId, "done", status != null ? status : "FAIL");
	}
}
