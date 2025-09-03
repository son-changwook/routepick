# Step 6-4d: MessageService 구현

> 쪽지 시스템 서비스 - 발송/수신, 읽음 처리, 루트 태깅, 검색
> 생성일: 2025-08-22
> 단계: 6-4d (Service 레이어 - 쪽지 시스템)
> 참고: step5-4f2

---

## 🎯 설계 목표

- **쪽지 시스템**: Message 엔티티 관리
- **발송/수신**: 사용자 간 메시지 교환
- **읽음 처리**: is_read, read_at 상태 관리
- **루트 태깅**: MessageRouteTag 연동
- **검색 기능**: 제목, 내용 기반 검색
- **삭제/보관**: 쪽지함 관리

---

## 💌 MessageService 구현

### MessageService.java
```java
package com.routepick.service.community;

import com.routepick.common.enums.MessageStatus;
import com.routepick.common.enums.MessageType;
import com.routepick.domain.community.entity.Message;
import com.routepick.domain.community.entity.MessageRouteTag;
import com.routepick.domain.community.repository.MessageRepository;
import com.routepick.domain.community.repository.MessageRouteTagRepository;
import com.routepick.domain.route.entity.Route;
import com.routepick.domain.route.repository.RouteRepository;
import com.routepick.domain.tag.entity.Tag;
import com.routepick.domain.tag.repository.TagRepository;
import com.routepick.domain.user.entity.User;
import com.routepick.domain.user.repository.UserRepository;
import com.routepick.exception.community.CommunityException;
import com.routepick.exception.user.UserException;
import com.routepick.util.XssProtectionUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 쪽지 시스템 관리 서비스
 * - 쪽지 발송/수신 관리
 * - 읽음 상태 처리
 * - 메시지 루트 태깅
 * - 쪽지 검색 기능
 * - 쪽지 삭제 및 보관
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {
    
    private final MessageRepository messageRepository;
    private final MessageRouteTagRepository messageRouteTagRepository;
    private final UserRepository userRepository;
    private final RouteRepository routeRepository;
    private final TagRepository tagRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    
    // 캐시 이름
    private static final String CACHE_MESSAGE = "message";
    private static final String CACHE_USER_MESSAGES = "userMessages";
    private static final String CACHE_UNREAD_COUNT = "unreadCount";
    
    // 설정값
    private static final int MAX_SUBJECT_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_ROUTE_TAGS_PER_MESSAGE = 5;
    private static final int MAX_RECIPIENTS = 10; // 단체 메시지 최대 수신자
    
    /**
     * 쪽지 발송
     * @param senderId 발송자 ID
     * @param recipientIds 수신자 ID 목록
     * @param subject 제목
     * @param content 내용
     * @param messageType 메시지 타입
     * @param routeIds 연관 루트 ID 목록 (선택사항)
     * @param tagIds 연관 태그 ID 목록 (선택사항)
     * @return 발송된 메시지 목록
     */
    @Transactional
    @CacheEvict(value = {CACHE_USER_MESSAGES, CACHE_UNREAD_COUNT}, allEntries = true)
    public List<Message> sendMessage(Long senderId, List<Long> recipientIds,
                                   String subject, String content, MessageType messageType,
                                   List<Long> routeIds, List<Long> tagIds) {
        log.info("Sending message: senderId={}, recipients={}, subject={}", 
                senderId, recipientIds.size(), subject);
        
        // 발송자 확인
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new UserException("발송자를 찾을 수 없습니다: " + senderId));
            
        // 입력값 검증
        validateMessageInput(subject, content, recipientIds);
        
        // XSS 방지
        String cleanSubject = XssProtectionUtil.cleanXss(subject);
        String cleanContent = XssProtectionUtil.cleanXss(content);
        
        List<Message> sentMessages = new ArrayList<>();
        
        // 각 수신자별로 메시지 생성
        for (Long recipientId : recipientIds) {
            User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new UserException("수신자를 찾을 수 없습니다: " + recipientId));
                
            // 자기 자신에게 발송 방지
            if (senderId.equals(recipientId)) {
                log.warn("Cannot send message to self: userId={}", senderId);
                continue;
            }
            
            Message message = Message.builder()
                .sender(sender)
                .recipient(recipient)
                .subject(cleanSubject)
                .content(cleanContent)
                .messageType(messageType != null ? messageType : MessageType.PERSONAL)
                .status(MessageStatus.SENT)
                .isRead(false)
                .isDeletedBySender(false)
                .isDeletedByRecipient(false)
                .build();
                
            Message savedMessage = messageRepository.save(message);
            sentMessages.add(savedMessage);
            
            // 루트 태깅 처리
            if (routeIds != null && !routeIds.isEmpty()) {
                processMessageRouteTags(savedMessage, routeIds, tagIds);
            }
            
            // 수신자에게 알림
            notificationService.sendMessageNotification(recipientId, savedMessage);
        }
        
        // 이벤트 발행
        eventPublisher.publishEvent(new MessageSentEvent(senderId, recipientIds, sentMessages));
        
        log.info("Messages sent successfully: count={}", sentMessages.size());
        return sentMessages;
    }
    
    /**
     * 단일 쪽지 발송 (편의 메서드)
     * @param senderId 발송자 ID
     * @param recipientId 수신자 ID
     * @param subject 제목
     * @param content 내용
     * @return 발송된 메시지
     */
    @Transactional
    public Message sendSingleMessage(Long senderId, Long recipientId, 
                                   String subject, String content) {
        List<Message> messages = sendMessage(
            senderId, 
            Collections.singletonList(recipientId),
            subject, 
            content, 
            MessageType.PERSONAL,
            null, 
            null
        );
        
        return messages.isEmpty() ? null : messages.get(0);
    }
    
    /**
     * 루트 관련 쪽지 발송
     * @param senderId 발송자 ID
     * @param recipientId 수신자 ID
     * @param subject 제목
     * @param content 내용
     * @param routeId 루트 ID
     * @param tagIds 태그 ID 목록
     * @return 발송된 메시지
     */
    @Transactional
    public Message sendRouteMessage(Long senderId, Long recipientId,
                                  String subject, String content,
                                  Long routeId, List<Long> tagIds) {
        List<Message> messages = sendMessage(
            senderId,
            Collections.singletonList(recipientId),
            subject,
            content,
            MessageType.ROUTE_RELATED,
            Collections.singletonList(routeId),
            tagIds
        );
        
        return messages.isEmpty() ? null : messages.get(0);
    }
    
    /**
     * 쪽지 조회 (읽음 처리)
     * @param messageId 메시지 ID
     * @param userId 조회자 ID
     * @return 메시지
     */
    @Transactional
    @Cacheable(value = CACHE_MESSAGE, key = "#messageId")
    public Message getMessage(Long messageId, Long userId) {
        log.debug("Getting message: messageId={}, userId={}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new CommunityException("메시지를 찾을 수 없습니다: " + messageId));
            
        // 권한 확인 (발송자 또는 수신자만 조회 가능)
        if (!message.getSender().getUserId().equals(userId) &&
            !message.getRecipient().getUserId().equals(userId)) {
            throw new CommunityException("메시지 조회 권한이 없습니다");
        }
        
        // 수신자가 읽으면 읽음 처리
        if (message.getRecipient().getUserId().equals(userId) && !message.isRead()) {
            markAsRead(messageId, userId);
        }
        
        return message;
    }
    
    /**
     * 쪽지 읽음 처리
     * @param messageId 메시지 ID
     * @param userId 사용자 ID
     */
    @Transactional
    @CacheEvict(value = {CACHE_MESSAGE, CACHE_UNREAD_COUNT}, allEntries = true)
    public void markAsRead(Long messageId, Long userId) {
        log.info("Marking message as read: messageId={}, userId={}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new CommunityException("메시지를 찾을 수 없습니다: " + messageId));
            
        // 수신자만 읽음 처리 가능
        if (!message.getRecipient().getUserId().equals(userId)) {
            throw new CommunityException("읽음 처리 권한이 없습니다");
        }
        
        if (!message.isRead()) {
            message.setIsRead(true);
            message.setReadAt(LocalDateTime.now());
            messageRepository.save(message);
            
            // 이벤트 발행
            eventPublisher.publishEvent(new MessageReadEvent(messageId, userId));
        }
    }
    
    /**
     * 여러 쪽지 일괄 읽음 처리
     * @param messageIds 메시지 ID 목록
     * @param userId 사용자 ID
     * @return 읽음 처리된 메시지 수
     */
    @Transactional
    @CacheEvict(value = {CACHE_MESSAGE, CACHE_UNREAD_COUNT}, allEntries = true)
    public int markMultipleAsRead(List<Long> messageIds, Long userId) {
        log.info("Marking multiple messages as read: count={}, userId={}", 
                messageIds.size(), userId);
        
        int updatedCount = messageRepository.markAsReadByIds(messageIds, userId);
        
        if (updatedCount > 0) {
            eventPublisher.publishEvent(new MultipleMessagesReadEvent(messageIds, userId));
        }
        
        return updatedCount;
    }
    
    /**
     * 수신함 조회
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 수신 메시지 페이지
     */
    @Cacheable(value = CACHE_USER_MESSAGES, 
              key = "'inbox_' + #userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Message> getInboxMessages(Long userId, Pageable pageable) {
        log.debug("Getting inbox messages: userId={}", userId);
        
        return messageRepository.findReceivedMessages(userId, pageable);
    }
    
    /**
     * 발신함 조회
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 발신 메시지 페이지
     */
    @Cacheable(value = CACHE_USER_MESSAGES,
              key = "'sent_' + #userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<Message> getSentMessages(Long userId, Pageable pageable) {
        log.debug("Getting sent messages: userId={}", userId);
        
        return messageRepository.findSentMessages(userId, pageable);
    }
    
    /**
     * 읽지 않은 메시지 수 조회
     * @param userId 사용자 ID
     * @return 읽지 않은 메시지 수
     */
    @Cacheable(value = CACHE_UNREAD_COUNT, key = "#userId")
    public Long getUnreadMessageCount(Long userId) {
        log.debug("Getting unread message count: userId={}", userId);
        
        return messageRepository.countUnreadMessages(userId);
    }
    
    /**
     * 메시지 검색
     * @param userId 사용자 ID
     * @param keyword 검색어
     * @param messageType 메시지 타입 (선택사항)
     * @param isInbox 수신함 여부 (true: 수신함, false: 발신함)
     * @param pageable 페이징
     * @return 검색 결과
     */
    public Page<Message> searchMessages(Long userId, String keyword, 
                                      MessageType messageType, boolean isInbox,
                                      Pageable pageable) {
        log.debug("Searching messages: userId={}, keyword={}, type={}, inbox={}", 
                 userId, keyword, messageType, isInbox);
        
        if (!StringUtils.hasText(keyword)) {
            throw new CommunityException("검색어를 입력해주세요");
        }
        
        String cleanKeyword = XssProtectionUtil.cleanXss(keyword);
        
        if (isInbox) {
            return messageRepository.searchReceivedMessages(
                userId, cleanKeyword, messageType, pageable
            );
        } else {
            return messageRepository.searchSentMessages(
                userId, cleanKeyword, messageType, pageable
            );
        }
    }
    
    /**
     * 쪽지 삭제 (발송자 입장)
     * @param messageId 메시지 ID
     * @param userId 사용자 ID
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_MESSAGES, allEntries = true)
    public void deleteMessageBySender(Long messageId, Long userId) {
        log.info("Deleting message by sender: messageId={}, userId={}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new CommunityException("메시지를 찾을 수 없습니다: " + messageId));
            
        if (!message.getSender().getUserId().equals(userId)) {
            throw new CommunityException("발송자만 삭제할 수 있습니다");
        }
        
        message.setIsDeletedBySender(true);
        messageRepository.save(message);
        
        // 양쪽 모두 삭제했으면 완전 삭제
        if (message.isDeletedByRecipient()) {
            messageRepository.delete(message);
        }
    }
    
    /**
     * 쪽지 삭제 (수신자 입장)
     * @param messageId 메시지 ID
     * @param userId 사용자 ID
     */
    @Transactional
    @CacheEvict(value = CACHE_USER_MESSAGES, allEntries = true)
    public void deleteMessageByRecipient(Long messageId, Long userId) {
        log.info("Deleting message by recipient: messageId={}, userId={}", messageId, userId);
        
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new CommunityException("메시지를 찾을 수 없습니다: " + messageId));
            
        if (!message.getRecipient().getUserId().equals(userId)) {
            throw new CommunityException("수신자만 삭제할 수 있습니다");
        }
        
        message.setIsDeletedByRecipient(true);
        messageRepository.save(message);
        
        // 양쪽 모두 삭제했으면 완전 삭제
        if (message.isDeletedBySender()) {
            messageRepository.delete(message);
        }
    }
    
    /**
     * 대화 스레드 조회
     * @param userId 사용자 ID
     * @param otherUserId 상대방 사용자 ID
     * @param pageable 페이징
     * @return 대화 메시지 페이지
     */
    public Page<Message> getConversation(Long userId, Long otherUserId, Pageable pageable) {
        log.debug("Getting conversation: userId={}, otherUserId={}", userId, otherUserId);
        
        return messageRepository.findConversation(userId, otherUserId, pageable);
    }
    
    /**
     * 루트 관련 메시지 조회
     * @param routeId 루트 ID
     * @param userId 사용자 ID
     * @param pageable 페이징
     * @return 루트 관련 메시지 페이지
     */
    public Page<Message> getRouteRelatedMessages(Long routeId, Long userId, Pageable pageable) {
        log.debug("Getting route related messages: routeId={}, userId={}", routeId, userId);
        
        return messageRepository.findRouteRelatedMessages(routeId, userId, pageable);
    }
    
    /**
     * 메시지 통계 조회
     * @param userId 사용자 ID
     * @return 메시지 통계
     */
    public MessageStats getMessageStats(Long userId) {
        log.debug("Getting message stats: userId={}", userId);
        
        Long totalReceived = messageRepository.countReceivedMessages(userId);
        Long totalSent = messageRepository.countSentMessages(userId);
        Long unreadCount = messageRepository.countUnreadMessages(userId);
        
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        Long recentReceived = messageRepository.countReceivedMessagesSince(userId, since);
        Long recentSent = messageRepository.countSentMessagesSince(userId, since);
        
        return MessageStats.builder()
            .userId(userId)
            .totalReceived(totalReceived)
            .totalSent(totalSent)
            .unreadCount(unreadCount)
            .recentReceived(recentReceived)
            .recentSent(recentSent)
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    /**
     * 메시지 루트 태깅 처리
     * @param message 메시지
     * @param routeIds 루트 ID 목록
     * @param tagIds 태그 ID 목록
     */
    private void processMessageRouteTags(Message message, List<Long> routeIds, List<Long> tagIds) {
        if (routeIds == null || routeIds.isEmpty()) {
            return;
        }
        
        if (routeIds.size() > MAX_ROUTE_TAGS_PER_MESSAGE) {
            throw new CommunityException("메시지당 최대 " + MAX_ROUTE_TAGS_PER_MESSAGE + "개의 루트만 태깅 가능합니다");
        }
        
        for (Long routeId : routeIds) {
            Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new CommunityException("루트를 찾을 수 없습니다: " + routeId));
                
            MessageRouteTag routeTag = MessageRouteTag.builder()
                .message(message)
                .route(route)
                .build();
                
            messageRouteTagRepository.save(routeTag);
            
            // 태그 처리
            if (tagIds != null && !tagIds.isEmpty()) {
                for (Long tagId : tagIds) {
                    Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new CommunityException("태그를 찾을 수 없습니다: " + tagId));
                        
                    routeTag.getTags().add(tag);
                }
                
                messageRouteTagRepository.save(routeTag);
            }
        }
    }
    
    /**
     * 메시지 입력값 검증
     * @param subject 제목
     * @param content 내용
     * @param recipientIds 수신자 목록
     */
    private void validateMessageInput(String subject, String content, List<Long> recipientIds) {
        // 제목 검증
        if (!StringUtils.hasText(subject)) {
            throw new CommunityException("제목을 입력해주세요");
        }
        
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new CommunityException("제목은 " + MAX_SUBJECT_LENGTH + "자를 초과할 수 없습니다");
        }
        
        // 내용 검증
        if (!StringUtils.hasText(content)) {
            throw new CommunityException("내용을 입력해주세요");
        }
        
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new CommunityException("내용은 " + MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다");
        }
        
        // 수신자 검증
        if (recipientIds == null || recipientIds.isEmpty()) {
            throw new CommunityException("수신자를 선택해주세요");
        }
        
        if (recipientIds.size() > MAX_RECIPIENTS) {
            throw new CommunityException("최대 " + MAX_RECIPIENTS + "명까지 발송 가능합니다");
        }
    }
    
    // 이벤트 클래스들
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MessageSentEvent {
        private final Long senderId;
        private final List<Long> recipientIds;
        private final List<Message> messages;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MessageReadEvent {
        private final Long messageId;
        private final Long userId;
    }
    
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MultipleMessagesReadEvent {
        private final List<Long> messageIds;
        private final Long userId;
    }
    
    // DTO 클래스
    @lombok.Builder
    @lombok.Getter
    public static class MessageStats {
        private final Long userId;
        private final Long totalReceived;
        private final Long totalSent;
        private final Long unreadCount;
        private final Long recentReceived;
        private final Long recentSent;
        private final LocalDateTime lastUpdated;
    }
}
```

