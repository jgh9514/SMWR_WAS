package com.admin.code.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.admin.code.mapper.CdRelMapper;

@Service
public class CdRelServiceImpl implements CdRelService {
	
	@Autowired
	private CdRelMapper mapper;

	@Override
	public List<Map<String, ?>> selectCdList(Map<String, Object> param) {
		return mapper.selectCdList(param);
	}

	@Override
	public List<Map<String, ?>> selectCdRelList(Map<String, Object> param) {
		return mapper.selectCdRelList(param);
	}

	@Override
	public int deleteCdRel(Map<String, String> temp) {
		return mapper.deleteCdRel(temp);
	}

	@Override
	public int updateCdRel(Map<String, String> temp) {
		return mapper.updateCdRel(temp);
	}

	@Override
	public int insertCdRel(Map<String, String> temp) {
		return mapper.insertCdRel(temp);
	}

	@Override
	public List<Map<String, ?>> selectPopCdList(Map<String, Object> param) {
		return mapper.selectPopCdList(param);
	}

}
