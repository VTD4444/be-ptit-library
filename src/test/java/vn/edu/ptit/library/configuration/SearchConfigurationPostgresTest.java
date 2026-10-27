package vn.edu.ptit.library.configuration;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.ptit.library.search.repository.*;
import vn.edu.ptit.library.search.service.SearchConfigurationService;
import vn.edu.ptit.library.search.service.impl.SearchConfigurationServiceImpl;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Opt-in only. Requires an EMPTY disposable localhost database named module2_test*.
 * Never loads application.yml, DB_URL, or the application's datasource.
 * Creates and drops only its owned public.search_vector_configs table.
 */
@EnabledIfEnvironmentVariable(named = "MODULE2_POSTGRES_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchConfigurationPostgresTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SearchConfigurationService service;
    private boolean ownsTable;

    @BeforeAll
    void setup() {
        String url = System.getenv("MODULE2_TEST_JDBC_URL");
        assertThat(url).as("Only an explicitly provided disposable localhost test DB is permitted")
                .matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/module2_test[a-zA-Z0-9_]*");
        context = new AnnotationConfigApplicationContext(DatabaseConfig.class);
        jdbc = context.getBean(JdbcTemplate.class);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.search_vector_configs')::text", String.class))
                .as("Refuse to overwrite an existing table").isNull();
        jdbc.execute("""
                CREATE TABLE public.search_vector_configs (
                  id BIGINT PRIMARY KEY, chunk_size INTEGER, overlap INTEGER, strategy VARCHAR,
                  embedding_model VARCHAR, vector_dimensions INTEGER, is_active BOOLEAN,
                  created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ)
                """);
        ownsTable = true;
        service = context.getBean(SearchConfigurationService.class);
    }

    @BeforeEach
    void seedOwnedTestTable() {
        jdbc.update("DELETE FROM public.search_vector_configs");
        jdbc.update("INSERT INTO public.search_vector_configs(id, embedding_model, is_active) VALUES (1, 'first', true), (2, 'second', false)");
    }

    @AfterAll
    void cleanup() {
        if (context != null) {
            try {
                if (ownsTable) jdbc.execute("DROP TABLE public.search_vector_configs");
            } finally {
                context.close();
            }
        }
    }

    @Test
    void activationIsIdempotentAndPreservesModel() {
        service.activate(2L);
        service.activate(2L);
        assertThat(activeId()).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT embedding_model FROM public.search_vector_configs WHERE id = 2", String.class))
                .isEqualTo("second");
    }

    @Test
    void outerTransactionFailureRestoresPreviousActiveConfiguration() {
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.activate(2L);
            throw new IllegalStateException("force rollback after flush");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(activeId()).isEqualTo(1L);
    }

    @Test
    void concurrentActivationsSerializeAcrossTransactions() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        try {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                service.activate(1L);
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return service.activate(2L);
            });
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(activeId()).isEqualTo(2L);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Long activeId() {
        var ids = jdbc.queryForList("SELECT id FROM public.search_vector_configs WHERE is_active", Long.class);
        assertThat(ids).hasSize(1);
        return ids.get(0);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = SearchVectorConfigRepository.class)
    @Import({SearchConfigurationServiceImpl.class, SearchConfigActivationLock.class})
    static class DatabaseConfig {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("MODULE2_TEST_JDBC_URL"),
                    System.getenv("MODULE2_TEST_DB_USERNAME"), System.getenv("MODULE2_TEST_DB_PASSWORD"));
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("vn.edu.ptit.library");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }
}
