package com.asg.portediintegration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "EDI_UPLOAD_CODECO")
public class EdiUploadCodeco {

    @Id
    @Column(name = "EDI_REF_NO")
    private String ediRefNo;

    @Column(name = "SEQNO_FILE_LINE")
    private Long seqnoFileLine;

    @Column(name = "MAIN_GROUP_NO")
    private Long mainGroupNo;

    @Column(name = "SLOT_NUMBER")
    private Long slotNumber;

    @Column(name = "TAB_TEXT")
    private String tabText;

    @Column(name = "FILE_LOAD_NAME")
    private String fileLoadName;

    @Column(name = "EDI_LOAD_DATE")
    private LocalDateTime ediLoadDate;

    @Column(name = "EDI_LOAD_FLAG")
    private String ediLoadFlag;
}