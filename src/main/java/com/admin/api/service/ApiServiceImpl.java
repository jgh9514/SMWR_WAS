package com.admin.api.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.admin.api.mapper.ApiMapper;

@Service
public class ApiServiceImpl implements ApiService {

    @Autowired
    ApiMapper mapper;

    @Autowired
    com.admin.role.mapper.RoleMapper roleMapper;


    @Override
    public List<Map<String, ?>> selectApiList(Map<String, Object> param) {
        return mapper.selectApiList(param);
    }

    @Override
    public List<Map<String, ?>> selectApiRoleList(Map<String, Object> param) {
        return roleMapper.selectApiRoleList(param);
    }

    @Override
    public String selectApiKey(Map<String, Object> param) {
        return mapper.selectApiKey(param);
    }

    @Override
    public int insertApiList(Map<String, Object> param) {
        return mapper.insertApiList(param);
    }

    @Transactional
    @Override
    public int updateApiList(Map<String, Object> param) {
        return mapper.updateApiList(param);
    }

    @Transactional
    @Override
    public int deleteApiList(Map<String, Object> param) {
        return mapper.deleteApiList(param);
    }

    @Transactional
    @Override
    public int deleteApiRole(Map<String, Object> param) {
        return mapper.deleteApiRole(param);
    }

    @Transactional
    @Override
    public int insertApiRole(Map<String, Object> param) {
        return mapper.insertApiRole(param);
    }

    @Override
    public int updateApiRole(Map<String, Object> param) {
        return mapper.updateApiRole(param);
    }
}
