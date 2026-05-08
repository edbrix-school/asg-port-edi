package com.asg.portediintegration.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "GLOBAL_PARAMETERS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "GLOBAL_PARAMETERS_UK1",
                        columnNames = {
                                "PARAMETER_NAME",
                                "PARAMETER_KEYID_TYPE",
                                "PARAMETER_KEYID",
                                "PARAMETER_VALUE"
                        }
                )
        }
)
@Getter
@Setter
public class GlobalParameter {

    @Id
    @Column(name = "PARAMETER_POID", nullable = false)
    private Long parameterPoid;

    @Column(name = "GROUP_POID", nullable = false)
    private Long groupPoid;

    @Column(name = "PARAMETER_NAME", nullable = false, length = 100)
    private String parameterName;

    @Column(name = "PARAMETER_KEYID_TYPE", nullable = false, length = 100)
    private String parameterKeyIdType;

    @Column(name = "PARAMETER_KEYID", nullable = false, length = 100)
    private String parameterKeyId;

    @Column(name = "PARAMETER_VALUE", length = 4000)
    private String parameterValue;

    @Column(name = "PARAMETER_DETAILS", length = 1000)
    private String parameterDetails;

    @Column(name = "CATEGORY", length = 30)
    private String category;

    @Column(name = "PARAMETER_LINUX_VALUE", length = 500)
    private String parameterLinuxValue;

    @Column(name = "PARAMETER_TYPE", length = 100)
    private String parameterType = "SYSTEM";

    @Column(name = "DELETED", length = 1)
    private String deleted = "N";
}

