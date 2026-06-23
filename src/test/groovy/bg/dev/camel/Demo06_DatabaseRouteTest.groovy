package bg.dev.camel

import org.apache.camel.test.spring.junit5.CamelSpringBootTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

import static org.assertj.core.api.Assertions.assertThat

/**
 * Integration test for Demo06_DatabaseRoute.
 *
 * Spins up a real MySQL 8 container via Testcontainers.
 * Verifies that the Camel route reads unprocessed customers, marks them
 * as processed, and assigns welcome codes.
 */
@Testcontainers
@CamelSpringBootTest
@ActiveProfiles('demo06')
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class Demo06_DatabaseRouteTest {

  @Container
  static MySQLContainer<?> mysql = new MySQLContainer<>('mysql:8.0')
    .withDatabaseName('camel_demo')
    .withUsername('demo')
    .withPassword('demo123')

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    registry.add('spring.datasource.url', mysql::getJdbcUrl)
    registry.add('spring.datasource.username', mysql::getUsername)
    registry.add('spring.datasource.password', mysql::getPassword)
  }

  @Autowired
  JdbcTemplate jdbc

  @Test
  void routeMarksCustomersAsProcessedAndAssignsWelcomeCodes() throws InterruptedException {
    int total = jdbc.queryForObject('SELECT COUNT(*) FROM customers', Integer)
    assertThat(total).isGreaterThan(0)

    Thread.sleep(35_000)

    List<Map<String, Object>> processed = jdbc.queryForList(
      'SELECT name, welcome_code FROM customers WHERE processed = 1')

    assertThat(processed).isNotEmpty()
    processed.each { row ->
      assertThat(row.get('welcome_code').toString()).startsWith('WC-')
    }
  }
}
