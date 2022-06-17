package ai.datasqrl.io.sources.util;

import ai.datasqrl.execute.StreamEngine;
import ai.datasqrl.execute.StreamHolder;
import ai.datasqrl.io.sources.SourceRecord;
import ai.datasqrl.io.sources.dataset.SourceTable;

public interface StreamInputPreparer {

    boolean isRawInput(SourceTable table);

    StreamHolder<SourceRecord.Raw> getRawInput(SourceTable table, StreamEngine.Builder builder);

}
