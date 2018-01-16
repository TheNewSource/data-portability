package org.dataportabilityproject.cloud.interfaces;

import com.google.auto.value.AutoValue;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public interface Metrics {
  enum Kind {
    UNRECOGNIZED,
    GAUGE,
    DELTA,
    CUMULATIVE,
  }

  enum ValueType {
    UNRECOGNIZED,
    BOOL,
    INT64,
    DOUBLE,
    STRING,
    DISTRIBUTION,
    MONEY
  }

  @AutoValue
  abstract class LabelDescriptor {
    public static LabelDescriptor create(String key, ValueType valueType, String description) {
      return new AutoValue_Metrics_LabelDescriptor(key, valueType, description);
    }
    public abstract String key();
    public abstract ValueType valueType();
    public abstract String description();
  }

  void createMetric(String type, String description, Kind kind, ValueType valueValueType,
      Set<LabelDescriptor> labelDescriptors) throws IOException;

  void recordDataPoint(String type, ValueType valueType, Object value,
      Map<String, String> resourceLabels) throws IOException;
}
