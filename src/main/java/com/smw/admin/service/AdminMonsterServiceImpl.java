package com.smw.admin.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.smw.admin.mapper.AdminMonsterMapper;

@Service
@Primary
public class AdminMonsterServiceImpl implements AdminMonsterService {

	@Autowired
	private AdminMonsterMapper adminMonsterMapper;

	@Override
	public List<Map<String, Object>> getMonsterList(Map<String, Object> param) {
		return adminMonsterMapper.selectMonsterList(param);
	}

	@Override
	public int getMonsterCount(Map<String, Object> param) {
		return adminMonsterMapper.selectMonsterCount(param);
	}

	@Override
	public Map<String, Object> getMonsterDetail(String monsterId) {
		return adminMonsterMapper.selectMonsterDetail(monsterId);
	}

	@Override
	public int updateMonster(Map<String, Object> param) {
		return adminMonsterMapper.updateMonster(param);
	}
}
