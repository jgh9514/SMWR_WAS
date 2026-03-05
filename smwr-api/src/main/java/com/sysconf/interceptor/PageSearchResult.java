package com.sysconf.interceptor;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PageSearchResult {

    public static Map<String, List<Map<String, Object>>> PAGE_ITEM_LIST;

    @PostConstruct
    public void setPageConditionList() {
//        PAGE_ITEM_LIST = convertToGroupedMap(mapper.getPageSearchInterceptorList());
    }

    public void updatePageConditionList() {
//        PAGE_ITEM_LIST = convertToGroupedMap(mapper.getPageSearchInterceptorList());
    }

    public Map<String, List<Map<String, String>>> convertToGroupedMap(List<Map<String, String>> list) {
        return list.stream()
                .filter(map -> map.get("page_id") != null)
                .collect(Collectors.groupingBy(
                        map -> map.get("page_id"),
                        Collectors.mapping(
                                map -> {
                                    Map<String, String> resultMap = new HashMap<>();
                                    resultMap.put("bind_column_nm", map.get("bind_column_nm"));
                                    resultMap.put("element_cd", map.get("element_cd"));
                                    resultMap.put("calendar_from_model_id", map.get("calendar_from_model_id"));
                                    resultMap.put("calendar_to_model_id", map.get("calendar_to_model_id"));
                                    return resultMap;
                                },
                                Collectors.toList()
                        )
                ));
    }
}


