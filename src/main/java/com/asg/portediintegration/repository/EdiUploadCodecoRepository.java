package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.EdiUploadCodeco;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EdiUploadCodecoRepository extends JpaRepository<EdiUploadCodeco, String> {

    @Query(value = "SELECT MAX(TO_NUMBER(EDI_REF_NO)) FROM EDI_UPLOAD_CODECO WHERE REGEXP_LIKE(EDI_REF_NO, '^[0-9]+$')", nativeQuery = true)
    Long findMaxEdiRefNo();

    @Query("SELECT e FROM EdiUploadCodeco e WHERE e.tabText = :tabText AND e.fileLoadName = :fileLoadName")
    List<EdiUploadCodeco> findByTabTextAndFileLoadName(@Param("tabText") String tabText, @Param("fileLoadName") String fileLoadName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EdiUploadCodeco e SET e.ediLoadFlag = 'Y' WHERE e.fileLoadName = :fileLoadName")
    void updateEdiLoadFlag(@Param("fileLoadName") String fileLoadName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EdiUploadCodeco e SET e.ediLoadFlag = 'Y' WHERE e.ediRefNo = :ediRefNo AND e.fileLoadName = :fileLoadName")
    void updateEdiLoadFlagByRefNo(@Param("ediRefNo") String ediRefNo, @Param("fileLoadName") String fileLoadName);

    @Query("SELECT e FROM EdiUploadCodeco e WHERE e.ediRefNo = :ediRefNo AND e.fileLoadName = :fileLoadName " +
            "ORDER BY e.seqnoFileLine, e.mainGroupNo, e.slotNumber")
    List<EdiUploadCodeco> findByEdiRefNoAndFileLoadName(@Param("ediRefNo") String ediRefNo,
                                                        @Param("fileLoadName") String fileLoadName);

    @Query("SELECT e FROM EdiUploadCodeco e WHERE e.tabText = :tabText AND e.fileLoadName = :fileLoadName")
    List<EdiUploadCodeco> findByTabTextAndFileLoadNameForCoarri(@Param("tabText") String tabText,
                                                                @Param("fileLoadName") String fileLoadName);
}