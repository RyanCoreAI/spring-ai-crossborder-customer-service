# Backup and Restore Rehearsal

The repository provides a demo-grade rehearsal workflow. Production backups must use managed database snapshots, encryption, retention policy, and off-account copies.

## Demo Backup

```powershell
.\scripts\backup-demo.ps1
```

The script copies a MySQL logical dump and PostgreSQL custom-format dump from the running `compose.demo.yml` containers into a timestamped directory under `backups/`. It never writes database passwords into the dump manifest.

## Disposable Restore Check

```powershell
.\scripts\restore-rehearsal.ps1 -BackupDirectory .\backups\<timestamp>
```

The script creates temporary databases inside the demo containers, restores both dumps, checks required tables, and drops the temporary databases in `finally` blocks.

## Production Requirements

- Encrypt backups with a tenant-independent KMS key and restrict restore permission.
- Define RPO/RTO and retention per data class.
- Rehearse restore quarterly in an isolated account/network.
- Validate row counts, Flyway history, tenant isolation, and audit continuity after restore.
- Never restore production data into the public demo environment.
