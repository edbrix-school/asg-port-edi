package com.asg.portediintegration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "EDI_CONTAINER_GATE_IN_OUT")
public class EdiContainerGateInOut {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EDI_REF_NO")
    private Long ediRefNo;

    @Column(name = "EDI_LOAD_DATE")
    private LocalDateTime ediLoadDate;

    @Column(name = "EDI_FILE_DATE")
    private LocalDateTime ediFileDate;

    @Column(name = "SEQNO_FILE_LINE")
    private Long seqnoFileLine;

    @Column(name = "MAIN_GROUP_NO")
    private Long mainGroupNo;

    @Column(name = "SLOT_NUMBER")
    private Long slotNumber;

    @Column(name = "CONTAINER_NO")
    private String containerNo;

    @Column(name = "IMPORT_GATE_OUT_FULL")
    private LocalDateTime importGateOutFull;

    @Column(name = "EMPTY_GATE_IN")
    private LocalDateTime emptyGateIn;

    @Column(name = "EMPTY_DATE_OUT")
    private LocalDateTime emptyDateOut;

    @Column(name = "EXPORT_DATE_IN_FULL")
    private LocalDateTime exportDateInFull;

    @Column(name = "STIPING_IMPORT")
    private LocalDateTime stipingImport;

    @Column(name = "STUFFING_EXPORT")
    private LocalDateTime stuffingExport;

    @Column(name = "LINECODE")
    private String lineCode;

    @Column(name = "BOOKING_NO")
    private String bookingNo;

    @Column(name = "PODEST")
    private String podest;

    @Column(name = "PODISCH")
    private String podisch;

    @Column(name = "SEALNO")
    private String sealNo;

    @Column(name = "VESSAL_VOYAGE")
    private String vessalVoyage;

    @Column(name = "GROSS_WEIGHT")
    private String grossWeight;

    @Column(name = "VGM_WEIGHT")
    private String vgmWeight;

    @Column(name = "TITLE")
    private String title;

    @Column(name = "REMARKS")
    private String remarks;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;
}