package com.asg.portediintegration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "SHIP_CONTAINER_INVENTORY")
public class ShipContainerInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ship_container_inventory_seq")
    @SequenceGenerator(name = "ship_container_inventory_seq", sequenceName = "SHIP_CONTAINER_INVENTORY_SEQ", allocationSize = 1)
    @Column(name = "TRANSACTION_POID")
    private Long transactionPoid;

    @Column(name = "TRANSACTION_DATE")
    private LocalDateTime transactionDate;

    @Column(name = "LINK_TRANSACTION_POID")
    private Long linkTransactionPoid;

    @Column(name = "CONTAINER_NO")
    private String containerNo;

    @Column(name = "MOVES_TYPE")
    private String movesType;

    @Column(name = "MOVES_DATE_TIME")
    private LocalDateTime movesDateTime;

    @Column(name = "MOVES_VALUE")
    private Integer movesValue;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_DATE")
    private LocalDateTime createdDate;

    @Column(name = "LASTMODIFIED_BY")
    private String lastModifiedBy;

    @Column(name = "LASTMODIFIED_DATE")
    private LocalDateTime lastModifiedDate;

    @Column(name = "GROUP_POID")
    private Long groupPoid;

    @Column(name = "COMPANY_POID")
    private Long companyPoid;

    @Column(name = "LINE_POID")
    private Long linePoid;

    @Column(name = "BOOKING_TRANSACTION_POID")
    private Long bookingTransactionPoid;

    @Column(name = "EQUIPMENT_ISO_TYPE")
    private String equipmentIsoType;
}