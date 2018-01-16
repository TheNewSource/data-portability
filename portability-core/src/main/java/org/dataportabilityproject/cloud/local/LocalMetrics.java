package org.dataportabilityproject.cloud.local;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.dataportabilityproject.cloud.interfaces.Metrics;

/**
 * Local implementation of {@link Metrics}.
 */
public class LocalMetrics implements Metrics {

  @Override
  public void createMetric(String type, String description, Kind kind, ValueType valueType,
      Set<LabelDescriptor> labelDescriptors) throws IOException {
    // Do nothing
  }

  @Override
  public void recordDataPoint(String type, ValueType valueType, Object value,
      Map<String, String> resourceLabels) throws IOException {
    // Do nothing
  }
}
