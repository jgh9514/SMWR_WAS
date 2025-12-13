package com.admin.multilang.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.multilang.mapper.MultiLangMapper;
import com.cf.comm.service.CommService;
import com.sysconf.interceptor.SessionThread;
import com.sysconf.util.DateUtil;
import com.sysconf.util.StringUtil;

@Service
@Primary
public class MultiLangServiceImpl implements MultiLangService {

    @Autowired
    DateUtil dateUtil;

    @Autowired
    MultiLangMapper mapper;

    @Autowired
    com.admin.code.mapper.CdMapper codeMapper;

    @Autowired
    CommService commService;

    @Override
    public List<Map<String, ?>> selectMlangList(Map<String, Object> param) {
        return mapper.selectMlangList(param);
    }

    @Override
    @Transactional
    public int insertMlang(Map<String, Object> param) {
        Map<String, Object> codeParam = new HashMap<>();
        codeParam.put("cd_grp_no", "CO00000002");
        List<Map<String, ?>> langList = codeMapper.selectCdListByCdGrpNo(codeParam);

        for (Map<String, ?> cd : langList) {
            Map<String, Object> mlangParam = new HashMap<>(param);
            mlangParam.put("lang_cd", cd.get("cd"));
            if (cd.get("cd").equals(SessionThread.SESSION_USER_INFO.get().get("sess_lang_cd"))) {
                mlangParam.put("mlang_txt", StringUtil.nvl(param.get("mlang_txt").toString()).trim());
            } else {
                mlangParam.put("mlang_txt", StringUtil.nvl(param.get("mlang_txt").toString()).trim() + "(" + cd.get("cd") + ")");
            }
            mapper.insertMlang(mlangParam);
        }

        commService.cachingMultiLanguageI18n();

        return langList.size();
    }

    @Override
    @Transactional
    public int updateMlang(Map<String, Object> param) {
        mapper.updateMlang(param);
        commService.cachingMultiLanguageI18n();
        return 1;
    }

    @Override
    @Transactional
    public int deleteMlang(Map<String, Object> param) {
        mapper.deleteMlang(param);
        return 1;
    }

    @Override
    @Transactional
    public int deleteMlangUpCd(Map<String, Object> param) {
        mapper.deleteMlangUpCd(param);
        return 1;
    }

    @Override
    public String selectMlangInfo(Map<String, Object> param) {
        return mapper.selectMlangInfo(param);
    }

    @Override
    public List<Map<String, Object>> selectMlangListInfo(Map<String, Object> param) {
        List<Map<String, String>> result = mapper.selectMlangListInfo(param);
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
    public String selectMlangSeq(String mlang_tp_cd) {
        return mapper.selectMlangSeq(mlang_tp_cd);
    }
}

