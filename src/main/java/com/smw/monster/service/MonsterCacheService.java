package com.smw.monster.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Service;

import com.smw.monster.mapper.SwarfarmMonsterMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 몬스터 기본 정보(이름·이미지·속성)를 JVM 메모리에 보관하는 캐시.
 * 몇 달에 한 번 신규 몬스터가 추가될 때만 reload() 호출.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonsterCacheService {

    public record MonsterInfo(String monsterId, String krName, String imageUrl, String elemental) {}

    private final SwarfarmMonsterMapper swarfarmMonsterMapper;

    private volatile Map<String, MonsterInfo> cache = Collections.emptyMap();

    @PostConstruct
    public void load() {
        reload();
    }

    public void reload() {
        try {
            List<Map<String, Object>> rows = swarfarmMonsterMapper.selectAllForCache();
            Map<String, MonsterInfo> next = new ConcurrentHashMap<>(rows.size() * 2);
            for (Map<String, Object> row : rows) {
                String id = str(row.get("monster_id"));
                if (id == null || id.isBlank()) continue;
                next.put(id, new MonsterInfo(
                    id,
                    str(row.get("kr_name")),
                    str(row.get("image_url")),
                    str(row.get("monster_elemental"))
                ));
            }
            cache = next;
            log.info("MonsterCache reloaded: {} entries", cache.size());
        } catch (Exception e) {
            log.error("MonsterCache reload 실패 — 기존 캐시 유지", e);
        }
    }

    public MonsterInfo get(String monsterId) {
        if (monsterId == null) return null;
        return cache.get(monsterId);
    }

    public Map<String, MonsterInfo> getAll() {
        return Collections.unmodifiableMap(cache);
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
