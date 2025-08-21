# Step 5-4f2: 메시지 시스템 Repository 생성

## 개요
- **목적**: 메시지 시스템 Repository 생성
- **대상**: MessageRepository, MessageRouteTagRepository
- **최적화**: 실시간 메시지, 대화 스레드, 루트 태깅

## 1. MessageRepository (메시지 시스템 최적화)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.message;

import com.routepick.backend.domain.entity.message.Message;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지 Repository
 * - 실시간 메시지 시스템 지원
 * - 읽음 상태 관리 및 알림 연동
 * - 대화 스레드 및 검색 최적화
 */
@Repository
public interface MessageRepository extends BaseRepository<Message, Long> {
    
    // ===== 수신자별 메시지 조회 =====
    
    Page<Message> findByReceiverUserIdOrderByCreatedAtDesc(Long receiverUserId, Pageable pageable);
    
    List<Message> findByReceiverUserIdAndIsReadFalseOrderByCreatedAtDesc(Long receiverUserId);
    
    @Query("SELECT m FROM Message m " +
           "WHERE m.receiverUserId = :receiverUserId " +
           "AND m.createdAt >= :since " +
           "ORDER BY m.createdAt DESC")
    List<Message> findRecentMessagesByReceiver(@Param("receiverUserId") Long receiverUserId, 
                                              @Param("since") LocalDateTime since);
    
    // ===== 발신자별 메시지 조회 =====
    
    Page<Message> findBySenderUserIdOrderByCreatedAtDesc(Long senderUserId, Pageable pageable);
    
    @Query("SELECT m FROM Message m " +
           "WHERE m.senderUserId = :senderUserId " +
           "AND m.createdAt >= :since " +
           "ORDER BY m.createdAt DESC")
    List<Message> findRecentMessagesBySender(@Param("senderUserId") Long senderUserId, 
                                           @Param("since") LocalDateTime since);
    
    // ===== 대화 조회 (두 사용자 간) =====
    
    @Query("SELECT m FROM Message m " +
           "WHERE (m.senderUserId = :userId1 AND m.receiverUserId = :userId2) " +
           "OR (m.senderUserId = :userId2 AND m.receiverUserId = :userId1) " +
           "ORDER BY m.createdAt ASC")
    Page<Message> findConversationBetweenUsers(@Param("userId1") Long userId1, 
                                              @Param("userId2") Long userId2, 
                                              Pageable pageable);
    
    @Query("SELECT m FROM Message m " +
           "WHERE (m.senderUserId = :userId1 AND m.receiverUserId = :userId2) " +
           "OR (m.senderUserId = :userId2 AND m.receiverUserId = :userId1) " +
           "AND m.createdAt >= :since " +
           "ORDER BY m.createdAt DESC")
    List<Message> findRecentConversation(@Param("userId1") Long userId1, 
                                        @Param("userId2") Long userId2, 
                                        @Param("since") LocalDateTime since);
    
    // ===== 미읽은 메시지 카운트 =====
    
