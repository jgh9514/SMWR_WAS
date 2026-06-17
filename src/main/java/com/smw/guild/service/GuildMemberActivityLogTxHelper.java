package com.smw.guild.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.smw.guild.mapper.GuildMemberActivityLogMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 활동 로그 INSERT 전용 — REQUIRES_NEW로 본 트랜잭션과 분리.
 */
@Slf4j
@Service
public class GuildMemberActivityLogTxHelper {

	@Autowired
	private GuildMemberActivityLogMapper guildMemberActivityLogMapper;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertGuildMemberActivityLog(Map<String, Object> param) {
		try {
			guildMemberActivityLogMapper.insertGuildMemberActivityLog(param);
		} catch (Exception e) {
			log.warn("길드원 활동 로그 INSERT 실패(본 트랜잭션과 분리됨): action_type={}, user_id={}",
					param.get("action_type"), param.get("user_id"), e);
		}
	}
}
