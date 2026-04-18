package org.example.migration.repository;

import org.example.migration.postgres.InsightsInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
//public interface InsightsInfoRepository extends JpaRepository<InsightsInfo, String>, JpaSpecificationExecutor<InsightsInfo> {
    public interface InsightsInfoRepository extends JpaRepository<InsightsInfo, String>, CustomizedInsightsRepository {

        Page<InsightsInfo> findAll(Specification<InsightsInfo> spec, Pageable pageable);
    //InsightsInfo findByInsightIdAndAccountId(String insightId, String accountId);

    List<InsightsInfo> findAll(Specification<InsightsInfo> spec);

}

