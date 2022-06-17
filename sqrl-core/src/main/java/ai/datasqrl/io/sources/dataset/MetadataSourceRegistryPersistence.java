package ai.datasqrl.io.sources.dataset;

import ai.datasqrl.config.metadata.MetadataStore;
import ai.datasqrl.config.provider.DatasetRegistryPersistenceProvider;
import ai.datasqrl.config.provider.TableStatisticsStoreProvider;
import ai.datasqrl.io.sources.DataSource;
import ai.datasqrl.io.sources.SourceTableConfiguration;
import ai.datasqrl.io.sources.stats.SourceTableStatistics;
import ai.datasqrl.parse.tree.name.Name;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MetadataSourceRegistryPersistence implements DatasetRegistryPersistence, TableStatisticsStore {

  public static final String STORE_TABLE_STATS_KEY = "stats";
  public static final String STORE_DATASET_KEY = "datasets";
  public static final String STORE_SOURCE_CONFIG_KEY = "source";
  public static final String STORE_TABLE_KEY = "tables";
  public static final String STORE_TABLE_CONFIG_KEY = "config";

  private final MetadataStore store;

  @Override
  public Collection<DataSource> getDatasets() {
    return store.getSubKeys(STORE_DATASET_KEY).stream().map(dsName -> {
      DataSource config = store.get(DataSource.class, STORE_DATASET_KEY, dsName,
          STORE_SOURCE_CONFIG_KEY);
      Preconditions.checkArgument(config != null,
          "Persistence of configuration failed.");
      return config;
    }).collect(Collectors.toList());
  }

  @Override
  public void putDataset(Name dataset, DataSource datasource) {
    store.put(datasource, STORE_DATASET_KEY, store.name2Key(dataset), STORE_SOURCE_CONFIG_KEY);
  }

  @Override
  public boolean removeDataset(Name dataset) {
    return store.remove(STORE_DATASET_KEY, store.name2Key(dataset), STORE_SOURCE_CONFIG_KEY);
  }

  @Override
  public Set<SourceTableConfiguration> getTables(Name dataset) {
    return store.getSubKeys(STORE_DATASET_KEY, store.name2Key(dataset), STORE_TABLE_KEY).stream()
        .map(tbName -> {
          SourceTableConfiguration config = store.get(SourceTableConfiguration.class,
              STORE_DATASET_KEY, store.name2Key(dataset), STORE_TABLE_KEY, tbName,
              STORE_TABLE_CONFIG_KEY);
          Preconditions.checkArgument(config != null, "Persistence of configuration failed.");
          return config;
        }).collect(Collectors.toSet());
  }

  @Override
  public void putTable(Name dataset, Name tblName, SourceTableConfiguration table) {
    store.put(table, STORE_DATASET_KEY, store.name2Key(dataset), STORE_TABLE_KEY,
        store.name2Key(tblName), STORE_TABLE_CONFIG_KEY);
  }

  @Override
  public boolean removeTable(Name dataset, Name tblName) {
    return store.remove(STORE_DATASET_KEY, store.name2Key(dataset), STORE_TABLE_KEY,
        store.name2Key(tblName), STORE_TABLE_CONFIG_KEY);
  }

  @Override
  public SourceTableStatistics getTableStatistics(Name datasetName, Name tableName) {
    return store.get(SourceTableStatistics.class,
        STORE_DATASET_KEY, store.name2Key(datasetName), STORE_TABLE_KEY, store.name2Key(tableName),
        STORE_TABLE_STATS_KEY);
  }

  @Override
  public void putTableStatistics(Name datasetName, Name tableName, SourceTableStatistics stats) {
    store.put(stats,
        STORE_DATASET_KEY, store.name2Key(datasetName), STORE_TABLE_KEY, store.name2Key(tableName),
        STORE_TABLE_STATS_KEY);
  }

  @Override
  public boolean removeTableStatistics(Name datasetName, Name tableName) {
    return store.remove(STORE_DATASET_KEY, store.name2Key(datasetName), STORE_TABLE_KEY,
        store.name2Key(tableName), STORE_TABLE_STATS_KEY);
  }

  @Override
  public void close() throws IOException {
    store.close();
  }

  public static class RegistryProvider implements DatasetRegistryPersistenceProvider {

    @Override
    public DatasetRegistryPersistence createRegistryPersistence(MetadataStore metaStore) {
      return new MetadataSourceRegistryPersistence(metaStore);
    }
  }


  public static class TableStatsProvider implements TableStatisticsStoreProvider {

    @Override
    public TableStatisticsStore openStore(MetadataStore metaStore) {
      return new MetadataSourceRegistryPersistence(metaStore);
    }
  }


}
