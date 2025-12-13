package com.cf.comm.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.admin.multilang.mapper.MultiLangMapper;
import com.cf.comm.mapper.CommMapper;
import com.sysconf.cache.MultiLangCacheManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CommServiceImpl implements CommService {
    
    @Autowired
    CommMapper mapper;
    
    @Autowired
    com.admin.code.mapper.CdMapper codeMapper;
    
    @Autowired
    MultiLangMapper multiLangMapper;

    @Autowired
    com.admin.user.mapper.UserMapper userMapper;

    @PostConstruct
    public void caching() {
        cachingMultiLanguageI18n();
    }

    @Override
    public List<Map<String, ?>> selectMenuList(Map<String, Object> param) {
        return mapper.selectMenuList(param);
    }
    
    @Override
    public List<Map<String, ?>> selectLoginAccessPageList(Map<String, Object> param) {
        return mapper.selectLoginAccessPageList(param);
    }
    
    @Override
    public List<Map<String, ?>> selectCdList(Map<String, Object> param) {
        return codeMapper.selectCdUtilList(param);
    }

    @Override
    public int updateConfig(Map<String, Object> param) {
        return userMapper.updateLangCd(param);
    }

    @Override
    public List<Map<String, ?>> selectMultiLanguageList(Map<String, Object> param) {
        log.info("CommServiceImpl  selectMultiLanguageList");
        return multiLangMapper.selectMultiLanguageList(param);
    }

    @Override
    public Map<String, String> selectMultiLanguageI18n(Map<String, Object> param) {
        if ("ZH".equals(param.get("lang_cd"))) {
            return MultiLangCacheManager.getCacheLangMapZh();
        } else if ("EN".equals(param.get("lang_cd"))) {
            return MultiLangCacheManager.getCacheLangMapEn();
        }
        return MultiLangCacheManager.getCacheLangMapKo();
    }

    @Override
    public void cachingMultiLanguageI18n() {
        log.info("==================== Multi Language Cahcing Process START ====================");
        
        Map<String, Object> param = new HashMap<>();
        param.put("lang_cd", "KO");
        List<Map<String, ?>> langKoList = multiLangMapper.selectI18nList(param);
        
        // KO
        Map<String, String> langKoMap = new HashMap<String, String>();
        for (Map<String, ?> item : langKoList) {
            langKoMap.put(item.get("mlang_key").toString(), item.get("mlang_txt").toString());
        }
        
        // EN
        param.put("lang_cd", "EN");
        List<Map<String, ?>> langEnList = multiLangMapper.selectI18nList(param);
        Map<String, String> langEnMap = new HashMap<String, String>();
        for (Map<String, ?> item : langEnList) {
            langEnMap.put(item.get("mlang_key").toString(), item.get("mlang_txt").toString());
        }
        
        // ZH
        param.put("lang_cd", "ZH");
        List<Map<String, ?>> langZhList = multiLangMapper.selectI18nList(param);
        Map<String, String> langZhMap = new HashMap<String, String>();
        for (Map<String, ?> item : langZhList) {
            langZhMap.put(item.get("mlang_key").toString(), item.get("mlang_txt").toString());
        }

        MultiLangCacheManager.setCacheLangMapKo(langKoMap);
        MultiLangCacheManager.setCacheLangMapEn(langEnMap);
        MultiLangCacheManager.setCacheLangMapZh(langZhMap);

        log.info("==================== Multi Language Cahcing Process END ====================");
    }
    
    @Override
    public List<Map<String, ?>> selectVersionCheck() {
    	return mapper.selectVersionCheck();
    }
    
    @Override
    public void insertErrorLogs(Map<String, Object> param) {
    	mapper.insertErrorLogs(param);
    }
}
