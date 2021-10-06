package ai.dataeng.sqml;

import ai.dataeng.sqml.ingest.schema.*;
import ai.dataeng.sqml.ingest.stats.SourceTableStatistics;
import ai.dataeng.sqml.metadata.Metadata;
import ai.dataeng.sqml.ingest.source.SourceRecord;
import ai.dataeng.sqml.ingest.source.SourceTable;
import ai.dataeng.sqml.schema2.name.Name;
import ai.dataeng.sqml.tree.AstVisitor;
import ai.dataeng.sqml.tree.ImportData;
import ai.dataeng.sqml.tree.Node;
import ai.dataeng.sqml.tree.QualifiedName;
import ai.dataeng.sqml.tree.Script;

import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.functions.sink.PrintSinkFunction;
import org.apache.flink.util.OutputTag;

public class ImportPipelineResolver {

  private final Metadata metadata;

  public ImportPipelineResolver(Metadata metadata) {
    this.metadata = metadata;
  }

  public void analyze(Script script) {
    Visitor visitor = new Visitor();
    script.accept(visitor, null);
  }

  class Visitor extends AstVisitor<Void, Void> {

    @Override
    protected Void visitScript(Script node, Void context) {
      for (Node statement : node.getStatements()) {
        statement.accept(this, context);
      }
      return null;
    }

    @Override
    protected Void visitImportState(ImportData node, Void context) {
      String loc = node.getQualifiedName().getParts().get(0);
      String tableName = node.getQualifiedName().getParts().get(1);

      SourceTable stable = metadata.getDataset(loc).getTable(tableName);
      SourceTableStatistics tableStats = metadata.getDatasourceTableStatistics(stable);
      FlexibleDatasetSchema.TableField tableSchema = null; //TODO: FIX! tableStats.getSchema();

      DataStream<SourceRecord<String>> stream = stable.getDataStream(metadata.getFlinkEnv());
      final OutputTag<SchemaValidationProcess.Error> schemaErrorTag = new OutputTag<>("schema-error-"+tableName){};
      SingleOutputStreamOperator<SourceRecord<Name>> validate = stream.process(new SchemaValidationProcess(schemaErrorTag, tableSchema,
              SchemaAdjustmentSettings.DEFAULT,
              metadata.getDataset(loc).getRegistration()));
      validate.getSideOutput(schemaErrorTag).addSink(new PrintSinkFunction<>()); //TODO: handle errors
      //Todo: hackery to get retail example up and running
      QualifiedName parts;
      if (tableName.equalsIgnoreCase("orders")) {
        parts = QualifiedName.of(loc, "orders", "entries");
      } else {
        parts = node.getQualifiedName();
      }

      /*
      for (int i = 1; i < parts.getParts().size(); i++) {
        //Todo: hackery, move to qualified name
        NamePathOld shreddingPath;
        if (i == 1) {
          shreddingPath = NamePathOld.ROOT;
        } else {
          List<String> shreddedPart = parts.getParts().subList(2, i + 1);
          shreddingPath = NamePathOld.of(shreddedPart.toArray(new String[0]));
        }
        RecordShredder shredder = RecordShredder.from(shreddingPath, tableSchema);
        SingleOutputStreamOperator<Row> process = validate.flatMap(shredder.getProcess(),
            FlinkUtilities.convert2RowTypeInfo(shredder.getResultSchema()));

        process.addSink(new PrintSinkFunction<>()); //TODO: remove, debugging only

        Table table = metadata.getStreamTableEnvironment().fromDataStream(process);
        table.printSchema();

        String shreddedTableName = tableName;
        if (shreddingPath.getNumComponents()>0) shreddedTableName += "_" + shreddingPath.toString('_');

        metadata.registerTable(QualifiedName.of(parts.getParts().subList(1, i + 1)), table, shredder.getResultSchema());
      }
      */

      return null;
    }
  }
}
