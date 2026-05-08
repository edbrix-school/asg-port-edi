package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.EdiContainerGateInOut;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EdiContainerGateInOutRepository extends JpaRepository<EdiContainerGateInOut, Long> {

    /**
     * Find containers for update - equivalent to CURSOR C_UPD in PORT_EDI_INSERT_RECORDS
     * Uses MAX() aggregation to match procedure logic exactly
     * Returns: Object[] = [CONTAINER_NO, LINECODE, EDI_LOAD_DATE, MAX(IMPORT_GATE_OUT_FULL), MAX(EMPTY_GATE_IN), MAX(STIPING_IMPORT)]
     */
    @Query(value = "SELECT CONTAINER_NO, " +
            "LINECODE, " +
            "EDI_LOAD_DATE, " +
            "MAX(IMPORT_GATE_OUT_FULL) as IMPORT_GATE_OUT_FULL, " +
            "MAX(EMPTY_GATE_IN) as EMPTY_GATE_IN, " +
            "MAX(STIPING_IMPORT) as STIPING_IMPORT " +
            "FROM EDI_CONTAINER_GATE_IN_OUT " +
            "WHERE EDI_LOAD_DATE >= :fromDate " +
            "AND REMARKS IS NULL " +
            "AND (IMPORT_GATE_OUT_FULL IS NOT NULL OR EMPTY_GATE_IN IS NOT NULL OR STIPING_IMPORT IS NOT NULL) " +
            "GROUP BY CONTAINER_NO, LINECODE, EDI_LOAD_DATE " +
            "ORDER BY LINECODE, CONTAINER_NO", nativeQuery = true)
    List<Object[]> findContainersForUpdateNative(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Find containers for MATE update - equivalent to CURSOR C_MATE in PORT_EDI_INSERT_RECORDS
     * Uses MAX() aggregation to match procedure logic exactly
     * Returns: Object[] = [CONTAINER_NO, LINECODE, EDI_LOAD_DATE, BOOKING_NO, MAX(SEALNO), MAX(GROSS_WEIGHT), MAX(VGM_WEIGHT), MAX(EMPTY_DATE_OUT), MAX(EXPORT_DATE_IN_FULL), MAX(STUFFING_EXPORT)]
     */
    @Query(value = "SELECT CONTAINER_NO, " +
            "LINECODE, " +
            "EDI_LOAD_DATE, " +
            "BOOKING_NO, " +
            "MAX(NVL(SEALNO, '0')) as SEALNO, " +
            "MAX(NVL(SUBSTR(GROSS_WEIGHT, INSTR(GROSS_WEIGHT, ':') + 1), '0')) as GROSS_WEIGHT, " +
            "MAX(NVL(SUBSTR(VGM_WEIGHT, INSTR(VGM_WEIGHT, ':') + 1), '0')) as VGM_WEIGHT, " +
            "MAX(EMPTY_DATE_OUT) as EMPTY_DATE_OUT, " +
            "MAX(EXPORT_DATE_IN_FULL) as EXPORT_DATE_IN_FULL, " +
            "MAX(STUFFING_EXPORT) as STUFFING_EXPORT " +
            "FROM EDI_CONTAINER_GATE_IN_OUT " +
            "WHERE EDI_LOAD_DATE >= :fromDate " +
            "AND REMARKS IS NULL " +
            "AND BOOKING_NO IS NOT NULL " +
            "AND BOOKING_NO <> 'NOT PRESENT' " +
            "GROUP BY CONTAINER_NO, LINECODE, EDI_LOAD_DATE, BOOKING_NO " +
            "ORDER BY LINECODE, CONTAINER_NO, BOOKING_NO", nativeQuery = true)
    List<Object[]> findContainersForMateUpdateNative(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Find existing gate record by container number and line code
     */
    @Query("SELECT e FROM EdiContainerGateInOut e WHERE e.containerNo = :containerNo AND e.lineCode = :lineCode")
    List<EdiContainerGateInOut> findByContainerNoAndLineCode(@Param("containerNo") String containerNo,
                                                             @Param("lineCode") String lineCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EdiContainerGateInOut e SET e.remarks = :remarks WHERE e.containerNo = :containerNo " +
            "AND e.lineCode = :lineCode AND e.remarks IS NULL " +
            "AND (e.importGateOutFull IS NOT NULL OR e.emptyGateIn IS NOT NULL OR e.stipingImport IS NOT NULL)")
    void updateRemarksForContainer(@Param("containerNo") String containerNo,
                                   @Param("lineCode") String lineCode,
                                   @Param("remarks") String remarks);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EdiContainerGateInOut e SET e.remarks = :remarks WHERE e.containerNo = :containerNo " +
            "AND e.lineCode = :lineCode AND e.remarks IS NULL " +
            "AND e.bookingNo IS NOT NULL AND e.bookingNo != 'NOT PRESENT'")
    void updateRemarksForMateContainer(@Param("containerNo") String containerNo,
                                       @Param("lineCode") String lineCode,
                                       @Param("remarks") String remarks);
}
