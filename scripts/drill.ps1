param([ValidateSet('redpanda','minio','audit','primary-model','restore')] [string]$Scenario)
$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker is required; no drill evidence was generated.' }
Push-Location $root
try {
  switch ($Scenario) {
    'redpanda' {
      docker compose stop redpanda
      Invoke-RestMethod http://localhost:8080/actuator/health | Out-Null
      Write-Output 'Verify deterministic query remains available and consumer-lag alert fires; then start Redpanda and verify idempotent catch-up.'
      docker compose start redpanda
    }
    'minio' {
      docker compose stop minio
      Write-Output 'Attempt report approval and verify it is blocked while preview remains available.'
      docker compose start minio
    }
    'audit' { Write-Output 'Revoke audit INSERT in the isolated drill DB; verify every protected tool returns AUDIT_WRITE_FAILED, then restore privilege.' }
    'primary-model' { Write-Output 'Point the primary provider at the fault proxy; verify only timeout/429/5xx/circuit-open falls back and auth/schema/refusal does not.' }
    'restore' { Write-Output 'Restore PostgreSQL PITR and versioned MinIO objects into an isolated namespace; record measured RTO/RPO and hash verification.' }
  }
} finally { Pop-Location }
