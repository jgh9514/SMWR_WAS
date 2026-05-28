package com.smw.monster.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class MonsterListCacheWarmup {

    private static final Logger log = LoggerFactory.getLogger(MonsterListCacheWarmup.class);

    private final summonerswarService swService;

    MonsterListCacheWarmup(summonerswarService swService) {
        this.swService = swService;
    }

    /** 앱 완전 기동 후 비동기로 monsterList 캐시 워밍 — 첫 사용자 요청이 DB를 직접 치지 않도록 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmMonsterListCache() {
        Thread.ofVirtual().name("monster-list-cache-warmup").start(() -> {
            try {
                long t = System.currentTimeMillis();
                Map<String, Object> param = new HashMap<>(2);
                param.put("siegeDedupeSecondAwakening", true);
                swService.selectMonsterList(param);
                log.info("[MonsterListCacheWarmup] monsterList cache warmed in {}ms", System.currentTimeMillis() - t);
            } catch (Exception e) {
                log.warn("[MonsterListCacheWarmup] cache warmup failed — will be populated on first request: {} — {}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        });
    }
}
