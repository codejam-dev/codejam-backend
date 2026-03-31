# Database Scripts

SQL scripts for initializing and managing CodeJam databases.

## Directory Structure

```
scripts/
└── db/
    ├── 00-init.sql             # Create schemas (auth, execution)
    ├── 01-auth-tables.sql      # Auth service tables
    └── 02-execution-tables.sql  # Execution service tables (run_history)
```

## Running Scripts

### On Droplet (Production)

```bash
# SSH into droplet, then run:

# 1. Create schemas
docker exec -i codejam-postgres psql -U codejam -d codejam_db < scripts/db/00-init.sql

# 2. Create auth tables
docker exec -i codejam-postgres psql -U codejam -d codejam_db < scripts/db/01-auth-tables.sql

# 3. Create execution tables
docker exec -i codejam-postgres psql -U codejam -d codejam_db < scripts/db/02-execution-tables.sql

# Or run all in order:
for f in scripts/db/*.sql; do
  echo "Running $f..."
  docker exec -i codejam-postgres psql -U codejam -d codejam_db < "$f"
done
```

### Local Development

```bash
# With local PostgreSQL
psql -U codejam -d codejam_db -f scripts/db/00-init.sql
psql -U codejam -d codejam_db -f scripts/db/01-auth-tables.sql
psql -U codejam -d codejam_db -f scripts/db/02-execution-tables.sql

# With Docker Compose
docker exec -i codejam-postgres psql -U codejam -d codejam_db < scripts/db/00-init.sql
```

## Notes

- Scripts are numbered for execution order (00, 01, 02...)
- `IF NOT EXISTS` is used to make scripts idempotent (safe to run multiple times)
- Hibernate with `ddl-auto: update` will also create tables, but these scripts are useful for:
  - Fresh database setup
  - Manual schema inspection
  - CI/CD pipelines
  - Documentation of expected schema
