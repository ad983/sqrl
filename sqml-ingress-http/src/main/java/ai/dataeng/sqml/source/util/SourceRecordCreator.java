package ai.dataeng.sqml.source.util;

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;

public class SourceRecordCreator {

    public static Map<String,Object> dataFrom(String[] header, String[] content) {
        Preconditions.checkArgument(header!=null && header.length>0);
        Preconditions.checkNotNull(content!=null && content.length== header.length);
        HashMap<String,Object> map = new HashMap<>(header.length);
        for (int i = 0; i < header.length; i++) {
            map.put(header[i],content[i]);
        }
        return map;
    }

}
