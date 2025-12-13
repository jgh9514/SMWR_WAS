package com.admin.code.service;

import java.util.List;
import java.util.Map;

public interface CdRelService {

	List<Map<String, ?>> selectCdList(Map<String, Object> param);

	List<Map<String, ?>> selectCdRelList(Map<String, Object> param);

	int deleteCdRel(Map<String, String> temp);

	int updateCdRel(Map<String, String> temp);

	int insertCdRel(Map<String, String> temp);

	List<Map<String, ?>> selectPopCdList(Map<String, Object> param);

}
