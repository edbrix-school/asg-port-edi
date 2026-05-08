package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.ShipLineMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShipLineMasterRepository extends JpaRepository<ShipLineMaster, Long> {

    @Query(value = "SELECT DISTINCT LINE_POID FROM SHIP_LINE_MASTER WHERE TERMINAL_LINE_CODE = :terminalLineCode",
            nativeQuery = true)
    List<Long> findLinePoidByTerminalLineCode(@Param("terminalLineCode") String terminalLineCode);
}

