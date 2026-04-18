package org.example.migration.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for refreshing materialized views on a schedule,
 * rather than on every API request.
 *
 * Uses REFRESH MATERIALIZED VIEW CONCURRENTLY where possible
 * (requires a UNIQUE index on the materialized view).
 */
@Slf4j
@Service
public class MaterializedViewRefreshService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Refreshes all insights-related materialized views.
     * Runs every 5 minutes by default. Adjust the cron/fixedRate as needed.
     *
     * To use CONCURRENTLY (non-blocking reads during refresh), ensure each MV has a UNIQUE index:
     *   CREATE UNIQUE INDEX idx_mv_date_hist_pk ON mv_insights_date_histogram(period, account_id, forensic_info_id);
     *   CREATE UNIQUE INDEX idx_mv_type_pk ON mv_insights_type_counts(account_id, forensic_info_id, type, original_insight_time);
     *   CREATE UNIQUE INDEX idx_mv_category_pk ON mv_insights_category_counts(account_id, forensic_info_id, category, original_insight_time);
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void refreshAllMaterializedViews() {
        long start = System.currentTimeMillis();
        log.info("Starting scheduled materialized view refresh...");
        try {
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_date_histogram").executeUpdate();
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_type_counts").executeUpdate();
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_insights_category_counts").executeUpdate();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Materialized view refresh completed in {}ms", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Materialized view refresh failed after {}ms", elapsed, e);
        }
    }

    /**
     * On-demand refresh — call this after data mutations (e.g., after saveForensicInfo).
     * Uses non-concurrent refresh (blocking but simpler) for immediate consistency.
     */
    @Transactional
    public void refreshOnDemand() {
        long start = System.currentTimeMillis();
        log.info("On-demand materialized view refresh triggered...");
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_date_histogram").executeUpdate();
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_type_counts").executeUpdate();
        entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW mv_insights_category_counts").executeUpdate();
        long elapsed = System.currentTimeMillis() - start;
        log.info("On-demand materialized view refresh completed in {}ms", elapsed);
    }
}

