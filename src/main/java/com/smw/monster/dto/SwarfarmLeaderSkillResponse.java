package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Swarfarm 리더 스킬 API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmLeaderSkillResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<LeaderSkillData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LeaderSkillData {
        private Integer id;
        private String url;
        private String attribute;
        private Integer amount;
        private String area;
        private String element;
    }
}

