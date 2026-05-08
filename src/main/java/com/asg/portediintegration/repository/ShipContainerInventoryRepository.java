package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.ShipContainerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipContainerInventoryRepository extends JpaRepository<ShipContainerInventory, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM SHIP_CONTAINER_INVENTORY " +
            "WHERE (LINK_TRANSACTION_POID, CONTAINER_NO) IN " +
            "(SELECT TRANSACTION_POID, CONTAINER_NO FROM SHIP_BL_MANIFEST_CONTAINER_DTL " +
            "WHERE EQUIPMENT_SHIPPER_OWN IN ('Y','L'))", nativeQuery = true)
    void deleteShipperOwnContainers();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM SHIP_CONTAINER_INVENTORY " +
            "WHERE (LINK_TRANSACTION_POID, CONTAINER_NO) IN " +
            "(SELECT TRANSACTION_POID, CONTAINER_NO FROM SHIP_BL_MANIFEST_CONTAINER_DTL " +
            "WHERE TRANSACTION_POID IN " +
            "(SELECT TRANSACTION_POID FROM SHIP_BL_MANIFEST_HDR " +
            "WHERE BL_TYPE NOT IN('IMPORT','EXPORT')))", nativeQuery = true)
    void deleteCrossTradeContainers();
}

