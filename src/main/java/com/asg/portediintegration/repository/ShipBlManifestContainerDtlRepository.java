package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.ShipBlManifestContainerDtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ShipBlManifestContainerDtlRepository extends JpaRepository<ShipBlManifestContainerDtl, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE SHIP_BL_MANIFEST_CONTAINER_DTL SET " +
            "ISSUE_TO_CONSIGNEE = NVL(ISSUE_TO_CONSIGNEE, NVL(:stipingImport, :importGateOutFull)), " +
            "RETURN_FROM_CONSIGNEE = NVL(RETURN_FROM_CONSIGNEE, NVL(:stipingImport, :emptyGateIn)), " +
            "CREATED_DATE = SYSDATE, CREATED_BY = 'AUTOUPDATE' " +
            "WHERE (CONTAINER_NO, TRANSACTION_POID) IN " +
            "(SELECT CONTAINER_NO, TRANSACTION_POID FROM " +
            "(SELECT HDR.TRANSACTION_POID, CONTAINER_NO, MAX(NVL(ARRIVAL_DATE, EXPECTED_DATE)) " +
            "FROM SHIP_BL_MANIFEST_HDR HDR, SHIP_BL_MANIFEST_CONTAINER_DTL TRN, SHIP_VOYAGE_HDR VOYAGE " +
            "WHERE HDR.VOYAGE_TRANSACTION_POID = VOYAGE.TRANSACTION_POID " +
            "AND HDR.TRANSACTION_POID = TRN.TRANSACTION_POID " +
            "AND BL_TYPE = 'IMPORT' AND LINE_POID = :linePoid AND CONTAINER_NO = :containerNo " +
            "GROUP BY HDR.TRANSACTION_POID, CONTAINER_NO)) " +
            "AND CONTAINER_NO = :containerNo", nativeQuery = true)
    int updateManifestContainerDtl(@Param("containerNo") String containerNo,
                                   @Param("linePoid") Long linePoid,
                                   @Param("importGateOutFull") LocalDateTime importGateOutFull,
                                   @Param("emptyGateIn") LocalDateTime emptyGateIn,
                                   @Param("stipingImport") LocalDateTime stipingImport);
}

