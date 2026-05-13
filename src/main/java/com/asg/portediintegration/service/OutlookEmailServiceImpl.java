package com.asg.portediintegration.service;

import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutlookEmailServiceImpl implements OutlookEmailService {

    private final GraphServiceClient<Request> graphClient;
    private final GlobalParameterService globalParameterService;

    private static final String OUTLOOK_USER_EMAIL = "PORT_EDI_OUTLOOK_USER_EMAIL";

    private String getUserEmail() {
        String email = globalParameterService.getValue(OUTLOOK_USER_EMAIL);

        if (StringUtils.isBlank(email)) {
            throw new IllegalStateException("OUTLOOK_USER_EMAIL is not configured in GLOBAL_PARAMETERS");
        }
        return email;
    }

    /**
     * Fetches unread messages from the inbox
     */
    public List<Message> fetchUnreadMessages() {
        try {
            log.info("Fetching unread messages from inbox for user: {}", getUserEmail());

            MessageCollectionPage messages = graphClient
                    .users(getUserEmail())
                    .mailFolders("inbox")
                    .messages()
                    .buildRequest()
                    .filter("isRead eq false")
                    .top(50)
                    .get();

            List<Message> messageList = new ArrayList<>();
            if (messages != null) {
                messages.getCurrentPage();
                messageList.addAll(messages.getCurrentPage());
            }

            log.info("Found {} unread messages", messageList.size());
            return messageList;
        } catch (Exception e) {
            log.error("Error fetching unread messages", e);
            throw new RuntimeException("Failed to fetch unread messages", e);
        }
    }

    /**
     * Fetches all messages from the inbox (read and unread)
     */
    public List<Message> fetchAllMessages() {
        try {
            log.info("Fetching all messages from inbox for user: {}", getUserEmail());

            MessageCollectionPage messages = graphClient
                    .users(getUserEmail())
                    .mailFolders("inbox")
                    .messages()
                    .buildRequest()
                    .top(50)
                    .get();

            List<Message> messageList = new ArrayList<>();
            if (messages != null && messages.getCurrentPage() != null) {
                messageList.addAll(messages.getCurrentPage());
            }

            log.info("Found {} messages", messageList.size());
            return messageList;
        } catch (Exception e) {
            log.error("Error fetching messages", e);
            throw new RuntimeException("Failed to fetch messages", e);
        }
    }

    /**
     * Gets attachments from a message
     */
    public List<FileAttachment> getAttachments(String messageId) {
        try {
            log.debug("Fetching attachments for message: {}", messageId);

            var attachments = graphClient
                    .users(getUserEmail())
                    .messages(messageId)
                    .attachments()
                    .buildRequest()
                    .get();

            List<FileAttachment> fileAttachments = new ArrayList<>();
            if (attachments != null && attachments.getCurrentPage() != null) {
                for (Attachment attachment : attachments.getCurrentPage()) {
                    if (attachment instanceof FileAttachment) {
                        fileAttachments.add((FileAttachment) attachment);
                    }
                }
            }

            log.debug("Found {} attachments for message: {}", fileAttachments.size(), messageId);
            return fileAttachments;
        } catch (Exception e) {
            log.error("Error fetching attachments for message: {}", messageId, e);
            throw new RuntimeException("Failed to fetch attachments", e);
        }
    }

    /**
     * Downloads attachment content as InputStream
     */
    public InputStream downloadAttachment(String messageId, String attachmentId) {
        try {
            log.debug("Downloading attachment: {} from message: {}", attachmentId, messageId);

            FileAttachment attachment = (FileAttachment) graphClient
                    .users(getUserEmail())
                    .messages(messageId)
                    .attachments(attachmentId)
                    .buildRequest()
                    .get();

            if (attachment != null && attachment.contentBytes != null) {
                return new ByteArrayInputStream(attachment.contentBytes);
            } else {
                throw new RuntimeException("Attachment content is null");
            }
        } catch (Exception e) {
            log.error("Error downloading attachment: {} from message: {}", attachmentId, messageId, e);
            throw new RuntimeException("Failed to download attachment", e);
        }
    }

    /**
     * Gets attachment content as byte array
     */
    public byte[] getAttachmentContent(String messageId, String attachmentId) {
        try {
            log.debug("Getting attachment content: {} from message: {}", attachmentId, messageId);

            FileAttachment attachment = (FileAttachment) graphClient
                    .users(getUserEmail())
                    .messages(messageId)
                    .attachments(attachmentId)
                    .buildRequest()
                    .get();

            if (attachment != null && attachment.contentBytes != null) {
                return attachment.contentBytes;
            } else {
                throw new RuntimeException("Attachment content is null");
            }
        } catch (Exception e) {
            log.error("Error getting attachment content: {} from message: {}", attachmentId, messageId, e);
            throw new RuntimeException("Failed to get attachment content", e);
        }
    }

    /**
     * Gets attachment content as stream
     */
    public InputStream getAttachmentStream(String messageId, String attachmentId) {
        try {
            log.debug("Streaming attachment content: {} from message: {}", attachmentId, messageId);

            String requestUrl = String.format("/users/%s/messages/%s/attachments/%s/$value", getUserEmail(), messageId, attachmentId);

            // This returns an InputStream directly
            return graphClient.customRequest(requestUrl, InputStream.class).buildRequest().get();

        } catch (Exception e) {
            log.error("Error streaming attachment content: {} from message: {}", attachmentId, messageId, e);
            throw new RuntimeException("Failed to stream attachment content", e);
        }
    }

    /**
     * Marks a message as read
     */
    public void markMessageAsRead(String messageId) {
        try {
            log.debug("Marking message as read: {}", messageId);

            Message message = new Message();
            message.isRead = true;

            graphClient.users(getUserEmail()).messages(messageId).buildRequest().patch(message);

            log.debug("Message marked as read: {}", messageId);
        } catch (Exception e) {
            log.error("Error marking message as read: {}", messageId, e);
            throw new RuntimeException("Failed to mark message as read", e);
        }
    }

    /**
     * Filters EDI file attachments (files with .edi extension)
     */
    public List<FileAttachment> filterEdiAttachments(List<FileAttachment> attachments) {
        return attachments.stream()
                .filter(att -> att.name != null && (att.name.toLowerCase().endsWith(".edi") || att.name.toLowerCase().endsWith(".edifact") || att.name.toLowerCase().endsWith(".x12")))
                .collect(Collectors.toList());
    }
}

