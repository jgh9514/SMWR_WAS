package com.sysconf.util;

import com.querydsl.core.types.Expression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ResultUtil {

    /**
     * Transform List<Map<Expression<?>, ?>> to List<Map<String, ?>> for easier control
     * @param fetchResult
     * @Author choi
     * @return
     */
    public static List<Map<String, ?>> transformListMap(List<Map<Expression<?>, ?>> fetchResult){
        List<Map<String, ?>> resultList = new ArrayList<>();

        for (Map<Expression<?>, ?> resultMap : fetchResult) {
            Map<String, Object> rowMap = new HashMap<>();
            for (Expression<?> key : resultMap.keySet()) {
                String columnName = key.toString();
                Object value = resultMap.get(key);
                try {
                    log.debug("columnName = " + columnName);
                    if (columnName.indexOf(" as ") > 0) {
                        String[] alias = columnName.split(" as ");
                        columnName = alias[1].trim();
                    } else {
                        String[] colName = columnName.split(".");
                        columnName = colName[1].trim();
                    }
                } catch (IndexOutOfBoundsException e) {
                    log.info("Alias specification is missing. Please check.");
                    e.printStackTrace();
                }

                rowMap.put(columnName, value);
            }
            resultList.add(rowMap);
        }

        return resultList;
    }
    
    /**
     * Transform Map<Expression<?>, ?> to Map<String, Object>
     * @param fetchResult
     * @return
     */
    public static Map<String, Object> transformMap(Map<Expression<?>, ?> fetchResult){
        if (fetchResult == null) return null;
        
        Map<String, Object> rowMap = new HashMap<>();
        for (Expression<?> key : fetchResult.keySet()) {
            String columnName = key.toString();
            Object value = fetchResult.get(key);
            try {
                if (columnName.indexOf(" as ") > 0) {
                    String[] alias = columnName.split(" as ");
                    columnName = alias[1].trim();
                } else {
                    String[] colName = columnName.split(".");
                    columnName = colName[1].trim();
                }
            } catch (IndexOutOfBoundsException e) {
                log.info("Alias specification is missing. Please check.");
                e.printStackTrace();
            }
            rowMap.put(columnName, value);
        }
        return rowMap;
    }
}

