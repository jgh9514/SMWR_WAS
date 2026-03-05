package com.sysconf.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MybatisInterceptorMapper {
	
	/**
	 * 페이지 검색 인터셉터 목록 조회
	 */
	List<Map<String, Object>> getPageSearchInterceptorList();
}

