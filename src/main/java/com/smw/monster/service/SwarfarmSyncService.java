package com.smw.monster.service;

import java.util.function.Consumer;

public interface SwarfarmSyncService {

    void setLogCallback(Consumer<String> logCallback);
}
