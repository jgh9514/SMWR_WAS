package com.smw.monster.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Swarfarm API 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SwarfarmMonsterResponse {
    private Integer count;
    private String next;
    private String previous;
    private List<MonsterData> results;
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MonsterData {
        private Integer id;
        private String url;
        private String bestiarySlug;
        
        @JsonProperty("com2us_id")
        private Integer com2usId;
        
        @JsonProperty("family_id")
        private Integer familyId;
        
        @JsonProperty("skill_group_id")
        private Integer skillGroupId;
        
        private String name;
        
        @JsonProperty("image_filename")
        private String imageFilename;
        
        private String element;
        private String archetype;
        
        @JsonProperty("base_stars")
        private Integer baseStars;
        
        @JsonProperty("natural_stars")
        private Integer naturalStars;
        
        private Boolean obtainable;
        
        @JsonProperty("can_awaken")
        private Boolean canAwaken;
        
        @JsonProperty("awaken_level")
        private Integer awakenLevel;
        
        @JsonProperty("awaken_bonus")
        private String awakenBonus;
        
        private List<Integer> skills;
        
        @JsonProperty("skill_ups_to_max")
        private Integer skillUpsToMax;
        
        @JsonProperty("leader_skill")
        private Object leaderSkill;
        
        @JsonProperty("homunculus_skills")
        private List<Object> homunculusSkills;
        
        @JsonProperty("base_hp")
        private Integer baseHp;
        
        @JsonProperty("base_attack")
        private Integer baseAttack;
        
        @JsonProperty("base_defense")
        private Integer baseDefense;
        
        private Integer speed;
        
        @JsonProperty("crit_rate")
        private Integer critRate;
        
        @JsonProperty("crit_damage")
        private Integer critDamage;
        
        private Integer resistance;
        private Integer accuracy;
        
        @JsonProperty("raw_hp")
        private Integer rawHp;
        
        @JsonProperty("raw_attack")
        private Integer rawAttack;
        
        @JsonProperty("raw_defense")
        private Integer rawDefense;
        
        @JsonProperty("max_lvl_hp")
        private Integer maxLvlHp;
        
        @JsonProperty("max_lvl_attack")
        private Integer maxLvlAttack;
        
        @JsonProperty("max_lvl_defense")
        private Integer maxLvlDefense;
        
        @JsonProperty("awakens_from")
        private Integer awakensFrom;
        
        @JsonProperty("awakens_to")
        private Integer awakensTo;
        
        @JsonProperty("awaken_cost")
        private List<Object> awakenCost;
        
        @JsonProperty("transforms_to")
        private Integer transformsTo;
        
        private List<SourceData> source;
        
        @JsonProperty("fusion_food")
        private Boolean fusionFood;
        
        private Boolean homunculus;
        
        @JsonProperty("craft_cost")
        private Object craftCost;
        
        @JsonProperty("craft_materials")
        private List<Object> craftMaterials;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SourceData {
        private Integer id;
        private String url;
        private String name;
        private String description;
        
        @JsonProperty("farmable_source")
        private Boolean farmableSource;
    }
}

