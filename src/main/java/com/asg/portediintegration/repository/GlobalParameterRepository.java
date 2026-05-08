package com.asg.portediintegration.repository;

import com.asg.portediintegration.entity.GlobalParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalParameterRepository extends JpaRepository<GlobalParameter, Long> {
    Optional<GlobalParameter> findByParameterNameIgnoreCase(String parameterName);

    @Query("""
            select gp.parameterValue
            from GlobalParameter gp
            where lower(gp.parameterName) = lower(:parameterName)
            """)
    Optional<String> findParameterValueByParameterNameIgnoreCase(@Param("parameterName") String parameterName);

    Optional<GlobalParameter> findByParameterNameIgnoreCaseAndCategoryAndGroupPoidAndDeleted(String parameterName, String category, Long groupPoid, String deleted);

    Optional<GlobalParameter> findByParameterNameIgnoreCaseAndCategoryAndDeleted(String parameterName, String category, String deleted);

    List<GlobalParameter> findByCategoryAndGroupPoidAndDeleted(String category, Long groupPoid, String deleted);
}
