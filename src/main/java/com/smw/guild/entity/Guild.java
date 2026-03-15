package com.smw.guild.entity;

import java.util.Map;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "GUILD")
public class Guild {
    
    @Id
    @JsonProperty("guild_id")
    @Column(name="guild_id", length = 50)
    private String guildId;
    
    @JsonProperty("guild_name")
    @Column(name="guild_name", length = 100)
    private String guildName;
    
    @JsonProperty("guild_description")
    @Column(name="guild_description", length = 500)
    private String guildDescription;
    
    @JsonProperty("guild_leader_id")
    @Column(name="guild_leader_id", length = 20)
    private String guildLeaderId;
    
    @JsonProperty("max_members")
    @Column(name="max_members")
    private Integer maxMembers;
    
    @JsonProperty("current_members")
    @Column(name="current_members")
    private Integer currentMembers;
    
    @JsonProperty("join_type")
    @Column(name="join_type", length = 10)
    private String joinType;
    
    @JsonProperty("usg_yn")
    @Column(name="usg_yn", length = 1)
    private String usgYn;
    
    @JsonProperty("del_yn")
    @Column(name="del_yn", length = 1)
    private String delYn;
    
    @JsonProperty("crt_user_id")
    @Column(name="crt_user_id", length = 50)
    private String crtUserId;
    
    @JsonProperty("crt_date")
    @Column(name="crt_date")
    private String crtDate;
    
    @JsonProperty("upt_user_id")
    @Column(name="upt_user_id", length = 50)
    private String uptUserId;
    
    @JsonProperty("upt_date")
    @Column(name="upt_date")
    private String uptDate;

    public Map<String, Object> convertMap() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(this, new TypeReference<Map<String, Object>>(){});
    }
}

