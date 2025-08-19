-- ============================================
-- RoutePickr Database Schema
-- ============================================
-- 
-- 📖 사용하기 전에 README.md 파일을 먼저 읽어보세요!
--    데이터베이스 구조와 관계를 이해하고 개발하시기 바랍니다.
--
-- 📁 필요한 파일:
--    - database.sql (이 파일) : 테이블 생성 스크립트  
--    - README.md : 데이터베이스 구조 설명서
--
-- 🚀 실행 방법:
--    1. README.md 파일 읽기
--    2. mysql -u root -p < database.sql
--
-- ============================================

CREATE DATABASE IF NOT EXISTS `routepick` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `routepick`;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- 사용자 테이블
CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '',
  `nick_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_type` enum('NORMAL','ADMIN','GYM_ADMIN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login_at` timestamp NULL DEFAULT NULL,
  `user_status` enum('ACTIVE','INACTIVE','SUSPENDED','DELETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `birth_date` date DEFAULT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail_address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_contact` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `nick_name` (`nick_name`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 클라이밍 레벨
CREATE TABLE `climbing_levels` (
  `level_id` int NOT NULL AUTO_INCREMENT,
  `level_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 체육관
CREATE TABLE `gyms` (
  `gym_id` int NOT NULL AUTO_INCREMENT,
  `gym_admin_id` int DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`gym_id`),
  KEY `gym_admin_id` (`gym_admin_id`),
  KEY `idx_gyms_name` (`name`),
  CONSTRAINT `gyms_ibfk_1` FOREIGN KEY (`gym_admin_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 체육관 지점
CREATE TABLE `gym_branches` (
  `branch_id` int NOT NULL AUTO_INCREMENT,
  `gym_id` int NOT NULL,
  `branch_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_number` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `logo_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `contact_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `business_hours` json DEFAULT NULL,
  `amenities` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `branch_status` enum('ACTIVE','INACTIVE','CLOSED','PENDING') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`branch_id`),
  KEY `idx_gym_branches_gym_id` (`gym_id`),
  KEY `idx_gym_branches_location` (`latitude`,`longitude`),
  CONSTRAINT `gym_branches_ibfk_1` FOREIGN KEY (`gym_id`) REFERENCES `gyms` (`gym_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 벽
CREATE TABLE `walls` (
  `wall_id` int NOT NULL AUTO_INCREMENT,
  `branch_id` int NOT NULL,
  `wall_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `set_date` date DEFAULT NULL,
  `last_available_date` date DEFAULT NULL,
  `removal_after_hours` tinyint(1) DEFAULT '0',
  `wall_status` enum('ACTIVE','INACTIVE','MAINTENANCE') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`wall_id`),
  KEY `idx_walls_branch_id` (`branch_id`),
  CONSTRAINT `walls_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 세터
CREATE TABLE `route_setters` (
  `setter_id` int NOT NULL AUTO_INCREMENT,
  `gym_id` int NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `birth_date` date DEFAULT NULL,
  `gender` enum('MALE','FEMALE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `setter_type` enum('EMPLOYEE','GUEST') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EMPLOYEE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` int DEFAULT NULL,
  `setter_status` enum('ACTIVE','INACTIVE','SUSPENDED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`setter_id`),
  KEY `created_by` (`created_by`),
  KEY `idx_route_setters_gym_id` (`gym_id`),
  CONSTRAINT `route_setters_ibfk_1` FOREIGN KEY (`gym_id`) REFERENCES `gyms` (`gym_id`),
  CONSTRAINT `route_setters_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트
CREATE TABLE `routes` (
  `route_id` int NOT NULL AUTO_INCREMENT,
  `branch_id` int NOT NULL,
  `wall_id` int NOT NULL,
  `setter_id` int DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `angle` enum('VERTICAL','SLIGHT_OVERHANG','OVERHANG','ROOF') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `level_id` int NOT NULL,
  `color` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `route_status` enum('ACTIVE','EXPIRED','REMOVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`route_id`),
  KEY `wall_id` (`wall_id`),
  KEY `idx_routes_branch_id` (`branch_id`),
  KEY `idx_routes_level_id` (`level_id`),
  KEY `idx_routes_setter_id` (`setter_id`),
  KEY `idx_routes_branch_level` (`branch_id`,`level_id`),
  CONSTRAINT `routes_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`),
  CONSTRAINT `routes_ibfk_2` FOREIGN KEY (`wall_id`) REFERENCES `walls` (`wall_id`),
  CONSTRAINT `routes_ibfk_3` FOREIGN KEY (`setter_id`) REFERENCES `route_setters` (`setter_id`),
  CONSTRAINT `routes_ibfk_4` FOREIGN KEY (`level_id`) REFERENCES `climbing_levels` (`level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 태그 시스템
CREATE TABLE `tags` (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `tag_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `tag_type` enum('STYLE','FEATURE','TECHNIQUE','DIFFICULTY','MOVEMENT','HOLD_TYPE','WALL_ANGLE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER',
  `tag_category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `is_user_selectable` tinyint(1) DEFAULT '1',
  `is_route_taggable` tinyint(1) DEFAULT '1',
  `display_order` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `tag_name` (`tag_name`),
  KEY `idx_tags_tag_type` (`tag_type`),
  KEY `idx_tags_category` (`tag_category`),
  KEY `idx_tags_user_selectable` (`is_user_selectable`),
  KEY `idx_tags_route_taggable` (`is_route_taggable`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 태그 연결
CREATE TABLE `route_tags` (
  `route_tag_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `tag_id` int NOT NULL,
  `relevance_score` decimal(3,2) DEFAULT '1.00',
  `created_by` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`route_tag_id`),
  UNIQUE KEY `uk_route_tag` (`route_id`, `tag_id`),
  KEY `idx_route_tags_route_id` (`route_id`),
  KEY `idx_route_tags_tag_id` (`tag_id`),
  KEY `idx_relevance_score` (`relevance_score`),
  CONSTRAINT `route_tags_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE,
  CONSTRAINT `route_tags_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`tag_id`) ON DELETE CASCADE,
  CONSTRAINT `route_tags_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 프로필
CREATE TABLE `user_profile` (
  `detail_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `gender` enum('MALE','FEMALE','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `height` int DEFAULT NULL,
  `weight` int DEFAULT NULL,
  `wingspan` int DEFAULT NULL,
  `pull_reach` int DEFAULT NULL,
  `level_id` int DEFAULT NULL,
  `branch_id` int DEFAULT NULL,
  `following_count` int DEFAULT '0',
  `follower_count` int DEFAULT '0',
  `status_message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `preferences` json DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`detail_id`),
  KEY `user_id` (`user_id`),
  KEY `level_id` (`level_id`),
  KEY `branch_id` (`branch_id`),
  CONSTRAINT `user_profile_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `user_profile_ibfk_2` FOREIGN KEY (`level_id`) REFERENCES `climbing_levels` (`level_id`),
  CONSTRAINT `user_profile_ibfk_3` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 선호 태그
CREATE TABLE `user_preferred_tags` (
  `user_tag_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `tag_id` int NOT NULL,
  `preference_level` enum('LOW','MEDIUM','HIGH') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM',
  `skill_level` enum('BEGINNER','INTERMEDIATE','ADVANCED','EXPERT') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'BEGINNER',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_tag_id`),
  UNIQUE KEY `uk_user_tag` (`user_id`, `tag_id`),
  KEY `idx_user_preferred_tags_user_id` (`user_id`),
  KEY `idx_user_preferred_tags_tag_id` (`tag_id`),
  KEY `idx_preference_level` (`preference_level`),
  CONSTRAINT `user_preferred_tags_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_preferred_tags_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 루트 추천
CREATE TABLE `user_route_recommendations` (
  `recommendation_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `route_id` int NOT NULL,
  `recommendation_score` decimal(5,2) NOT NULL,
  `tag_match_score` decimal(5,2) DEFAULT NULL,
  `level_match_score` decimal(5,2) DEFAULT NULL,
  `calculated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_active` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`recommendation_id`),
  UNIQUE KEY `uk_user_route_recommendation` (`user_id`, `route_id`),
  KEY `idx_user_recommendations` (`user_id`, `recommendation_score` DESC),
  KEY `idx_route_recommendations` (`route_id`),
  KEY `idx_recommendation_score` (`recommendation_score` DESC),
  CONSTRAINT `user_route_recommendations_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_route_recommendations_ibfk_2` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 클라이밍 기록
CREATE TABLE `user_climbs` (
  `climb_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `route_id` int NOT NULL,
  `climb_date` date NOT NULL,
  `notes` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `record_status` enum('ACTIVE','REMOVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`climb_id`),
  KEY `idx_user_climbs_user_id` (`user_id`),
  KEY `idx_user_climbs_route_id` (`route_id`),
  KEY `idx_user_climbs_climb_date` (`climb_date`),
  CONSTRAINT `user_climbs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_climbs_ibfk_2` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 클라이밍 신발
CREATE TABLE `climbing_shoes` (
  `shoe_id` int NOT NULL AUTO_INCREMENT,
  `brand` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`shoe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 클라이밍 신발
CREATE TABLE `user_climbing_shoes` (
  `user_shoe_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `shoe_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_shoe_id`),
  KEY `user_id` (`user_id`),
  KEY `shoe_id` (`shoe_id`),
  CONSTRAINT `user_climbing_shoes_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_climbing_shoes_ibfk_2` FOREIGN KEY (`shoe_id`) REFERENCES `climbing_shoes` (`shoe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시판 카테고리
CREATE TABLE `board_categories` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글
CREATE TABLE `posts` (
  `post_id` int NOT NULL AUTO_INCREMENT,
  `category_id` int NOT NULL,
  `user_id` int NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `link_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `like_count` int DEFAULT '0',
  `comment_count` int DEFAULT '0',
  `video_count` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `post_status` enum('ACTIVE','REMOVED','HIDDEN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`post_id`),
  KEY `idx_posts_category_id` (`category_id`),
  KEY `idx_posts_user_id` (`user_id`),
  KEY `idx_posts_created_at` (`created_at`),
  CONSTRAINT `posts_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `board_categories` (`category_id`),
  CONSTRAINT `posts_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 댓글
CREATE TABLE `comments` (
  `comment_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `user_id` int NOT NULL,
  `parent_id` int DEFAULT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `like_count` int DEFAULT '0',
  `is_accepted_answer` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `comment_status` enum('ACTIVE','REMOVED','HIDDEN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`comment_id`),
  KEY `idx_comments_post_id` (`post_id`),
  KEY `idx_comments_user_id` (`user_id`),
  KEY `idx_comments_parent_id` (`parent_id`),
  CONSTRAINT `comments_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE,
  CONSTRAINT `comments_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `comments_ibfk_3` FOREIGN KEY (`parent_id`) REFERENCES `comments` (`comment_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 이미지
CREATE TABLE `route_images` (
  `image_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_main` tinyint(1) DEFAULT '0',
  `upload_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `image_status` enum('ACTIVE','REMOVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`image_id`),
  KEY `idx_route_images_route_id` (`route_id`),
  CONSTRAINT `route_images_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 비디오
CREATE TABLE `route_videos` (
  `video_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `video_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `thumbnail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `video_size` bigint DEFAULT NULL,
  `is_main` tinyint(1) DEFAULT '0',
  `upload_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `video_status` enum('ACTIVE','REMOVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'ACTIVE',
  PRIMARY KEY (`video_id`),
  KEY `idx_route_videos_route_id` (`route_id`),
  CONSTRAINT `route_videos_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 댓글
CREATE TABLE `route_comments` (
  `comment_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `user_id` int NOT NULL,
  `parent_id` int DEFAULT NULL,
  `is_reply` tinyint(1) DEFAULT '0',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `comment_status` enum('ACTIVE','REMOVED','HIDDEN') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`comment_id`),
  KEY `user_id` (`user_id`),
  KEY `parent_id` (`parent_id`),
  KEY `idx_route_comments_route_id` (`route_id`),
  CONSTRAINT `route_comments_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE,
  CONSTRAINT `route_comments_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `route_comments_ibfk_3` FOREIGN KEY (`parent_id`) REFERENCES `route_comments` (`comment_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 난이도 투표
CREATE TABLE `route_difficulty_votes` (
  `vote_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `user_id` int NOT NULL,
  `difficulty_level` enum('EASY','MEDIUM','HARD') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`vote_id`),
  UNIQUE KEY `uk_route_user` (`route_id`,`user_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_route_difficulty_votes_route_id` (`route_id`),
  CONSTRAINT `route_difficulty_votes_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE,
  CONSTRAINT `route_difficulty_votes_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 루트 스크랩
CREATE TABLE `route_scraps` (
  `scrap_id` int NOT NULL AUTO_INCREMENT,
  `route_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `scrap_status` enum('ACTIVE','REMOVED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`scrap_id`),
  KEY `route_id` (`route_id`),
  KEY `idx_route_scraps_user_id` (`user_id`),
  CONSTRAINT `route_scraps_ibfk_1` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE,
  CONSTRAINT `route_scraps_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 지점 이미지
CREATE TABLE `branch_images` (
  `image_id` int NOT NULL AUTO_INCREMENT,
  `branch_id` int NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`image_id`),
  KEY `idx_branch_images_branch_id` (`branch_id`),
  CONSTRAINT `branch_images_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 체육관 멤버
CREATE TABLE `gym_members` (
  `gym_member_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `branch_id` int NOT NULL,
  `role` enum('MANAGER','STAFF') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STAFF',
  `joined_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `member_status` enum('ACTIVE','INACTIVE','SUSPENDED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`gym_member_id`),
  KEY `user_id` (`user_id`),
  KEY `branch_id` (`branch_id`),
  CONSTRAINT `gym_members_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`),
  CONSTRAINT `gym_members_ibfk_2` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 팔로우
CREATE TABLE `user_follows` (
  `follow_id` int NOT NULL AUTO_INCREMENT,
  `follower_id` int NOT NULL,
  `following_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`follow_id`),
  KEY `idx_user_follows_follower` (`follower_id`),
  KEY `idx_user_follows_following` (`following_id`),
  CONSTRAINT `user_follows_ibfk_1` FOREIGN KEY (`follower_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_follows_ibfk_2` FOREIGN KEY (`following_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 이미지
CREATE TABLE `post_images` (
  `image_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`image_id`),
  KEY `idx_post_images_post_id` (`post_id`),
  CONSTRAINT `post_images_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 비디오
CREATE TABLE `post_videos` (
  `video_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `video_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `thumbnail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `duration` int DEFAULT NULL,
  `video_size` bigint DEFAULT NULL,
  `display_order` int DEFAULT '0',
  `processing_status` enum('PROCESSING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'PROCESSING',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`video_id`),
  KEY `idx_post_videos_post_id` (`post_id`),
  CONSTRAINT `post_videos_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 좋아요
CREATE TABLE `post_likes` (
  `like_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`like_id`),
  KEY `idx_post_likes_user_id` (`user_id`),
  KEY `idx_post_likes_post_id` (`post_id`),
  CONSTRAINT `post_likes_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE,
  CONSTRAINT `post_likes_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 북마크
CREATE TABLE `post_bookmarks` (
  `bookmark_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bookmark_id`),
  KEY `idx_post_bookmarks_user_id` (`user_id`),
  KEY `idx_post_bookmarks_post_id` (`post_id`),
  CONSTRAINT `post_bookmarks_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE,
  CONSTRAINT `post_bookmarks_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 루트 태그
CREATE TABLE `post_route_tags` (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `post_id` int NOT NULL,
  `route_id` int NOT NULL,
  PRIMARY KEY (`tag_id`),
  KEY `idx_post_route_tags_post_id` (`post_id`),
  KEY `idx_post_route_tags_route_id` (`route_id`),
  CONSTRAINT `post_route_tags_ibfk_1` FOREIGN KEY (`post_id`) REFERENCES `posts` (`post_id`) ON DELETE CASCADE,
  CONSTRAINT `post_route_tags_ibfk_2` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 댓글 좋아요
CREATE TABLE `comment_likes` (
  `like_id` int NOT NULL AUTO_INCREMENT,
  `comment_id` int NOT NULL,
  `user_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`like_id`),
  KEY `idx_comment_likes_user_id` (`user_id`),
  KEY `idx_comment_likes_comment_id` (`comment_id`),
  CONSTRAINT `comment_likes_ibfk_1` FOREIGN KEY (`comment_id`) REFERENCES `comments` (`comment_id`) ON DELETE CASCADE,
  CONSTRAINT `comment_likes_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 메시지
CREATE TABLE `messages` (
  `message_id` int NOT NULL AUTO_INCREMENT,
  `sender_id` int NOT NULL,
  `receiver_id` int NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`message_id`),
  KEY `idx_messages_sender_id` (`sender_id`),
  KEY `idx_messages_receiver_id` (`receiver_id`),
  KEY `idx_messages_created_at` (`created_at`),
  CONSTRAINT `messages_ibfk_1` FOREIGN KEY (`sender_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `messages_ibfk_2` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 메시지 루트 태그
CREATE TABLE `message_route_tags` (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `message_id` int NOT NULL,
  `route_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tag_id`),
  KEY `idx_message_route_tags_message_id` (`message_id`),
  KEY `idx_message_route_tags_route_id` (`route_id`),
  CONSTRAINT `message_route_tags_ibfk_1` FOREIGN KEY (`message_id`) REFERENCES `messages` (`message_id`) ON DELETE CASCADE,
  CONSTRAINT `message_route_tags_ibfk_2` FOREIGN KEY (`route_id`) REFERENCES `routes` (`route_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 알림
CREATE TABLE `notifications` (
  `notification_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `type` enum('SYSTEM','COMMENT','LIKE','FOLLOW','CLIMB','ROUTE_UPDATE','PAYMENT') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `reference_id` int DEFAULT NULL,
  `reference_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  KEY `idx_notifications_user_id` (`user_id`),
  KEY `idx_notifications_is_read` (`is_read`),
  KEY `idx_notifications_created_at` (`created_at`),
  CONSTRAINT `notifications_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 공지사항
CREATE TABLE `notices` (
  `notice_id` int NOT NULL AUTO_INCREMENT,
  `branch_id` int DEFAULT NULL,
  `gym_id` int DEFAULT NULL,
  `user_id` int NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `notice_type` enum('APP','GYM','BRANCH') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`notice_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_notices_branch_id` (`branch_id`),
  KEY `idx_notices_gym_id` (`gym_id`),
  KEY `idx_notices_notice_type` (`notice_type`),
  KEY `idx_notices_is_active` (`is_active`),
  KEY `idx_notices_created_at` (`created_at`),
  CONSTRAINT `notices_ibfk_1` FOREIGN KEY (`branch_id`) REFERENCES `gym_branches` (`branch_id`) ON DELETE CASCADE,
  CONSTRAINT `notices_ibfk_2` FOREIGN KEY (`gym_id`) REFERENCES `gyms` (`gym_id`) ON DELETE CASCADE,
  CONSTRAINT `notices_ibfk_3` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 배너
CREATE TABLE `banners` (
  `banner_id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_order` int DEFAULT '0',
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`banner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 앱 팝업
CREATE TABLE `app_popups` (
  `popup_id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` datetime NOT NULL,
  `end_date` datetime NOT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`popup_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- API 토큰
CREATE TABLE `api_tokens` (
  `token_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `token` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_type` enum('ACCESS','REFRESH','RESET_PASSWORD','EMAIL_VERIFICATION') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `is_revoked` tinyint(1) DEFAULT '0',
  PRIMARY KEY (`token_id`),
  KEY `idx_api_tokens_user_id` (`user_id`),
  KEY `idx_api_tokens_expires_at` (`expires_at`),
  KEY `idx_api_tokens_token` (`token`(255)),
  CONSTRAINT `api_tokens_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- API 로그
CREATE TABLE `api_logs` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `endpoint` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `response_time` int DEFAULT NULL,
  `status_code` int DEFAULT NULL,
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`log_id`),
  KEY `idx_api_logs_user_id` (`user_id`),
  KEY `idx_api_logs_request_time` (`request_time`),
  KEY `idx_api_logs_status_code` (`status_code`),
  CONSTRAINT `api_logs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 소셜 계정
CREATE TABLE `social_accounts` (
  `social_account_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `provider` enum('GOOGLE','KAKAO','NAVER','FACEBOOK') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `social_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `access_token` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `refresh_token` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `token_expires_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`social_account_id`),
  UNIQUE KEY `idx_social_provider_id` (`provider`,`social_id`),
  KEY `idx_social_accounts_user_id` (`user_id`),
  KEY `idx_social_accounts_provider` (`provider`),
  CONSTRAINT `social_accounts_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 검증
CREATE TABLE `user_verifications` (
  `verification_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `ci` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `di` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `real_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone_verified` tinyint(1) DEFAULT '0',
  `verification_method` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `verified_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`verification_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `user_verifications_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 약관 내용
CREATE TABLE `agreement_contents` (
  `agreement_content_id` int NOT NULL AUTO_INCREMENT,
  `agreement_type` enum('TERMS','PRIVACY','MARKETING','LOCATION') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_required` tinyint(1) NOT NULL DEFAULT '1',
  `effective_date` date NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` int DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  PRIMARY KEY (`agreement_content_id`),
  UNIQUE KEY `idx_agreement_contents_type_version` (`agreement_type`,`version`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `agreement_contents_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 사용자 약관 동의
CREATE TABLE `user_agreements` (
  `agreement_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `agreement_type` enum('TERMS','PRIVACY','MARKETING','LOCATION') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `agreement_version` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_agreed` tinyint(1) NOT NULL DEFAULT '1',
  `agreed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`agreement_id`),
  KEY `idx_user_agreements_user_id` (`user_id`),
  KEY `idx_user_agreements_type_version` (`agreement_type`,`agreement_version`),
  CONSTRAINT `user_agreements_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 외부 API 설정
CREATE TABLE `external_api_configs` (
  `config_id` int NOT NULL AUTO_INCREMENT,
  `api_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `api_secret` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `additional_config` json DEFAULT NULL,
  `is_active` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  KEY `idx_external_api_configs_api_name` (`api_name`),
  KEY `idx_external_api_configs_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 결제 기록
CREATE TABLE `payment_records` (
  `payment_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `payment_type` enum('MEMBERSHIP','PASS','EVENT','PRODUCT','OTHER') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `payment_method` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pg_provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transaction_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_status` enum('PENDING','COMPLETED','FAILED','CANCELLED','REFUNDED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `payment_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `reference_id` int DEFAULT NULL,
  `reference_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`payment_id`),
  KEY `idx_payment_records_user_id` (`user_id`),
  KEY `idx_payment_records_payment_status` (`payment_status`),
  KEY `idx_payment_records_payment_date` (`payment_date`),
  CONSTRAINT `payment_records_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 결제 상세 정보
CREATE TABLE `payment_details` (
  `detail_id` int NOT NULL AUTO_INCREMENT,
  `payment_id` int NOT NULL,
  `payment_method_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `card_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `card_number` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `card_quota` int DEFAULT NULL,
  `vbank_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vbank_number` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vbank_holder` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `vbank_date` datetime DEFAULT NULL,
  `vbank_issued_at` datetime DEFAULT NULL,
  `receipt_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `custom_data` json DEFAULT NULL,
  PRIMARY KEY (`detail_id`),
  KEY `idx_payment_details_payment_id` (`payment_id`),
  CONSTRAINT `payment_details_ibfk_1` FOREIGN KEY (`payment_id`) REFERENCES `payment_records` (`payment_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 결제 항목
CREATE TABLE `payment_items` (
  `item_id` int NOT NULL AUTO_INCREMENT,
  `payment_id` int NOT NULL,
  `item_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_amount` decimal(10,2) NOT NULL,
  `item_quantity` int DEFAULT '1',
  PRIMARY KEY (`item_id`),
  KEY `idx_payment_items_payment_id` (`payment_id`),
  CONSTRAINT `payment_items_ibfk_1` FOREIGN KEY (`payment_id`) REFERENCES `payment_records` (`payment_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 결제 환불
CREATE TABLE `payment_refunds` (
  `refund_id` int NOT NULL AUTO_INCREMENT,
  `payment_id` int NOT NULL,
  `refund_amount` decimal(10,2) NOT NULL,
  `refund_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `refund_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `refunder_id` int DEFAULT NULL,
  `refund_status` enum('PENDING','COMPLETED','FAILED') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `pg_response` json DEFAULT NULL,
  PRIMARY KEY (`refund_id`),
  KEY `refunder_id` (`refunder_id`),
  KEY `idx_payment_refunds_payment_id` (`payment_id`),
  KEY `idx_payment_refunds_refund_status` (`refund_status`),
  CONSTRAINT `payment_refunds_ibfk_1` FOREIGN KEY (`payment_id`) REFERENCES `payment_records` (`payment_id`),
  CONSTRAINT `payment_refunds_ibfk_2` FOREIGN KEY (`refunder_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 웹훅 로그
CREATE TABLE `webhook_logs` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload` json NOT NULL,
  `received_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `processed` tinyint(1) DEFAULT '0',
  `related_payment_id` int DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `related_payment_id` (`related_payment_id`),
  KEY `idx_webhook_logs_provider` (`provider`),
  KEY `idx_webhook_logs_processed` (`processed`),
  CONSTRAINT `webhook_logs_ibfk_1` FOREIGN KEY (`related_payment_id`) REFERENCES `payment_records` (`payment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본 태그 데이터 삽입
INSERT INTO tags (tag_name, tag_type, tag_category, description, is_user_selectable, is_route_taggable, display_order) VALUES
-- 스타일 (사용자 선호도 + 루트 태깅 모두 가능)
('볼더링', 'STYLE', 'discipline', '낮은 높이에서 짧고 강도 높은 문제를 해결', 1, 1, 1),
('리드클라이밍', 'STYLE', 'discipline', '로프를 사용하여 높은 루트를 완등', 1, 1, 2),
('톱로핑', 'STYLE', 'discipline', '미리 설치된 로프로 안전하게 오르기', 1, 1, 3),
('스피드클라이밍', 'STYLE', 'discipline', '정해진 루트를 빠르게 오르기', 1, 1, 4),

-- 무브먼트 (사용자 선호도 + 루트 태깅)
('다이나믹', 'MOVEMENT', 'power', '역동적인 움직임이 필요', 1, 1, 10),
('정적', 'MOVEMENT', 'balance', '천천히 균형을 맞춰가며', 1, 1, 11),
('파워풀', 'MOVEMENT', 'power', '순간적인 강한 힘이 필요', 1, 1, 12),
('테크니컬', 'MOVEMENT', 'technique', '복잡한 동작 순서 필요', 1, 1, 13),
('지구력', 'MOVEMENT', 'endurance', '오랜 시간 버티는 능력', 1, 1, 14),

-- 테크닉 (사용자 선호도 + 루트 태깅)
('크림핑', 'TECHNIQUE', 'grip', '손가락 끝으로 작은 홀드 잡기', 1, 1, 20),
('슬로핑', 'TECHNIQUE', 'grip', '둥글고 미끄러운 홀드 잡기', 1, 1, 21),
('핀치', 'TECHNIQUE', 'grip', '엄지와 나머지 손가락으로 집기', 1, 1, 22),
('매칭', 'TECHNIQUE', 'movement', '한 홀드에 양손 올리기', 1, 1, 23),
('맨틀', 'TECHNIQUE', 'movement', '홀드 위로 몸을 올리기', 1, 1, 24),
('힐훅', 'TECHNIQUE', 'footwork', '발뒤꿈치로 홀드 걸기', 1, 1, 25),
('토훅', 'TECHNIQUE', 'footwork', '발끝으로 홀드 걸기', 1, 1, 26),

-- 홀드 타입 (주로 루트 태깅용)
('저그', 'HOLD_TYPE', NULL, '잡기 좋은 큰 홀드', 0, 1, 30),
('크림프', 'HOLD_TYPE', NULL, '작고 날카로운 홀드', 0, 1, 31),
('슬로퍼', 'HOLD_TYPE', NULL, '둥글고 미끄러운 홀드', 0, 1, 32),
('핀치홀드', 'HOLD_TYPE', NULL, '집어야 하는 홀드', 0, 1, 33),
('포켓', 'HOLD_TYPE', NULL, '구멍이 뚫린 홀드', 0, 1, 34),

-- 벽 각도 (주로 루트 태깅용)
('슬랩', 'WALL_ANGLE', NULL, '90도 미만의 완만한 벽', 0, 1, 40),
('버티컬', 'WALL_ANGLE', NULL, '수직벽', 0, 1, 41),
('오버행', 'WALL_ANGLE', NULL, '90도 초과의 처진 벽', 0, 1, 42),
('루프', 'WALL_ANGLE', NULL, '천장', 0, 1, 43),

-- 특징 (주로 루트 태깅용)
('슬리퍼리', 'FEATURE', NULL, '미끄러운 홀드', 0, 1, 50),
('크랙', 'FEATURE', NULL, '바위의 갈라진 틈', 0, 1, 51),
('아레뜨', 'FEATURE', NULL, '바위의 모서리', 0, 1, 52),
('볼륨', 'FEATURE', NULL, '큰 조형물', 0, 1, 53),
('튜파', 'FEATURE', NULL, '석회암 특유의 구멍', 0, 1, 54);

-- 추천 시스템 프로시저
DELIMITER //
CREATE PROCEDURE CalculateUserRouteRecommendations(IN p_user_id INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_route_id INT;
    DECLARE v_tag_score DECIMAL(5,2) DEFAULT 0.00;
    DECLARE v_level_score DECIMAL(5,2) DEFAULT 0.00;
    DECLARE v_total_score DECIMAL(5,2) DEFAULT 0.00;
    
    DECLARE route_cursor CURSOR FOR 
        SELECT route_id FROM routes WHERE route_status = 'ACTIVE';
    
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    -- 기존 추천 데이터 삭제
    DELETE FROM user_route_recommendations WHERE user_id = p_user_id;
    
    OPEN route_cursor;
    
    route_loop: LOOP
        FETCH route_cursor INTO v_route_id;
        
        IF done THEN
            LEAVE route_loop;
        END IF;
        
        -- 태그 매칭 점수 계산
        SELECT COALESCE(
            (SELECT AVG(
                CASE upt.preference_level
                    WHEN 'HIGH' THEN rt.relevance_score * 100
                    WHEN 'MEDIUM' THEN rt.relevance_score * 70
                    WHEN 'LOW' THEN rt.relevance_score * 30
                    ELSE 0
                END
            )
            FROM user_preferred_tags upt
            JOIN route_tags rt ON upt.tag_id = rt.tag_id
            WHERE upt.user_id = p_user_id 
              AND rt.route_id = v_route_id), 0
        ) INTO v_tag_score;
        
        -- 레벨 매칭 점수 계산
        SELECT COALESCE(
            (SELECT 
                CASE 
                    WHEN ABS(up.level_id - r.level_id) = 0 THEN 100
                    WHEN ABS(up.level_id - r.level_id) = 1 THEN 80
                    WHEN ABS(up.level_id - r.level_id) = 2 THEN 60
                    WHEN ABS(up.level_id - r.level_id) = 3 THEN 40
                    WHEN ABS(up.level_id - r.level_id) = 4 THEN 20
                    ELSE 10
                END
            FROM user_profile up
            JOIN routes r ON r.route_id = v_route_id
            WHERE up.user_id = p_user_id
              AND up.level_id IS NOT NULL), 50
        ) INTO v_level_score;
        
        -- 총 추천 점수 계산
        SET v_total_score = (v_tag_score * 0.7) + (v_level_score * 0.3);
        
        -- 점수가 20 이상인 경우만 저장
        IF v_total_score >= 20 THEN
            INSERT INTO user_route_recommendations 
            (user_id, route_id, recommendation_score, tag_match_score, level_match_score)
            VALUES (p_user_id, v_route_id, v_total_score, v_tag_score, v_level_score);
        END IF;
        
    END LOOP;
    
    CLOSE route_cursor;
    
END //
DELIMITER ;

-- 유용한 뷰 생성

-- 사용자 프로필용 태그 목록
CREATE VIEW v_user_profile_tags AS
SELECT 
    tag_id,
    tag_name,
    tag_type,
    tag_category,
    description,
    display_order
FROM tags 
WHERE is_user_selectable = 1
ORDER BY display_order, tag_name;

-- 루트 태깅용 태그 목록
CREATE VIEW v_route_tagging_tags AS
SELECT 
    tag_id,
    tag_name,
    tag_type,
    tag_category,
    description,
    display_order
FROM tags 
WHERE is_route_taggable = 1
ORDER BY tag_type, display_order, tag_name;

-- 사용자별 추천 루트
CREATE VIEW v_user_recommended_routes AS
SELECT 
    urr.user_id,
    urr.route_id,
    r.name as route_name,
    r.level_id,
    cl.level_name,
    urr.recommendation_score,
    urr.tag_match_score,
    urr.level_match_score,
    gb.branch_name,
    w.wall_name,
    r.color,
    r.angle,
    GROUP_CONCAT(t.tag_name) as route_tags
FROM user_route_recommendations urr
JOIN routes r ON urr.route_id = r.route_id
JOIN climbing_levels cl ON r.level_id = cl.level_id
JOIN gym_branches gb ON r.branch_id = gb.branch_id
JOIN walls w ON r.wall_id = w.wall_id
LEFT JOIN route_tags rt ON r.route_id = rt.route_id
LEFT JOIN tags t ON rt.tag_id = t.tag_id
WHERE urr.is_active = 1 AND r.route_status = 'ACTIVE'
GROUP BY urr.recommendation_id;

-- MySQL 설정 복원
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;
/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;