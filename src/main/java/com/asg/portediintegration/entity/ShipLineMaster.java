package com.asg.portediintegration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "SHIP_LINE_MASTER")
public class ShipLineMaster {

    @Id
    @Column(name = "LINE_POID")
    private Long linePoid;

    @Column(name = "TERMINAL_LINE_CODE")
    private String terminalLineCode;
}



