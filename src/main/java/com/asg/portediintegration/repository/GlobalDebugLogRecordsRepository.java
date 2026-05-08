package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.GlobalDebugLogRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface GlobalDebugLogRecordsRepository extends JpaRepository<GlobalDebugLogRecord, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO GLOBAL_DEBUG_LOG_RECORDS " +
            "(PROCEDURE_FUNCTION_NAME, DEBUG_VALUE, DEBUG_RECORD, DEBUG_REMARKS, DEBUT_DATE) " +
            "VALUES (:procedureName, :debugValue, :debugRecord, :debugRemarks, :debugDate)", nativeQuery = true)
    void insertDebugLog(@Param("procedureName") String procedureName,
                        @Param("debugValue") String debugValue,
                        @Param("debugRecord") String debugRecord,
                        @Param("debugRemarks") String debugRemarks,
                        @Param("debugDate") LocalDateTime debugDate);
}

