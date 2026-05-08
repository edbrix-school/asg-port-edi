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
@Table(name = "SHIP_BL_MANIFEST_CONTAINER_DTL")
public class ShipBlManifestContainerDtl {

    @Id
    @Column(name = "TRANSACTION_POID")
    private Long transactionPoid;

    @Column(name = "CONTAINER_NO")
    private String containerNo;
}



