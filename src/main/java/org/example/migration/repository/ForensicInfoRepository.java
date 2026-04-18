package org.example.migration.repository;

import org.example.migration.postgres.ForensicInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
//public interface ForensicInfoRepository extends JpaRepository<ForensicInfo, String>, JpaSpecificationExecutor<ForensicInfo> {
    public interface ForensicInfoRepository extends JpaRepository<ForensicInfo, String>, CustomizedForensicsRepository {

    Page<ForensicInfo> findAll(Specification<ForensicInfo>  spec, Pageable pageable);

    ForensicInfo findByIdAndAccountId(String investigationId, String accountId);

    /**
     * Lightweight query to get min and max original_insight_time for a given investigation.
     * Returns Object[] where [0] = min date, [1] = max date.
     * Avoids loading the full ForensicInfo entity + lazy insights proxy.
     */
    @Query(value = "SELECT MIN(i.original_insight_time), MAX(i.original_insight_time) " +
            "FROM insights_info i " +
            "WHERE i.account_id = :accountId AND i.forensic_info_id = :investigationId",
            nativeQuery = true)
    Object[] findInsightTimeBounds(@Param("accountId") String accountId,
                                   @Param("investigationId") String investigationId);
}
