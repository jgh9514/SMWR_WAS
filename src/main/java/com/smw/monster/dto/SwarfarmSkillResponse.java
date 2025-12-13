package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Swarfarm 스킬 API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmSkillResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<SkillData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillData {
        private Integer id;
        private String url;
        
        @JsonProperty("com2us_id")
        private Integer com2usId;
        
        private String name;
        private String description;
        private Integer slot;
        private Integer cooltime;
        private Integer hits;
        private Boolean passive;
        private Boolean aoe;
        private Boolean random;
        
        @JsonProperty("max_level")
        private Integer maxLevel;
        
        private List<UpgradeData> upgrades;
        private List<EffectData> effects;
        
        @JsonProperty("multiplier_formula")
        private String multiplierFormula;
        
        @JsonProperty("multiplier_formula_raw")
        private String multiplierFormulaRaw;
        
        @JsonProperty("scales_with")
        private List<String> scalesWith;
        
        @JsonProperty("icon_filename")
        private String iconFilename;
        
        @JsonProperty("used_on")
        private List<Integer> usedOn;
        
        @JsonProperty("level_progress_description")
        private List<String> levelProgressDescription;
        
        @JsonProperty("other_skill")
        private Integer otherSkill;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpgradeData {
        private String effect;
        private Integer amount;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EffectData {
        private EffectInfo effect;
        private Boolean aoe;
        
        @JsonProperty("single_target")
        private Boolean singleTarget;
        
        @JsonProperty("self_effect")
        private Boolean selfEffect;
        
        private Integer chance;
        
        @JsonProperty("on_crit")
        private Boolean onCrit;
        
        @JsonProperty("on_death")
        private Boolean onDeath;
        
        private Boolean random;
        private Integer quantity;
        private Boolean all;
        
        @JsonProperty("self_hp")
        private Boolean selfHp;
        
        @JsonProperty("target_hp")
        private Boolean targetHp;
        
        private Boolean damage;
        private String note;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EffectInfo {
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

