package com.smw.guild.entity;

import static com.querydsl.core.types.PathMetadataFactory.*;

import com.querydsl.core.types.dsl.*;

import com.querydsl.core.types.PathMetadata;
import javax.annotation.Generated;
import com.querydsl.core.types.Path;


/**
 * QGuild is a Querydsl query type for Guild
 */
@Generated("com.querydsl.codegen.EntitySerializer")
public class QGuild extends EntityPathBase<Guild> {

    private static final long serialVersionUID = 2007962465L;

    public static final QGuild guild = new QGuild("guild");

    public final StringPath crtDate = createString("crtDate");

    public final StringPath crtUserId = createString("crtUserId");

    public final NumberPath<Integer> currentMembers = createNumber("currentMembers", Integer.class);

    public final StringPath delYn = createString("delYn");

    public final StringPath guildDescription = createString("guildDescription");

    public final StringPath guildId = createString("guildId");

    public final StringPath guildLeaderId = createString("guildLeaderId");

    public final StringPath guildName = createString("guildName");

    public final StringPath joinType = createString("joinType");

    public final NumberPath<Integer> maxMembers = createNumber("maxMembers", Integer.class);

    public final StringPath uptDate = createString("uptDate");

    public final StringPath uptUserId = createString("uptUserId");

    public final StringPath usgYn = createString("usgYn");

    public QGuild(String variable) {
        super(Guild.class, forVariable(variable));
    }

    public QGuild(Path<? extends Guild> path) {
        super(path.getType(), path.getMetadata());
    }

    public QGuild(PathMetadata metadata) {
        super(Guild.class, metadata);
    }

}

