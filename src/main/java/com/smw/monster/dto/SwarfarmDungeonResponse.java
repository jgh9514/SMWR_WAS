package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Swarfarm 던전 API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmDungeonResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<DungeonData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DungeonData {
        private Integer id;
        private String url;
        private Boolean enabled;
        private String name;
        private String slug;
        private String category;
        private String icon;
        private List<Integer> levels;
    }
}

