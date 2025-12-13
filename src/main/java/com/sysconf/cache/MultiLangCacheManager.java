package com.sysconf.cache;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MultiLang 객체에 Read, Write 동시 작업 직렬화. 및 Lock 로직
 */
@SuppressWarnings("serial")
public class MultiLangCacheManager implements Serializable {
    private static Map<String, String> cacheLangMapKo = new HashMap<String, String>();
    private static Map<String, String> cacheLangMapEn = new HashMap<String, String>();
    private static Map<String, String> cacheLangMapZh = new HashMap<String, String>();

    // Read-Write Lock 객체 생성
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // 쓰기 작업을 동기화: 쓰기 작업은 락을 독점적으로 획득
    public static void setCacheLangMapKo(Map<String, String> cacheLangMapKo) {
        lock.writeLock().lock(); // 쓰기 락 획득
        try {
            MultiLangCacheManager.cacheLangMapKo = cacheLangMapKo;
        } finally {
            lock.writeLock().unlock(); // 쓰기 락 해제
        }
    }

    public static void setCacheLangMapEn(Map<String, String> cacheLangMapEn) {
        lock.writeLock().lock(); // 쓰기 락 획득
        try {
            MultiLangCacheManager.cacheLangMapEn = cacheLangMapEn;
        } finally {
            lock.writeLock().unlock(); // 쓰기 락 해제
        }
    }

    public static void setCacheLangMapZh(Map<String, String> cacheLangMapZh) {
        lock.writeLock().lock(); // 쓰기 락 획득
        try {
            MultiLangCacheManager.cacheLangMapZh = cacheLangMapZh;
        } finally {
            lock.writeLock().unlock(); // 쓰기 락 해제
        }
    }

    // 읽기 작업을 동기화: 읽기 작업은 여러 쓰레드가 동시에 가능
    public static Map<String, String> getCacheLangMapKo() {
        lock.readLock().lock(); // 읽기 락 획득
        try {
            return cacheLangMapKo;
        } finally {
            lock.readLock().unlock(); // 읽기 락 해제
        }
    }

    public static Map<String, String> getCacheLangMapEn() {
        lock.readLock().lock(); // 읽기 락 획득
        try {
            return cacheLangMapEn;
        } finally {
            lock.readLock().unlock(); // 읽기 락 해제
        }
    }

    public static Map<String, String> getCacheLangMapZh() {
        lock.readLock().lock(); // 읽기 락 획득
        try {
            return cacheLangMapZh;
        } finally {
            lock.readLock().unlock(); // 읽기 락 해제
        }
    }
}

