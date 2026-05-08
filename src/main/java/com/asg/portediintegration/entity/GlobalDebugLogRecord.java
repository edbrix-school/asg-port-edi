package com.asg.portediintegration.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Immutable
@Table(name = "GLOBAL_DEBUG_LOG_RECORDS")
public class GlobalDebugLogRecord {

    // Composite key using columns that exist in the table (based on INSERT query)
    @EmbeddedId
    private GlobalDebugLogRecordId id;

    @Column(name = "DEBUG_RECORD", insertable = false, updatable = false)
    private String debugRecord;

    @Column(name = "DEBUG_REMARKS", insertable = false, updatable = false)
    private String debugRemarks;

    @Embeddable
    @Data
    @EqualsAndHashCode
    public static class GlobalDebugLogRecordId implements java.io.Serializable {
        @Column(name = "PROCEDURE_FUNCTION_NAME")
        private String procedureFunctionName;

        @Column(name = "DEBUG_VALUE")
        private String debugValue;

        @Column(name = "DEBUT_DATE")
        private LocalDateTime debugDate;
    }
}

