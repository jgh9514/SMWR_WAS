package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Swarfarm 레벨 API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmLevelResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<LevelData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LevelData {
        private Integer id;
        private String url;
        private Integer dungeon;
        
        @JsonProperty("floor")
        private Integer floor;
        
        private String difficulty;
        
        @JsonProperty("energy_cost")
        private Integer energyCost;
        
        private Integer xp;
        
        @JsonProperty("frontline_slots")
        private Integer frontlineSlots;
        
        @JsonProperty("backline_slots")
        private Integer backlineSlots;
        
        @JsonProperty("total_slots")
        private Integer totalSlots;
        
        private List<WaveData> waves;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WaveData {
        private List<EnemyData> enemies;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnemyData {
        private Integer id;
        private Integer monster;
        private Integer stars;
        private Integer level;
        private Integer hp;
        private Integer attack;
        private Integer defense;
        private Integer speed;
        private Integer resist;
        
        @JsonProperty("crit_bonus")
        private Integer critBonus;
        
        @JsonProperty("crit_damage_reduction")
        private Integer critDamageReduction;
        
        @JsonProperty("accuracy_bonus")
        private Integer accuracyBonus;
    }
}

