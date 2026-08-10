import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-grpc';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-grpc';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';

const endpoint = process.env.OTEL_EXPORTER_OTLP_ENDPOINT;
if (endpoint && process.env.NODE_ENV !== 'test') {
  const sdk = new NodeSDK({
    serviceName: 'ai-gateway',
    traceExporter: new OTLPTraceExporter({ url: endpoint }),
    metricReader: new PeriodicExportingMetricReader({ exporter: new OTLPMetricExporter({ url: endpoint }), exportIntervalMillis: 15_000 }),
    instrumentations: [getNodeAutoInstrumentations({ '@opentelemetry/instrumentation-fs': { enabled: false } })],
  });
  sdk.start();
  process.once('SIGTERM', () => { void sdk.shutdown(); });
}