    long countByReceiverUserIdAndIsReadFalse(Long receiverUserId);
    
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.receiverUserId = :receiverUserId " +
           "AND m.senderUserId = :senderUserId " +
           "AND m.isRead = false")
    long countUnreadMessagesBetweenUsers(@Param("receiverUserId") Long receiverUserId, 
                                        @Param("senderUserId") Long senderUserId);
    
    // ===== 메시지 읽음 처리 =====
    
    @Transactional
    @Modifying
    @Query("UPDATE Message m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP " +
           "WHERE m.id = :messageId AND m.receiverUserId = :receiverUserId")
    int markAsRead(@Param("messageId") Long messageId, @Param("receiverUserId") Long receiverUserId);
    
    @Transactional
    @Modifying
    @Query("UPDATE Message m SET m.isRead = true, m.readAt = CURRENT_TIMESTAMP " +
           "WHERE m.receiverUserId = :receiverUserId " +
           "AND m.senderUserId = :senderUserId " +
           "AND m.isRead = false")
    int markConversationAsRead(@Param("receiverUserId") Long receiverUserId, 
                              @Param("senderUserId") Long senderUserId);
    
    // ===== 최근 대화 목록 =====
    
    @Query("SELECT DISTINCT " +
           "CASE WHEN m.senderUserId = :userId THEN m.receiverUserId ELSE m.senderUserId END as otherUserId, " +
           "MAX(m.createdAt) as lastMessageTime " +
           "FROM Message m " +
           "WHERE m.senderUserId = :userId OR m.receiverUserId = :userId " +
           "GROUP BY otherUserId " +
           "ORDER BY lastMessageTime DESC")
    List<Object[]> findRecentConversations(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT m FROM Message m " +
           "WHERE m.id IN (" +
           "  SELECT MAX(m2.id) FROM Message m2 " +
           "  WHERE (m2.senderUserId = :userId OR m2.receiverUserId = :userId) " +
           "  GROUP BY CASE WHEN m2.senderUserId = :userId THEN m2.receiverUserId ELSE m2.senderUserId END" +
           ") " +
           "ORDER BY m.createdAt DESC")
    List<Message> findLastMessagesPerConversation(@Param("userId") Long userId);
    
    // ===== 메시지 검색 =====
    
    @Query("SELECT m FROM Message m " +
           "WHERE (m.senderUserId = :userId OR m.receiverUserId = :userId) " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.createdAt DESC")
    Page<Message> searchMessages(@Param("userId") Long userId, 
                                 @Param("keyword") String keyword, 
                                 Pageable pageable);
    
    // ===== 메시지 통계 =====
    
    @Query("SELECT DATE(m.createdAt), COUNT(m) " +
           "FROM Message m " +
           "WHERE m.senderUserId = :userId " +
           "AND m.createdAt >= :startDate " +
           "GROUP BY DATE(m.createdAt) " +
           "ORDER BY DATE(m.createdAt) DESC")
    List<Object[]> getUserMessageActivity(@Param("userId") Long userId, 
                                         @Param("startDate") LocalDateTime startDate);
    
    // ===== 메시지 정리 =====
    
    @Transactional
    @Modifying
    @Query("DELETE FROM Message m " +
           "WHERE m.isRead = true AND m.createdAt < :cutoffDate")
    int deleteOldReadMessages(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### Custom Repository Interface
```java
package com.routepick.backend.infrastructure.persistence.repository.message.custom;

import com.routepick.backend.application.dto.message.MessageSearchCriteria;
import com.routepick.backend.application.dto.projection.ConversationSummaryProjection;
import com.routepick.backend.application.dto.projection.MessageAnalyticsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지 커스텀 Repository
 */
public interface MessageRepositoryCustom {
    
    // 고급 검색
    Page<Message> searchMessages(MessageSearchCriteria criteria, Pageable pageable);
    
    // 대화 요약 정보
    List<ConversationSummaryProjection> getConversationSummaries(Long userId, int limit);
    
    // 메시지 분석
    List<MessageAnalyticsProjection> getMessageAnalytics(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // 활성 대화 찾기
    List<Long> findActiveConversations(Long userId, int hours, int limit);
    
    // 읽지 않은 메시지가 있는 대화
    List<Long> findConversationsWithUnreadMessages(Long userId);
    
    // 메시지 전송 패턴 분석
    List<MessageAnalyticsProjection> analyzeMessagePatterns(Long userId, int days);
    
    // 배치 처리
    void batchMarkMessagesAsRead(List<Long> messageIds);
    
    void batchDeleteOldMessages(LocalDateTime cutoffDate, int batchSize);
    
    // 실시간 메시지 지원
    List<Message> findRealtimeUpdates(Long userId, LocalDateTime lastCheck);
}
```

### Custom Repository 구현
```java
package com.routepick.backend.infrastructure.persistence.repository.message.custom;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.routepick.backend.domain.entity.message.Message;
import com.routepick.backend.domain.entity.message.QMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지 커스텀 Repository 구현
 */
@Repository
@RequiredArgsConstructor
public class MessageRepositoryCustomImpl implements MessageRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;
    
    private static final QMessage message = QMessage.message;
    
    @Override
    public Page<Message> searchMessages(MessageSearchCriteria criteria, Pageable pageable) {
        BooleanBuilder builder = new BooleanBuilder();
        
        if (criteria.getUserId() != null) {
            builder.and(message.senderUserId.eq(criteria.getUserId())
                      .or(message.receiverUserId.eq(criteria.getUserId())));
        }
        
        if (criteria.getOtherUserId() != null) {
            builder.and((message.senderUserId.eq(criteria.getUserId()).and(message.receiverUserId.eq(criteria.getOtherUserId())))
                      .or(message.senderUserId.eq(criteria.getOtherUserId()).and(message.receiverUserId.eq(criteria.getUserId()))));
        }
        
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            builder.and(message.content.containsIgnoreCase(criteria.getKeyword()));
        }
        
        if (criteria.getIsRead() != null) {
            builder.and(message.isRead.eq(criteria.getIsRead()));
        }
        
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            builder.and(message.createdAt.between(criteria.getStartDate(), criteria.getEndDate()));
        }
        
        if (criteria.getMessageType() != null) {
            builder.and(message.messageType.eq(criteria.getMessageType()));
        }
        
        List<Message> content = queryFactory
            .selectFrom(message)
            .where(builder)
            .orderBy(message.createdAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        
        Long total = queryFactory
            .select(message.count())
            .from(message)
            .where(builder)
            .fetchOne();
        
        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }
    
    @Override
    public List<ConversationSummaryProjection> getConversationSummaries(Long userId, int limit) {
        return queryFactory
            .select(Projections.constructor(ConversationSummaryProjection.class,
                message.senderUserId.when(userId).then(message.receiverUserId).otherwise(message.senderUserId),
                message.content.max(),
                message.createdAt.max(),
                message.isRead.countDistinct(),
                message.count()
            ))
            .from(message)
            .where(message.senderUserId.eq(userId).or(message.receiverUserId.eq(userId)))
            .groupBy(message.senderUserId.when(userId).then(message.receiverUserId).otherwise(message.senderUserId))
            .orderBy(message.createdAt.max().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<MessageAnalyticsProjection> getMessageAnalytics(
            LocalDateTime startDate, LocalDateTime endDate) {
        
        return queryFactory
            .select(Projections.constructor(MessageAnalyticsProjection.class,
                message.createdAt.yearMonth(),
                message.count(),
                message.senderUserId.countDistinct(),
                message.receiverUserId.countDistinct(),
                message.isRead.when(true).then(1).otherwise(0).sum()
            ))
            .from(message)
            .where(message.createdAt.between(startDate, endDate))
            .groupBy(message.createdAt.yearMonth())
            .orderBy(message.createdAt.yearMonth().desc())
            .fetch();
    }
    
    @Override
    public List<Long> findActiveConversations(Long userId, int hours, int limit) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        return queryFactory
            .select(message.senderUserId.when(userId).then(message.receiverUserId).otherwise(message.senderUserId))
            .from(message)
            .where(message.senderUserId.eq(userId).or(message.receiverUserId.eq(userId))
                .and(message.createdAt.goe(since)))
            .groupBy(message.senderUserId.when(userId).then(message.receiverUserId).otherwise(message.senderUserId))
            .orderBy(message.createdAt.max().desc())
            .limit(limit)
            .fetch();
    }
    
    @Override
    public List<Long> findConversationsWithUnreadMessages(Long userId) {
        return queryFactory
            .select(message.senderUserId)
            .from(message)
            .where(message.receiverUserId.eq(userId)
                .and(message.isRead.eq(false)))
            .groupBy(message.senderUserId)
            .fetch();
    }
    
    @Override
    public List<MessageAnalyticsProjection> analyzeMessagePatterns(Long userId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        return queryFactory
            .select(Projections.constructor(MessageAnalyticsProjection.class,
                message.createdAt.hour(),
                message.count(),
                message.receiverUserId.countDistinct(),
                message.senderUserId.countDistinct(),
                message.isRead.when(true).then(1).otherwise(0).sum()
            ))
            .from(message)
            .where(message.senderUserId.eq(userId)
                .and(message.createdAt.goe(since)))
            .groupBy(message.createdAt.hour())
            .orderBy(message.count().desc())
            .fetch();
    }
    
    @Override
    public void batchMarkMessagesAsRead(List<Long> messageIds) {
        int batchSize = 100;
        for (int i = 0; i < messageIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, messageIds.size());
            List<Long> batch = messageIds.subList(i, endIndex);
            
            queryFactory
                .update(message)
                .set(message.isRead, true)
                .set(message.readAt, LocalDateTime.now())
                .where(message.id.in(batch))
                .execute();
            
            entityManager.flush();
            entityManager.clear();
        }
    }
    
    @Override
    public void batchDeleteOldMessages(LocalDateTime cutoffDate, int batchSize) {
        int deletedCount;
        do {
            deletedCount = queryFactory
                .delete(message)
                .where(message.isRead.eq(true)
                    .and(message.createdAt.lt(cutoffDate)))
                .limit(batchSize)
                .execute();
            
            entityManager.flush();
            entityManager.clear();
        } while (deletedCount > 0);
    }
    
    @Override
    public List<Message> findRealtimeUpdates(Long userId, LocalDateTime lastCheck) {
        return queryFactory
            .selectFrom(message)
            .where(message.receiverUserId.eq(userId)
                .and(message.createdAt.gt(lastCheck)))
            .orderBy(message.createdAt.asc())
            .fetch();
    }
}
```

## 2. MessageRouteTagRepository (메시지-루트 태깅 연결)

### 기본 Repository
```java
package com.routepick.backend.infrastructure.persistence.repository.message;

import com.routepick.backend.domain.entity.message.MessageRouteTag;
import com.routepick.backend.infrastructure.persistence.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지-루트 태깅 Repository
 * - 메시지에서 언급된 클라이밍 루트 추적
 * - 루트 관련 대화 및 추천 분석
 * - 태깅 맥락 및 관련성 점수 관리
 */
@Repository
public interface MessageRouteTagRepository extends BaseRepository<MessageRouteTag, Long> {
    
    // ===== 메시지별 태깅된 루트 =====
    
    List<MessageRouteTag> findByMessageIdOrderByRelevanceScoreDesc(Long messageId);
    
    @Query("SELECT mrt FROM MessageRouteTag mrt " +
           "WHERE mrt.messageId = :messageId " +
           "AND mrt.relevanceScore >= :minScore " +
           "ORDER BY mrt.relevanceScore DESC")
    List<MessageRouteTag> findHighRelevanceTagsByMessage(@Param("messageId") Long messageId, 
                                                        @Param("minScore") Double minScore);
    
    // ===== 루트별 관련 메시지 =====
    
    List<MessageRouteTag> findByRouteIdOrderByCreatedAtDesc(Long routeId);
    
    @Query("SELECT mrt FROM MessageRouteTag mrt " +
           "WHERE mrt.routeId = :routeId " +
           "AND mrt.createdAt >= :since " +
           "ORDER BY mrt.relevanceScore DESC, mrt.createdAt DESC")
    List<MessageRouteTag> findRecentRouteMessages(@Param("routeId") Long routeId, 
                                                 @Param("since") LocalDateTime since);
    
    // ===== 태그 맥락 검색 =====
    
    @Query("SELECT mrt FROM MessageRouteTag mrt " +
           "WHERE LOWER(mrt.tagContext) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY mrt.relevanceScore DESC, mrt.createdAt DESC")
    List<MessageRouteTag> findByTagContextContaining(@Param("keyword") String keyword, 
                                                    Pageable pageable);
    
    @Query("SELECT mrt FROM MessageRouteTag mrt " +
           "WHERE mrt.tagType = :tagType " +
           "AND mrt.createdAt >= :since " +
           "ORDER BY mrt.relevanceScore DESC")
    List<MessageRouteTag> findByTagTypeAndSince(@Param("tagType") String tagType, 
                                               @Param("since") LocalDateTime since);
    
    // ===== 카운트 조회 =====
    
    long countByRouteId(Long routeId);
    
    long countByMessageId(Long messageId);
    
    @Query("SELECT COUNT(mrt) FROM MessageRouteTag mrt " +
           "WHERE mrt.routeId = :routeId " +
           "AND mrt.createdAt >= :since")
    long countRecentTagsByRoute(@Param("routeId") Long routeId, 
                               @Param("since") LocalDateTime since);
    
    // ===== 인기 메시지 태깅 루트 =====
    
    @Query("SELECT mrt.routeId, COUNT(mrt) as mentionCount, AVG(mrt.relevanceScore) as avgRelevance " +
           "FROM MessageRouteTag mrt " +
           "WHERE mrt.createdAt >= :since " +
           "GROUP BY mrt.routeId " +
           "HAVING mentionCount >= :minMentions " +
           "ORDER BY mentionCount DESC, avgRelevance DESC")
    List<Object[]> findPopularMessageTaggedRoutes(@Param("since") LocalDateTime since, 
                                                 @Param("minMentions") Long minMentions);
    
    @Query("SELECT mrt.routeId, COUNT(DISTINCT mrt.messageId) as messageCount " +
           "FROM MessageRouteTag mrt " +
           "JOIN Message m ON mrt.messageId = m.id " +
           "WHERE m.createdAt >= :since " +
           "GROUP BY mrt.routeId " +
           "ORDER BY messageCount DESC")
    List<Object[]> findMostDiscussedRoutes(@Param("since") LocalDateTime since, 
                                          Pageable pageable);
    
    // ===== 사용자별 루트 언급 분석 =====
    
    @Query("SELECT mrt.routeId, COUNT(mrt) as mentionCount " +
           "FROM MessageRouteTag mrt " +
           "JOIN Message m ON mrt.messageId = m.id " +
           "WHERE m.senderUserId = :userId " +
           "GROUP BY mrt.routeId " +
           "ORDER BY mentionCount DESC")
    List<Object[]> findUserMentionedRoutes(@Param("userId") Long userId, Pageable pageable);
    
    // ===== 관련성 점수 분석 =====
    
    @Query("SELECT AVG(mrt.relevanceScore) FROM MessageRouteTag mrt " +
           "WHERE mrt.routeId = :routeId")
    Double getAverageRelevanceScore(@Param("routeId") Long routeId);
    
    @Query("SELECT mrt FROM MessageRouteTag mrt " +
           "WHERE mrt.relevanceScore >= :minScore " +
           "AND mrt.createdAt >= :since " +
           "ORDER BY mrt.relevanceScore DESC")
    List<MessageRouteTag> findHighRelevanceTags(@Param("minScore") Double minScore, 
                                               @Param("since") LocalDateTime since, 
                                               Pageable pageable);
    
    // ===== 태그 트렌드 분석 =====
    
    @Query("SELECT DATE(mrt.createdAt), mrt.routeId, COUNT(mrt) " +
           "FROM MessageRouteTag mrt " +
           "WHERE mrt.createdAt >= :startDate " +
           "GROUP BY DATE(mrt.createdAt), mrt.routeId " +
           "ORDER BY DATE(mrt.createdAt) DESC, COUNT(mrt) DESC")
    List<Object[]> getRouteTaggingTrends(@Param("startDate") LocalDateTime startDate);
}
```

## Projection 인터페이스들

### ConversationSummaryProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.LocalDateTime;

/**
 * 대화 요약 Projection
 */
public class ConversationSummaryProjection {
    private Long otherUserId;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Long unreadCount;
    private Long totalMessages;
    
    public ConversationSummaryProjection(Long otherUserId, String lastMessageContent, 
                                       LocalDateTime lastMessageTime, Long unreadCount, Long totalMessages) {
        this.otherUserId = otherUserId;
        this.lastMessageContent = lastMessageContent;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount = unreadCount;
        this.totalMessages = totalMessages;
    }
    
    // Getters
    public Long getOtherUserId() { return otherUserId; }
    public String getLastMessageContent() { return lastMessageContent; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public Long getUnreadCount() { return unreadCount; }
    public Long getTotalMessages() { return totalMessages; }
    
    public boolean hasUnreadMessages() { return unreadCount > 0; }
}
```

### MessageAnalyticsProjection
```java
package com.routepick.backend.application.dto.projection;

import java.time.YearMonth;

/**
 * 메시지 분석 Projection
 */
public class MessageAnalyticsProjection {
    private YearMonth period;
    private Long messageCount;
    private Long uniqueSenders;
    private Long uniqueReceivers;
    private Long readMessages;
    
    public MessageAnalyticsProjection(YearMonth period, Long messageCount, 
                                    Long uniqueSenders, Long uniqueReceivers, Long readMessages) {
        this.period = period;
        this.messageCount = messageCount;
        this.uniqueSenders = uniqueSenders;
        this.uniqueReceivers = uniqueReceivers;
        this.readMessages = readMessages;
    }
    
    // Getters
    public YearMonth getPeriod() { return period; }
    public Long getMessageCount() { return messageCount; }
    public Long getUniqueSenders() { return uniqueSenders; }
    public Long getUniqueReceivers() { return uniqueReceivers; }
    public Long getReadMessages() { return readMessages; }
    
    public Double getReadRate() {
        return messageCount > 0 ? (double) readMessages / messageCount * 100 : 0.0;
    }
}
```

## 📈 성능 최적화 포인트

### 1. 인덱스 최적화
```sql
-- 메시지 시스템 최적화
CREATE INDEX idx_message_receiver_read ON messages(receiver_user_id, is_read, created_at DESC);
CREATE INDEX idx_message_conversation ON messages(sender_user_id, receiver_user_id, created_at);
CREATE INDEX idx_message_search ON messages(content);
CREATE INDEX idx_message_route_tag_message ON message_route_tags(message_id, relevance_score DESC);
CREATE INDEX idx_message_route_tag_route ON message_route_tags(route_id, created_at DESC);
```

### 2. 실시간 메시지 최적화
- **WebSocket 연동**: 실시간 메시지 전송 및 읽음 상태 동기화
- **Redis 캐싱**: 온라인 사용자 상태, 최근 대화 목록
- **푸시 알림**: Firebase FCM 연동으로 오프라인 알림

### 3. 메시지 검색 최적화
- **전문 검색**: ElasticSearch 연동 고려
- **인덱싱**: 메시지 내용 full-text 인덱스
- **캐싱**: 자주 검색되는 키워드 캐싱

## 🎯 주요 기능
- ✅ **실시간 메시징**: WebSocket 기반 즉시 전송
- ✅ **읽음 상태 관리**: 읽음/미읽음 추적
- ✅ **대화 스레드**: 사용자 간 대화 이력 관리
- ✅ **메시지 검색**: 내용 기반 검색 기능
- ✅ **루트 태깅**: 메시지에서 루트 언급 추적
- ✅ **분석 기능**: 메시지 패턴 및 사용 통계

---
*Step 5-4f2 완료: 메시지 시스템 Repository 생성 완료*  
*다음: step5-4f3 시스템 관리 Repository 대기 중*