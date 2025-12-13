package com.admin.page.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sysconf.interceptor.PageSearchResult;
import com.admin.multilang.service.MultiLangService;
import com.admin.page.mapper.PageMapper;
import com.sysconf.constants.Constant;

@Service
public class PageServiceImpl implements PageService{

    @Autowired
    PageMapper mapper;

    @Autowired
    MultiLangService mlangService;

	@Autowired
	PageSearchResult pageSearchResult;

    @Override
    public List<Map<String, Object>> selectPageConditionItems(Map<String, Object> param) {
        List<Map<String, String>> result = mapper.selectPageConditionItems(param);
        List<Map<String, Object>> convertedResult = new ArrayList<>();
        for (Map<String, String> item : result) {
            Map<String, Object> convertedItem = new HashMap<>();
            for (Map.Entry<String, String> entry : item.entrySet()) {
                convertedItem.put(entry.getKey(), entry.getValue());
            }
            convertedResult.add(convertedItem);
        }
        return convertedResult;
    }

	@Override
	public List<Map<String, ?>> selectPageList(Map<String, Object> param) {
		return mapper.selectPageList(param);
	}
	
	@Override
	public List<Map<String, ?>> selectPageConditionList(Map<String, Object> param) {
		return mapper.selectPageConditionList(param);
	}

	@Override
	@Transactional
	public int deletePage(Map<String, Object> param) {
		
		mapper.deletePage(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_PAGE);
		mlangParam.put("mlang_id", param.get("page_id"));
		mlangService.deleteMlang(mlangParam);

		mapper.deletePageConditionList(param);

		Map<String, Object> mlangParam2 = new HashMap<>();
		mlangParam2.put("mlang_tp_cd", Constant.MLANG_TP_CONDITION);
		mlangParam2.put("up_cd", param.get("page_id"));
		mlangService.deleteMlangUpCd(mlangParam2);
		
		return 1;
	}
	
	@Override
	@Transactional
	public int insertPage(Map<String, Object> param) {
		
		mapper.insertPage(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_PAGE);
		mlangParam.put("bsns_cd", Constant.BSNS_SYSTEM);
		mlangParam.put("mlang_id", param.get("page_id"));
		mlangParam.put("mlang_txt", param.get("page_nm"));
		mlangService.insertMlang(mlangParam);

		return 1;
	}
	
	@Override
	@Transactional
	public int updatePage(Map<String, Object> param) {

		mapper.updatePage(param);

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_PAGE);
		mlangParam.put("mlang_id", param.get("page_id"));
		mlangParam.put("mlang_txt", param.get("page_nm"));
		mlangService.updateMlang(mlangParam);
		
		return 1;
	}

	@Override
	@Transactional
	public int deletePageCondition(Map<String, Object> param) {
		
		mapper.deletePageCondition(param);
		pageSearchResult.setPageConditionList();

		Map<String, Object> mlangParam = new HashMap<>();
		mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CONDITION);
		mlangParam.put("mlang_id", param.get("condition_id"));
		mlangService.deleteMlang(mlangParam);

		return 1;
	}
	
	@Override
	@Transactional
	public int savePageCondition(Map<String, Object> param) {
		if (param.get("condition_id") == null) {
			mapper.insertPageCondition(param);
			pageSearchResult.setPageConditionList();

			if (param.get("label_nm") != null) {
				Map<String, Object> mlangParam = new HashMap<>();
				mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CONDITION);
				mlangParam.put("bsns_cd", Constant.BSNS_SYSTEM);
				mlangParam.put("up_cd", param.get("page_id"));
				mlangParam.put("mlang_id", param.get("condition_id"));
				mlangParam.put("mlang_txt", param.get("label_nm"));
				mlangService.insertMlang(mlangParam);
			}
		} else {
			mapper.updatePageCondition(param);
			pageSearchResult.setPageConditionList();

			if (param.get("label_nm") != null) {
				Map<String, Object> mlangParam = new HashMap<>();
				mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CONDITION);
				mlangParam.put("mlang_id", param.get("condition_id"));
				mlangParam.put("mlang_txt", param.get("label_nm"));
				mlangService.updateMlang(mlangParam);
			}
		}
		return 1;
	}

@Override
	@Transactional
	public int updatePageCondition(Map<String, Object> param) {

		mapper.updatePageCondition(param);
		pageSearchResult.setPageConditionList();

		if (param.get("label_nm") != null) {
			Map<String, Object> mlangParam = new HashMap<>();
			mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CONDITION);
			mlangParam.put("mlang_id", param.get("condition_id"));
			mlangParam.put("mlang_txt", param.get("label_nm"));
			mlangService.updateMlang(mlangParam);
		}

		return 1;
	}

	@Override
	public Map<String, ?> selectPageConditionInfo(Map<String, Object> param) {
		return mapper.selectPageConditionInfo(param);
	}

	@Override
	public Map<String, Object> selectPageAuth(Map<String, Object> param) {

		Map<String, Object> result = new HashMap<>();
		Map<String, Object> authCheckYn = mapper.selectAuthCheckYn(param);

		if (authCheckYn != null && "Y".equals(authCheckYn.get("auth_check_yn"))) {
			param.put("page_id", (String) authCheckYn.get("page_id"));
			List<Map<String, Object>> list = mapper.selectPageAuth(param);

			if (!list.isEmpty()) {
				result.put("result", "OK");
				return result;
			} else {
				Map<String, Object> auth = mapper.selectPageAuthTable(param);
				auth.put("key_id", param.get("key_id"));
				auth = mapper.selectAuthTable(auth);

				String rslt = (String) auth.get("rslt");
				if ("OK".equals(rslt)) {
					result.put("result", "OK");
				} else {
					result.put("result", "NOK");
				}
			}
		} else {
			result.put("result", "OK");
		}

		return result;
	}
}
