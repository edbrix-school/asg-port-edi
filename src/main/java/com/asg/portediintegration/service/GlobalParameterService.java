package com.asg.portediintegration.service;

import com.asg.portediintegration.entity.GlobalParameter;
import com.asg.portediintegration.repository.GlobalParameterRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GlobalParameterService {

    private final GlobalParameterRepository repository;

    // in-memory cache
    private final Map<String, GlobalParameter> cache = new ConcurrentHashMap<>();
    private volatile long lastReloadTime = 0;
    private static final long CACHE_TTL_MS = 300000; // 5 minutes

    private static final String CATEGORY = "SHIPPING";
    private static final Long GROUP_POID = 1L;
    private static final String NOT_DELETED = "N";

    @PostConstruct
    public void loadAtStartup() {
//        reload();
    }


    public void reload() {
        try {
            log.info("Loading GLOBAL_PARAMETERS [category={}, group={}]", CATEGORY, GROUP_POID);
            List<GlobalParameter> result = repository.findByCategoryAndGroupPoidAndDeleted(CATEGORY, GROUP_POID, NOT_DELETED);
            if (result == null) {
                result = List.of();
            }

            log.info("Repository returned {} rows", result.size());

            if (log.isDebugEnabled()) {
                result.forEach(p -> log.debug("Param: name={}, value={}", p.getParameterName(), p.getParameterValue()));
            }

            Map<String, GlobalParameter> newCache = result.stream().collect(Collectors.toMap(
                    GlobalParameter::getParameterName,
                    Function.identity(),
                    (existing, duplicate) -> {
                        log.error("Duplicate GLOBAL_PARAMETER [{}] found. Keeping POID={}, discarding POID={}", existing.getParameterName(), existing.getParameterPoid(), duplicate.getParameterPoid());
                        return existing;
                    }
            ));

            cache.clear();
            cache.putAll(newCache);

            lastReloadTime = System.currentTimeMillis();
            log.info("Loaded {} global parameters", cache.size());
        } catch (Exception e) {
            log.error("Failed to reload global parameters, keeping old cache", e);
        }
    }


    public String getValue(String key) {
        checkAndReloadIfExpired();
        GlobalParameter param = cache.get(key);
        return param != null ? param.getParameterValue() : null;
    }

    public String getLinuxAwareValue(String key) {
        checkAndReloadIfExpired();
        GlobalParameter param = cache.get(key);
        if (param == null) return null;

        boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");

        return isLinux && StringUtils.isNotBlank(param.getParameterLinuxValue()) ? param.getParameterLinuxValue() : param.getParameterValue();
    }

    private void checkAndReloadIfExpired() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReloadTime > CACHE_TTL_MS) {
            synchronized (this) {
                if (currentTime - lastReloadTime > CACHE_TTL_MS) {
                    reload();
                }
            }
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    @Scheduled(fixedRate = 300000) // Refresh every 5 minutes
    public void scheduledReload() {
        reload();
    }
}

