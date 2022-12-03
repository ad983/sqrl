package com.datasqrl.config;

import com.datasqrl.error.ErrorCollector;
import com.datasqrl.util.ConfigurationUtil;
import com.datasqrl.util.StreamUtil;
import com.datasqrl.metadata.MetadataConfiguration;
import com.datasqrl.metadata.MetadataStoreProvider;
import com.datasqrl.physical.EngineConfiguration;
import com.datasqrl.physical.ExecutionEngine;
import com.datasqrl.physical.database.DatabaseEngine;
import com.datasqrl.physical.database.DatabaseEngineConfiguration;
import com.datasqrl.physical.pipeline.EnginePipeline;
import com.datasqrl.physical.pipeline.ExecutionPipeline;
import com.datasqrl.physical.stream.StreamEngine;
import com.datasqrl.spi.GlobalConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.base.Strings;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for the engines
 *
 * TODO: Should extend GlobalLoaderConfiguration once we make loaders configurable
 */
@SuperBuilder
@NoArgsConstructor
@Getter
public class GlobalEngineConfiguration implements GlobalConfiguration {

    @Valid
    List<EngineConfiguration> engines;

    @Builder.Default @NonNull @Valid
    MetadataConfiguration metadata = new MetadataConfiguration();

    public EngineSettings initializeEngines(ErrorCollector errors) {
        if (!ConfigurationUtil.javaxValidate(this,errors)) return null;

        Optional<DatabaseEngineConfiguration> dbOpt = StreamUtil.filterByClass(engines,DatabaseEngineConfiguration.class)
                .findFirst();
        if (dbOpt.isEmpty()) {
            errors.fatal("Need to configure a database engine");
            return null;
        }
        Optional<EngineConfiguration> streamOpt = engines.stream().filter(conf -> conf.getEngineType()== ExecutionEngine.Type.STREAM)
                .findFirst();
        if (streamOpt.isEmpty()) {
            errors.fatal("Need to configure a stream engine");
            return null;
        }

        DatabaseEngine db = dbOpt.get().initialize(errors.resolve(dbOpt.get().getEngineName()));
        StreamEngine stream = (StreamEngine) streamOpt.get().initialize(errors.resolve(streamOpt.get().getEngineName()));

        Optional<DatabaseEngineConfiguration> metaOpt = dbOpt;
        if (!Strings.isNullOrEmpty(metadata.getEngineType())) {
            metaOpt = StreamUtil.filterByClass(engines,DatabaseEngineConfiguration.class)
                    .filter(eng -> eng.getEngineName().equalsIgnoreCase(metadata.getEngineType()))
                    .findAny();
            if (metaOpt.isEmpty()) {
                errors.fatal("Could not find database engine for metadata store with name: %s", metadata.getEngineType());
                return null;
            }
        }
        MetadataStoreProvider metadataStoreProvider = metaOpt.get().getMetadataStore();
        ExecutionPipeline pipeline = new EnginePipeline(db,stream);
        return new EngineSettings(pipeline, metadataStoreProvider, db.getConnectionProvider(), stream);
    }

    public void setDefaultEngines(DatabaseEngineConfiguration db, EngineConfiguration stream) {
        if (engines==null || engines.isEmpty()) {
            engines = new ArrayList<>();
            engines.add(stream);
            engines.add(db);
        }
    }

    @SneakyThrows
    public static<C extends GlobalEngineConfiguration> C readFrom(Path path, Class<C> clazz) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(EngineConfiguration.class, new EngineConfiguration.Deserializer());
        ObjectMapper mapper = new ObjectMapper().registerModule(module);
        return mapper.readValue(path.toFile(), clazz);
    }

}
