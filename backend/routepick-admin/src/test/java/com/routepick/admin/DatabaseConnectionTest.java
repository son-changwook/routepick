package com.routepick.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertTrue(connection.isValid(1));
            System.out.println("데이터베이스 연결 성공!");
            System.out.println("URL: " + connection.getMetaData().getURL());
            System.out.println("사용자: " + connection.getMetaData().getUserName());
        } catch (Exception e) {
            System.err.println("데이터베이스 연결 실패: " + e.getMessage());
            throw new RuntimeException("데이터베이스 연결 테스트 실패", e);
        }
    }

    @Test
    void testJdbcTemplate() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            assertNotNull(result);
            assertTrue(result == 1);
            System.out.println("JDBC Template 테스트 성공!");
        } catch (Exception e) {
            System.err.println("JDBC Template 테스트 실패: " + e.getMessage());
            throw new RuntimeException("JDBC Template 테스트 실패", e);
        }
    }
}