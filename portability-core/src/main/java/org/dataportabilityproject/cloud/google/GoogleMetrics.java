package org.dataportabilityproject.cloud.google;

import static java.util.stream.Collectors.toSet;

import com.google.api.Distribution;
import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MetricDescriptor.MetricKind;
import com.google.api.MonitoredResource;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.inject.Inject;
import com.google.monitoring.v3.CreateMetricDescriptorRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.util.Timestamps;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dataportabilityproject.cloud.google.Annotations.ProjectId;
import org.dataportabilityproject.cloud.interfaces.Metrics;

/**
 * Google Cloud Platform implementation of {@link Metrics} which writes to Stackdriver.
 */
final class GoogleMetrics implements Metrics {
  private static final String BASE_METRIC_TYPE = "custom.googleapis.com/";
  private final MetricServiceClient metricServiceClient;
  private final ProjectName projectName;

  @Inject
  GoogleMetrics(@ProjectId String projectId) throws IOException {
    this.metricServiceClient =  MetricServiceClient.create();
    this.projectName = ProjectName.create(projectId);
  }

  @Override
  public void createMetric(String type, String description, Kind kind, ValueType valueType,
      Set<LabelDescriptor> labelDescriptors) throws IOException {
    MetricDescriptor descriptor = MetricDescriptor.newBuilder()
        .setType(getFullType(type))
        .setDescription(description)
        .setMetricKind(toGoogleMetricKind(kind))
        .setValueType(toGoogleMetricDescriptorValueType(valueType))
        .addAllLabels(labelDescriptors
            .stream().map(label -> toGoogleLabelDescriptor(label)).collect(toSet()))
        .build();

    CreateMetricDescriptorRequest request = CreateMetricDescriptorRequest.newBuilder()
        .setNameWithProjectName(projectName)
        .setMetricDescriptor(descriptor)
        .build();

    metricServiceClient.createMetricDescriptor(request);
  }

  @Override
  public void recordDataPoint(String type, ValueType valueType, Object value,
      Map<String, String> resourceLabels) throws IOException {
    TimeInterval interval = TimeInterval.newBuilder()
        .setEndTime(Timestamps.fromMillis(System.currentTimeMillis()))
        .build();
    TypedValue.Builder typedValue;
    switch(valueType) {
      case BOOL:
        typedValue = TypedValue.newBuilder().setBoolValue((boolean) value);
        break;
      case INT64:
        typedValue = TypedValue.newBuilder().setInt64Value((long) value);
        break;
      case MONEY:
      case DOUBLE:
        typedValue = TypedValue.newBuilder().setDoubleValue((double) value);
        break;
      case STRING:
        typedValue = TypedValue.newBuilder().setStringValue((String) value);
        break;
      case DISTRIBUTION:
        typedValue = TypedValue.newBuilder().setDistributionValue((Distribution) value);
        break;
      case UNRECOGNIZED:
      default:
        throw new IllegalArgumentException("Invalid ValueType to record data point in metrics. "
            + "Data point was: " + value + ". ValueType was: " + valueType);
    }
    Point point = Point.newBuilder()
        .setInterval(interval)
        .setValue(typedValue.build())
        .build();

    List<Point> pointList = new ArrayList<>();
    pointList.add(point);

    // Prepares the metric descriptor
    //Map<String, String> metricLabels = new HashMap<String, String>();
    Metric metric = Metric.newBuilder()
        .setType(getFullType(type))
        //.putAllLabels(metricLabels)
        .build();

    MonitoredResource resource = MonitoredResource.newBuilder()
        .setType(type)
        .putAllLabels(resourceLabels)
        .build();

    // Prepares the time series request
    TimeSeries timeSeries = TimeSeries.newBuilder()
        .setMetric(metric)
        .setResource(resource)
        .addAllPoints(pointList)
        .build();

    List<TimeSeries> timeSeriesList = new ArrayList<>();
    timeSeriesList.add(timeSeries);

    CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
        .setNameWithProjectName(projectName)
        .addAllTimeSeries(timeSeriesList)
        .build();

    metricServiceClient.createTimeSeries(request);
  }

  private static String getFullType(String type) throws IOException {
    try {
      return URLEncoder.encode(BASE_METRIC_TYPE + type, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IOException("Problem URL-encoding metric type " + type, e);
    }
  }

  private static MetricKind toGoogleMetricKind(Kind kind) {
    switch (kind) {
      case DELTA:
        return MetricKind.DELTA;
      case CUMULATIVE:
        return MetricKind.CUMULATIVE;
      case GAUGE:
        return MetricKind.GAUGE;
      case UNRECOGNIZED:
      default:
        return MetricKind.UNRECOGNIZED;
    }
  }

  private static MetricDescriptor.ValueType toGoogleMetricDescriptorValueType(ValueType valueType) {
    switch (valueType) {
      case BOOL:
        return MetricDescriptor.ValueType.BOOL;
      case INT64:
        return MetricDescriptor.ValueType.INT64;
      case DOUBLE:
        return MetricDescriptor.ValueType.DOUBLE;
      case STRING:
        return MetricDescriptor.ValueType.STRING;
      case DISTRIBUTION:
        return MetricDescriptor.ValueType.DISTRIBUTION;
      case MONEY:
        return MetricDescriptor.ValueType.MONEY;
      case UNRECOGNIZED:
      default:
        return MetricDescriptor.ValueType.UNRECOGNIZED;
    }
  }

  private static com.google.api.LabelDescriptor.ValueType toGoogleLabelDescriptorValueType(
      ValueType valueType) {
    switch (valueType) {
      case BOOL:
        return com.google.api.LabelDescriptor.ValueType.BOOL;
      case INT64:
        return com.google.api.LabelDescriptor.ValueType.INT64;
      case STRING:
        return com.google.api.LabelDescriptor.ValueType.STRING;
      case UNRECOGNIZED:
      default:
        return com.google.api.LabelDescriptor.ValueType.UNRECOGNIZED;
    }
  }

  private static com.google.api.LabelDescriptor toGoogleLabelDescriptor(
      LabelDescriptor labelDescriptor) {
    return com.google.api.LabelDescriptor.newBuilder()
        .setKey(labelDescriptor.key())
        .setValueType(toGoogleLabelDescriptorValueType(labelDescriptor.valueType()))
        .setDescription(labelDescriptor.description()).build();
  }
}
