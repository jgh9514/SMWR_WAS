package com.admin.code.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.code.mapper.CdMapper;
import com.admin.multilang.service.MultiLangService;
import com.sysconf.constants.Constant;
import com.sysconf.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CdServiceImpl implements CdService {

    @Autowired
    DateUtil dateUtil;

    @Autowired
    CdMapper mapper;

    @Autowired
    private MultiLangService mlangService;

    @Override
    public List<Map<String, ?>> selectCdGrpList(Map<String, Object> param) {
        return mapper.selectCdGrpList(param);
    }

    @Override
    public List<Map<String, ?>> selectCdList(Map<String, Object> param) {
        return mapper.selectCdListSystem(param);
    }

    @Override
    public List<Map<String, ?>> selectCdUtilList(Map<String, Object> param) {
        return mapper.selectCdUtilList(param);
    }
    
    @Override
    public String selectBsnsCdKey(Map<String, Object> param) {
        String key = "";
        Long apiCount = mapper.selectBsnsCount(param);
        if (apiCount > 0) {
            String bsnsCdKey = mapper.selectBsnsCdKey(param);
            key = param.get("bsns_cd").toString() + String.format("%08d", Long.parseLong(bsnsCdKey) + 1);
        } else {
            key += param.get("bsns_cd").toString() + "00000001";
        }
        return key;
    }

    @Override
    public int insertCdGrp(Map<String, Object> param) {
        log.info("CdServiceImpl  insertCdGrp");
        mapper.insertCdGrp(param);

        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE_GRP);
        mlangParam.put("bsns_cd", Constant.BSNS_COMMON);
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString());
        mlangParam.put("mlang_txt", param.get("cd_grp_nm").toString());
        mlangParam.put("lang_cd", param.get("lang_cd").toString());
        mlangParam.put("user_id", param.get("user_id").toString());
        mlangService.insertMlang(mlangParam);

        return 1;
    }

    @Override
    @Transactional
    public int updateCdGrp(Map<String, Object> param) {
        log.info("CdServiceImpl  updateCdGrp");
        mapper.updateCdGrp(param);

        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE_GRP);
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString());
        mlangParam.put("mlang_txt", param.get("cd_grp_nm").toString());
        mlangParam.put("lang_cd", param.get("lang_cd").toString());
        mlangParam.put("user_id", param.get("user_id").toString());
        mlangService.updateMlang(mlangParam);

        return 1;
    }

    @Override
    @Transactional
    public int deleteCdGrp(Map<String, Object> param) {
        log.info("CdServiceImpl  deleteCdGrp");
        mapper.deleteCdGrp(param);

        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE_GRP);
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString());
        mlangService.deleteMlang(mlangParam);

        deleteCdList(param);

        return 1;
    }

    @Override
    @Transactional
    public int deleteCdList(Map<String, Object> param) {
        log.info("CdServiceImpl  deleteCdList");
        mapper.deleteCdList(param);

        Map<String, Object> mlangParam2 = new HashMap<>();
        mlangParam2.put("mlang_tp_cd", Constant.MLANG_TP_CODE);
        mlangParam2.put("up_cd", param.get("cd_grp_no").toString());
        mlangService.deleteMlangUpCd(mlangParam2);

        return 1;
    }

    @Override
    @Transactional
    public int insertCd(Map<String, Object> param) {
        mapper.insertCd(param);

        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE);
        mlangParam.put("bsns_cd", Constant.BSNS_COMMON);
        mlangParam.put("up_cd", param.get("cd_grp_no").toString());
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString() + "-" + param.get("cd").toString());
        mlangParam.put("mlang_txt", param.get("cd_nm").toString());
        mlangParam.put("lang_cd", param.get("lang_cd").toString());
        mlangParam.put("user_id", param.get("user_id").toString());
        mlangService.insertMlang(mlangParam);
        
        return 1;
    }

    @Override
    @Transactional
    public int updateCd(Map<String, Object> param) {
        mapper.updateCd(param);
    
        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE);
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString() + "-" + param.get("cd").toString());
        mlangParam.put("mlang_txt", param.get("cd_nm").toString());
        mlangParam.put("lang_cd", param.get("lang_cd").toString());
        mlangParam.put("user_id", param.get("user_id").toString());
        mlangService.updateMlang(mlangParam);
        
        return 1;
    }

    @Override
    @Transactional
    public int deleteCd(Map<String, Object> param) {
        mapper.deleteCd(param);

        Map<String, Object> mlangParam = new HashMap<>();
        mlangParam.put("mlang_tp_cd", Constant.MLANG_TP_CODE);
        mlangParam.put("mlang_id", param.get("cd_grp_no").toString() + "-" + param.get("cd").toString());
        mlangService.deleteMlang(mlangParam);

        return 1;
    }
}