---

## 🔧 설정 및 통합

### application.yml 추가 설정
```yaml
# 메시지 시스템 설정
app:
  community:
    message:
      cache-ttl: 30m  # 메시지 캐시 TTL
      max-subject-length: 100
      max-content-length: 2000
      max-recipients: 10  # 단체 메시지 최대 수신자
      max-route-tags: 5  # 메시지당 최대 루트 태그
```

---

## 📊 주요 기능 요약

### 1. 쪽지 시스템
- **발송/수신**: 1:1, 1:N 메시지 지원
- **읽음 처리**: 자동 읽음 상태 업데이트
- **대화 스레드**: 사용자간 대화 히스토리
- **메시지 타입**: 개인, 루트 관련 구분

### 2. 루트 태깅
- **루트 연동**: 메시지에 루트 정보 첨부
- **태그 시스템**: 루트별 태그 정보 포함
- **검색 지원**: 루트별 메시지 조회
- **제한 관리**: 메시지당 최대 5개 루트

### 3. 쪽지함 관리
- **수신함/발신함**: 분리된 메시지 관리
- **검색 기능**: 제목, 내용 기반 검색
- **삭제 처리**: 소프트 삭제 후 완전 삭제
- **통계 제공**: 메시지 사용 현황

### 4. 성능 최적화
- **캐싱**: 자주 조회되는 데이터
- **배치 처리**: 다중 읽음 처리
- **이벤트 기반**: 비동기 알림 처리
- **XSS 방지**: 안전한 컨텐츠 처리

---

## ✅ 완료 사항
- ✅ 쪽지 시스템 (Message 엔티티)
- ✅ 쪽지 발송/수신 관리
- ✅ 읽음 상태 처리 (is_read, read_at)
- ✅ 메시지 루트 태깅 (MessageRouteTag)
- ✅ 쪽지 검색 기능
- ✅ 쪽지 삭제 및 보관
- ✅ 대화 스레드 관리
- ✅ Redis 캐싱 적용
- ✅ 알림 시스템 연동
- ✅ XSS 방지 처리

---

*MessageService 설계 완료: 쪽지 시스템 관리*