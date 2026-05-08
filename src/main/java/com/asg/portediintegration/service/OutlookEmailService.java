package com.asg.portediintegration.service;

import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;

import java.io.InputStream;
import java.util.List;

public interface OutlookEmailService {
    List<Message> fetchUnreadMessages();

    List<Message> fetchAllMessages();

    List<FileAttachment> getAttachments(String messageId);

    InputStream downloadAttachment(String messageId, String attachmentId);

    byte[] getAttachmentContent(String messageId, String attachmentId);

    InputStream getAttachmentStream(String messageId, String attachmentId);

    void markMessageAsRead(String messageId);

    List<FileAttachment> filterEdiAttachments(List<FileAttachment> attachments);
}