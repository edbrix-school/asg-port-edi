package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.ShipMateContainerDtl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ShipMateContainerDtlRepository extends JpaRepository<ShipMateContainerDtl, Long> {

    @Query(value = "SELECT TRANSACTION_POID FROM SHIP_MATE_HDR " +
            "WHERE TRIM(BOOKING_ISSUE_NO) = TRIM(:bookingNo) AND LINE_POID = :linePoid", nativeQuery = true)
    Long findTransactionPoidByBookingNoAndLinePoid(@Param("bookingNo") String bookingNo,
                                                   @Param("linePoid") Long linePoid);

    @Query(value = "SELECT MAX(TO_NUMBER(DET_ROW_ID)) FROM SHIP_MATE_CONTAINER_DTL " +
            "WHERE TRANSACTION_POID = :transactionPoid", nativeQuery = true)
    Long findMaxDetRowId(@Param("transactionPoid") Long transactionPoid);

    @Query(value = "SELECT MAX(EQUIPMENT_ISO_TYPE) FROM SHIP_VOYAGE_HDR VHDR " +
            "INNER JOIN SHIP_BL_MANIFEST_HDR MHDR ON VHDR.TRANSACTION_POID = MHDR.VOYAGE_TRANSACTION_POID " +
            "INNER JOIN SHIP_BL_MANIFEST_CONTAINER_DTL CNTDTL ON CNTDTL.TRANSACTION_POID = MHDR.TRANSACTION_POID " +
            "WHERE CONTAINER_NO = :containerNo", nativeQuery = true)
    String findEquipmentIsoTypeByContainerNo(@Param("containerNo") String containerNo);

    @Query(value = "SELECT MAX(EMPTY_DATE_OUT) FROM EDI_CONTAINER_GATE_IN_OUT " +
            "WHERE CONTAINER_NO = :containerNo AND LINECODE = :lineCode " +
            "GROUP BY LINECODE, CONTAINER_NO", nativeQuery = true)
    LocalDateTime findMaxEmptyDateOut(@Param("containerNo") String containerNo,
                                      @Param("lineCode") String lineCode);

    @Query(value = "SELECT DISTINCT 'X' FROM SHIP_CONTAINER_INVENTORY " +
            "WHERE MOVES_TYPE IN ('LDMTY', 'LDFULL') AND LINK_TRANSACTION_POID = :transactionPoid",
            nativeQuery = true)
    String checkContainerInventoryExists(@Param("transactionPoid") Long transactionPoid);

    @Query(value = "SELECT MAX(TRANSACTION_POID) FROM SHIP_MATE_CONTAINER_DTL " +
            "WHERE CONTAINER_NO = :containerNo", nativeQuery = true)
    Long findTransactionPoidByContainerNo(@Param("containerNo") String containerNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE SHIP_MATE_CONTAINER_DTL SET " +
            "ISSUE_TO_SHIPPER = NVL(ISSUE_TO_SHIPPER, NVL(:stuffingExport, :emptyDateOut)), " +
            "RETURN_FROM_SHIPPER = NVL(RETURN_FROM_SHIPPER, NVL(:stuffingExport, :exportDateInFull)), " +
            "EQUIPMENT_SEAL_NO = :sealNo, GRS_WEIGHT = :grossWeight, " +
            "VGM_WEIGHT = :vgmWeight, EQUIPMENT_ISO_TYPE = :equipmentIsoType " +
            "WHERE TRANSACTION_POID = :transactionPoid AND CONTAINER_NO = :containerNo", nativeQuery = true)
    int updateMateContainerDtl(@Param("transactionPoid") Long transactionPoid,
                               @Param("containerNo") String containerNo,
                               @Param("emptyDateOut") LocalDateTime emptyDateOut,
                               @Param("exportDateInFull") LocalDateTime exportDateInFull,
                               @Param("stuffingExport") LocalDateTime stuffingExport,
                               @Param("sealNo") String sealNo,
                               @Param("grossWeight") String grossWeight,
                               @Param("vgmWeight") String vgmWeight,
                               @Param("equipmentIsoType") String equipmentIsoType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE SHIP_MATE_CONTAINER_DTL SET TRANSACTION_POID = :newTransactionPoid, " +
            "DET_ROW_ID = :detRowId WHERE CONTAINER_NO = :containerNo " +
            "AND TRANSACTION_POID = :oldTransactionPoid AND RETURN_FROM_SHIPPER IS NULL", nativeQuery = true)
    int updateMateContainerDtlTransaction(@Param("containerNo") String containerNo,
                                          @Param("oldTransactionPoid") Long oldTransactionPoid,
                                          @Param("newTransactionPoid") Long newTransactionPoid,
                                          @Param("detRowId") Long detRowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO SHIP_MATE_CONTAINER_DTL " +
            "(TRANSACTION_POID, DET_ROW_ID, CONTAINER_NO, EQUIPMENT_SEAL_NO, EQUIPMENT_ISO_TYPE, " +
            "EQUIPMENT_TYPE, EQUIPMENT_SIZE, QUANTITY, GRS_VOLUME, GRS_WEIGHT, NET_VOLUME, NET_WEIGHT, " +
            "NO_OF_PACKS, PACK_UNIT, CREATED_BY, CREATED_DATE, ISSUE_TO_SHIPPER, RETURN_FROM_SHIPPER, VGM_WEIGHT) " +
            "VALUES (:transactionPoid, :detRowId, :containerNo, :sealNo, :equipmentIsoType, " +
            "NULL, NULL, 1, NULL, :grossWeight, NULL, NULL, NULL, NULL, 'AUTOUPDATE', SYSDATE, " +
            "NVL(NVL(:stuffingExport, :emptyDateOut), :issueToShipper), " +
            "NVL(:stuffingExport, :exportDateInFull), :vgmWeight)", nativeQuery = true)
    int insertMateContainerDtl(@Param("transactionPoid") Long transactionPoid,
                               @Param("detRowId") Long detRowId,
                               @Param("containerNo") String containerNo,
                               @Param("sealNo") String sealNo,
                               @Param("equipmentIsoType") String equipmentIsoType,
                               @Param("emptyDateOut") LocalDateTime emptyDateOut,
                               @Param("exportDateInFull") LocalDateTime exportDateInFull,
                               @Param("stuffingExport") LocalDateTime stuffingExport,
                               @Param("grossWeight") String grossWeight,
                               @Param("vgmWeight") String vgmWeight,
                               @Param("issueToShipper") LocalDateTime issueToShipper);
}

