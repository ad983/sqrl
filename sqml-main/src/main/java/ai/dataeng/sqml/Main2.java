package ai.dataeng.sqml;

import ai.dataeng.sqml.db.keyvalue.HierarchyKeyValueStore;
import ai.dataeng.sqml.db.keyvalue.LocalFileHierarchyKeyValueStore;
import ai.dataeng.sqml.execution.SQMLBundle;
import ai.dataeng.sqml.ingest.DataSourceMonitor;
import ai.dataeng.sqml.flink.DefaultEnvironmentProvider;
import ai.dataeng.sqml.ingest.NamePath;
import ai.dataeng.sqml.ingest.SourceTableStatistics;
import ai.dataeng.sqml.source.simplefile.DirectoryDataset;
import ai.dataeng.sqml.type.SqmlType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class Main2 {

    public static final Path RETAIL_DIR = Path.of(System.getProperty("user.dir")).resolve("sqml-examples").resolve("retail");
    public static final Path RETAIL_DATA_DIR = RETAIL_DIR.resolve("ecommerce-data");
    public static final Path RETAIL_SCRIPT_DIR = RETAIL_DIR.resolve("c360");
    public static final String RETAIL_SCRIPT_NAME = "c360";
    public static final String SQML_SCRIPT_EXTENSION = ".sqml";

    public static final String[] RETAIL_TABLE_NAMES = { "Customer", "Order", "Product"};

    public static final Path outputBase = Path.of("tmp","datasource");


    public static void main(String[] args) throws Exception {
        DirectoryDataset dd = new DirectoryDataset(RETAIL_DATA_DIR);
        HierarchyKeyValueStore.Factory kvStoreFactory = new LocalFileHierarchyKeyValueStore.Factory(outputBase.toString());
        final DataSourceMonitor env = new DataSourceMonitor(new DefaultEnvironmentProvider(), kvStoreFactory);
        env.addDataset(dd);

        Thread.sleep(1000);

        String content = Files.readString(RETAIL_SCRIPT_DIR.resolve(RETAIL_SCRIPT_NAME + SQML_SCRIPT_EXTENSION));
        SQMLBundle sqml = new SQMLBundle.Builder().setMainScript(RETAIL_SCRIPT_NAME, content).build();

        //Retrieve the collected statistics
        for (String table : RETAIL_TABLE_NAMES) {
            SourceTableStatistics tableStats = env.getTableStatistics(dd.getTable(table));
            Map<NamePath, SqmlType> schema = tableStats.getSchema();
            schema.forEach((k,v) -> System.out.printf("%s -> %s\n", k.getQualifiedName(table), v));
        }
    }
}
