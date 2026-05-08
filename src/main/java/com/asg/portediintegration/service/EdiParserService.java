package com.asg.portediintegration.service;

import com.asg.portediintegration.entity.EdiContainerGateInOut;

import java.util.List;

public interface EdiParserService {
    EdiContainerGateInOut parseCodecoMessage(String[] ediLines, String ediRefNo, String fileName);

    void processAdditionalSegments(EdiContainerGateInOut gateRecord, List<String> ediLines, Long currentSeqNo, String fileName);
}