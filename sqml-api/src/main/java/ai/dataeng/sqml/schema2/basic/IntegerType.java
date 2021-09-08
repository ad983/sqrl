package ai.dataeng.sqml.schema2.basic;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class IntegerType extends NumberType<Long> {

    public static final IntegerType INSTANCE = new IntegerType();

    IntegerType() {
        super("INTEGER", new Conversion());
    }

    public static class Conversion extends AbstractBasicType.Conversion<Long> {

        private static final Set<Class> INT_CLASSES = ImmutableSet.of(Integer.class, Long.class, Byte.class, Short.class);

        public Conversion() {
            super(Long.class, s -> Long.parseLong(s));
        }

        @Override
        public Set<Class> getJavaTypes() {
            return INT_CLASSES;
        }

        public Long convert(Object o) {
            Preconditions.checkArgument(o instanceof Number);
            return ((Number)o).longValue();
        }
    }

}
