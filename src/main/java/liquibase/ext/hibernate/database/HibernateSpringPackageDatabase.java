package liquibase.ext.hibernate.database;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.spi.PersistenceUnitInfo;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.Bootstrap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.SmartPersistenceUnitInfo;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.connection.HibernateConnection;

/**
 * Database implementation for "spring" hibernate configurations that scans packages. If specifying a bean, {@link HibernateSpringBeanDatabase} is used.
 */
public class HibernateSpringPackageDatabase extends JpaPersistenceDatabase {

    public static final String HIBERNATE_SEARCH_AUTOREGISTER_LISTENER = "hibernate.search.autoregister_listener";
    public static final String HIBERNATE_SEARCH_INDEXING_STARTEGY = "hibernate.search.indexing_strategy";
    public static final String HIBERNATE_SEARCH_DEFAULT_INDEXMANAGER = "hibernate.search.default.indexmanager";
    public static final String HIBERNATE_SEARCH_ANALYSIS_DEFINITION_PROVIDER = "hibernate.search.default.elasticsearch.analysis_definition_provider";

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getURL().startsWith("hibernate:spring:") && !isXmlFile(conn);
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 10; //want this to be picked over HibernateSpringBeanDatabase if it is not xml file
    }

    /**
     * Return true if the given path is a spring XML file.
     */
    protected boolean isXmlFile(DatabaseConnection connection) {
        HibernateConnection hibernateConnection;
        if (connection instanceof JdbcConnection) {
            hibernateConnection = ((HibernateConnection) ((JdbcConnection) connection).getUnderlyingConnection());
        } else if (connection instanceof HibernateConnection) {
            hibernateConnection = (HibernateConnection) connection;
        } else {
            return false;
        }


        String path = hibernateConnection.getPath();
        if (path.contains("/")) {
            return true;
        }
        ClassPathResource resource = new ClassPathResource(path);
        try {
            if (resource.exists() && !resource.getFile().isDirectory()) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }


    }

    @Override
    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        DefaultPersistenceUnitManager internalPersistenceUnitManager = new DefaultPersistenceUnitManager();
        internalPersistenceUnitManager.setResourceLoader(new DefaultResourceLoader(getHibernateConnection().getResourceAccessor().toClassLoader()));

        String[] packagesToScan = getHibernateConnection().getPath().split(",");

        for (String packageName : packagesToScan) {
            LOG.info("Found package " + packageName);
        }

        internalPersistenceUnitManager.setPackagesToScan(packagesToScan);

        internalPersistenceUnitManager.preparePersistenceUnitInfos();
        PersistenceUnitInfo persistenceUnitInfo = internalPersistenceUnitManager.obtainDefaultPersistenceUnitInfo();
        HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();

        if (persistenceUnitInfo instanceof SmartPersistenceUnitInfo) {
            ((SmartPersistenceUnitInfo) persistenceUnitInfo).setPersistenceProviderPackageName(jpaVendorAdapter.getPersistenceProviderRootPackage());
        }

        Map<String, String> map = new HashMap<>();
        map.put(AvailableSettings.DIALECT, getProperty(AvailableSettings.DIALECT));
        map.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, Boolean.FALSE.toString());
        map.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, getHibernateConnection().getProperties().getProperty(AvailableSettings.PHYSICAL_NAMING_STRATEGY));
        map.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY, getHibernateConnection().getProperties().getProperty(AvailableSettings.IMPLICIT_NAMING_STRATEGY));
        map.put(HIBERNATE_SEARCH_AUTOREGISTER_LISTENER, getHibernateConnection().getProperties().getProperty(HIBERNATE_SEARCH_AUTOREGISTER_LISTENER));
        map.put(HIBERNATE_SEARCH_INDEXING_STARTEGY, getHibernateConnection().getProperties().getProperty(HIBERNATE_SEARCH_INDEXING_STARTEGY));
        map.put(AvailableSettings.SCANNER_DISCOVERY, "");	// disable scanning of all classes and hbm.xml files. Only scan speficied packages
        
        EntityManagerFactoryBuilderImpl builder = (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(persistenceUnitInfo, map);
        
        return builder;
    }

    @Override
    public String getShortName() {
        return "hibernateSpringPackage";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "Hibernate Spring Package";
    }

}
