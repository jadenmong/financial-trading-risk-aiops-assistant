import { readFile, readdir } from 'node:fs/promises';
import { join } from 'node:path';

const root = new URL('../', import.meta.url);
const schemas = await readdir(new URL('contracts/jsonschema/', root));
if (schemas.length < 2) throw new Error('expected versioned JSON Schemas');
for (const file of schemas) {
  const value = JSON.parse(await readFile(new URL(`contracts/jsonschema/${file}`, root), 'utf8'));
  if (!value.$schema || !value.$id) throw new Error(`${file} is missing $schema or $id`);
}
const asyncApi = await readFile(new URL('contracts/asyncapi/risk-events-v1.yaml', root), 'utf8');
const topics = ['market.snapshot.v1','position.snapshot.v1','order.event.v1','execution.event.v1','order.lifecycle.v1','execution.report.v1','risk.limit.v1','risk.breach.v1','system.health.v1','incident.signal.v1','agent.run.v1','report.lifecycle.v1','ops.audit.v1'];
for (const topic of topics) {
  if (!asyncApi.includes(topic)) throw new Error(`missing topic ${topic}`);
}
const openApi = await readFile(new URL('contracts/openapi/risk-aiops-v1.yaml', root), 'utf8');
for (const endpoint of ['/api/v1/diagnoses','/api/v1/reports','/api/v1/audit-events','/api/v1/risk-snapshots','/api/v1/reconciliation-breaks','/api/v1/evidence/{id}','/api/v1/incidents','/internal/v1/events']) {
  if (!openApi.includes(endpoint)) throw new Error(`missing endpoint ${endpoint}`);
}
process.stdout.write(`verified ${schemas.length} schemas, ${topics.length} topics and production REST contract\n`);
