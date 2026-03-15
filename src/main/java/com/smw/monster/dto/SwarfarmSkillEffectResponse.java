package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Swarfarm 스킬 이펙트 API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmSkillEffectResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<SkillEffectData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillEffectData {
        private Integer id;
        private String url;
        private String name;
        
        @JsonProperty("is_buff")
        private Boolean isBuff;
        
        private String type;
        private String description;
        
        @JsonProperty("icon_filename")
        private String iconFilename;
    }
}

